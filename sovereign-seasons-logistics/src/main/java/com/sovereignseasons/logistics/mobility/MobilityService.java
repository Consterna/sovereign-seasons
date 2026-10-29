/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.sovereignseasons.logistics.mobility;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.sovereignseasons.logistics.weight.InventoryWeigher;
import com.sovereignseasons.logistics.weight.WeightModel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies seasonal mobility (snow, mud, roads, blizzards) and carried weight to players and their mounts.
 * {@link #tick(Player)} must run on the player's own thread (main thread on Paper, entity scheduler on Folia).
 */
public class MobilityService implements Listener {
    // Resolved by registry key: the Java field names changed across versions, the vanilla keys did not
    private static final PotionEffectType SLOWNESS = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
    private static final PotionEffectType SPEED = Registry.EFFECT.get(NamespacedKey.minecraft("speed"));

    /**
     * Last computed situation of a player, for /logistics status.
     */
    public record Snapshot(SurfaceType surface, boolean mounted, boolean blizzard, double weight, WeightModel.Load load,
                           MovementModel.Effect effect) {}

    private final LogisticsConfig config;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;
    private final SurfaceClassifier classifier;
    private final MovementModel movement;
    private final WeightModel weightModel;
    private final InventoryWeigher weigher;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, WeightModel.Load> lastLoad = new ConcurrentHashMap<>();
    // Inventory weighing (with shulker/bundle contents) is the expensive part: it is refreshed every other tick
    private final Map<UUID, Double> cachedWeight = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> weighCountdown = new ConcurrentHashMap<>();
    private static final int WEIGH_EVERY_N_TICKS = 2;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public MobilityService(LogisticsConfig config, SeasonService seasonService, TemperatureService temperatureService,
                           WeightModel weightModel, InventoryWeigher weigher) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.classifier = new SurfaceClassifier(config);
        this.movement = new MovementModel(config);
        this.weightModel = Objects.requireNonNull(weightModel, "weightModel cannot be null");
        this.weigher = Objects.requireNonNull(weigher, "weigher cannot be null");
    }

    public void tick(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            snapshots.remove(player.getUniqueId());
            return;
        }
        World world = player.getWorld();
        boolean seasonsHere = seasonService.isEnabledInWorld(world);
        Season season = seasonsHere ? seasonService.getSeason(world) : null;

        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle instanceof LivingEntity && config.getMounts().contains(vehicle.getType());
        LivingEntity mover = mounted ? (LivingEntity) vehicle : player;

        double weight = config.isWeightEnabled() ? currentWeight(player) : 0.0;
        WeightModel.Load load = config.isWeightEnabled() ? weightModel.load(weight) : WeightModel.Load.LIGHT;
        warnOnLoadChange(player, load);

        SurfaceType surface = SurfaceType.NORMAL;
        boolean blizzard = false;
        // No on-ground requirement: mounts standing still never report it, and in the air the block below is air anyway
        if (seasonsHere) {
            Location feet = mover.getLocation();
            // An entity can be inside a snow layer (not stepped on top of it): that counts as walking through snow
            Block feetBlock = feet.getBlock();
            Block ground = isSnow(feetBlock.getType()) ? feetBlock : feet.clone().subtract(0, 0.01, 0).getBlock();
            Block under = ground.getRelative(0, -1, 0);
            int layers = ground.getBlockData() instanceof Snow snow ? snow.getLayers() : 0;
            TemperatureBand band = temperatureService.getBand(feet);
            boolean open = isSkyExposed(feet);
            // Vanilla biomes without precipitation (deserts, savannas, badlands) have zero humidity: no rain, no snow
            boolean precipitation = world.hasStorm() && open && ground.getHumidity() > 0.0;
            boolean raining = precipitation && band != TemperatureBand.FREEZING;
            blizzard = precipitation && season == Season.WINTER && band == TemperatureBand.FREEZING;
            surface = classifier.classify(ground.getType(), layers, under.getType(), raining, season);
        }

        boolean snowProtected = mounted ? hasLeatherHorseArmor(mover) : wearsLeatherBoots(player);
        int weightSlowness = mounted ? 0 : weightModel.slownessLevel(weight);
        MovementModel.Effect effect = movement.evaluate(surface, mounted, snowProtected, blizzard, weightSlowness);
        apply(mover, effect);
        snapshots.put(player.getUniqueId(), new Snapshot(surface, mounted, blizzard, weight, load, effect));
    }

    private double currentWeight(Player player) {
        UUID id = player.getUniqueId();
        int countdown = weighCountdown.getOrDefault(id, 0);
        Double cached = cachedWeight.get(id);
        if (cached == null || countdown <= 0) {
            cached = weigher.weigh(player.getInventory().getContents());
            cachedWeight.put(id, cached);
            countdown = WEIGH_EVERY_N_TICKS;
        }
        weighCountdown.put(id, countdown - 1);
        return cached;
    }

    private void apply(LivingEntity entity, MovementModel.Effect effect) {
        int duration = config.getEffectDurationTicks();
        if (effect.slowness() > 0 && SLOWNESS != null) {
            entity.addPotionEffect(new PotionEffect(SLOWNESS, duration, effect.slowness() - 1, true, false, true));
        }
        if (effect.speed() > 0 && SPEED != null) {
            entity.addPotionEffect(new PotionEffect(SPEED, duration, effect.speed() - 1, true, false, true));
        }
    }

    private void warnOnLoadChange(Player player, WeightModel.Load load) {
        WeightModel.Load previous = lastLoad.put(player.getUniqueId(), load);
        if (previous == null || previous == load || load.ordinal() < previous.ordinal()) {
            return;
        }
        String message = load == WeightModel.Load.OVERLOADED
                ? "<red>Vas sobrecargado: deja carga en una mula, un cofre o empaquétala en cajas.</red>"
                : "<yellow>Tu carga empieza a pesar.</yellow>";
        player.sendActionBar(miniMessage.deserialize(message));
    }

    public Snapshot getSnapshot(Player player) {
        return snapshots.get(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(event.getPlayer().getUniqueId());
        lastLoad.remove(event.getPlayer().getUniqueId());
        cachedWeight.remove(event.getPlayer().getUniqueId());
        weighCountdown.remove(event.getPlayer().getUniqueId());
    }

    private static boolean isSnow(Material material) {
        return material == Material.SNOW || material == Material.POWDER_SNOW;
    }

    private static boolean isSkyExposed(Location location) {
        World world = location.getWorld();
        return world != null && world.getHighestBlockYAt(location) <= location.getBlockY();
    }

    private boolean wearsLeatherBoots(Player player) {
        if (!config.isLeatherBootsProtect()) {
            return false;
        }
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.getType() == Material.LEATHER_BOOTS;
    }

    private boolean hasLeatherHorseArmor(LivingEntity mount) {
        if (!config.isLeatherHorseArmorProtect() || !(mount instanceof Horse horse)) {
            return false;
        }
        try {
            ItemStack armor = horse.getInventory().getArmor();
            return armor != null && armor.getType() == Material.LEATHER_HORSE_ARMOR;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /**
     * Whether an entity type can be ridden as a configured mount (exposed for commands and tests).
     */
    public boolean isMount(Entity entity) {
        return entity instanceof AbstractHorse && config.getMounts().contains(entity.getType());
    }
}
