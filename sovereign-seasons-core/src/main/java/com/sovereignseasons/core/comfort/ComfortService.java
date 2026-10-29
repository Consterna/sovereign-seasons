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

package com.sovereignseasons.core.comfort;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.core.config.SeasonsConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies {@link ComfortModel} to online players. {@link #tick(Player)} must run on the player's own thread
 * (main thread on Paper, entity scheduler on Folia).
 */
public class ComfortService implements Listener {
    // Resolved by key: the Java field was renamed (SLOW -> SLOWNESS) in 1.20.5, the vanilla key never changed
    private static final PotionEffectType SLOWNESS = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));

    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;
    private final ComfortModel model;
    // Cooldown per player and exposure type, so a recent cold warning never hides a heat warning
    private final Map<String, Long> lastMessage = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ComfortService(SeasonsConfig config, SeasonService seasonService, TemperatureService temperatureService) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.model = new ComfortModel(config);
    }

    public void tick(Player player) {
        if (!config.isComfortEnabled() || player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        World world = player.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }

        Location eye = player.getEyeLocation();
        Block head = eye.getBlock();
        ComfortModel.Situation situation = new ComfortModel.Situation(
                temperatureService.getBand(player.getLocation()),
                head.getLightFromSky(),
                head.getLightFromBlocks(),
                wearsLeather(player),
                isDaytime(world),
                world.hasStorm(),
                player.isInWater(),
                player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)
        );

        int interval = config.getComfortIntervalTicks();
        switch (model.evaluate(situation)) {
            case COLD -> {
                int target = model.targetFreezeTicks(player.getMaxFreezeTicks());
                if (player.getFreezeTicks() < target) {
                    player.setFreezeTicks(target);
                }
                if (config.isColdSlowness() && SLOWNESS != null) {
                    player.addPotionEffect(new PotionEffect(SLOWNESS, interval + 20, 0, true, false, true));
                }
                notify(player, ComfortModel.Exposure.COLD, "<aqua>❄ Hace un frío helado. Ponte alguna prenda de cuero o acércate al fuego.</aqua>");
            }
            case HEAT -> {
                player.setExhaustion(player.getExhaustion() + model.heatExhaustion(interval));
                notify(player, ComfortModel.Exposure.HEAT, "<gold>☀ El calor es sofocante. Busca sombra o agua para no agotarte.</gold>");
            }
            default -> {
            }
        }
    }

    private void notify(Player player, ComfortModel.Exposure exposure, String miniMessageText) {
        long cooldownMillis = config.getComfortMessageCooldownSeconds() * 1000L;
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + exposure.name();
        Long last = lastMessage.get(key);
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        lastMessage.put(key, now);
        player.sendActionBar(miniMessage.deserialize(miniMessageText));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String prefix = event.getPlayer().getUniqueId() + ":";
        lastMessage.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static boolean wearsLeather(Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType().name().startsWith("LEATHER_")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDaytime(World world) {
        long time = world.getTime();
        return time < 12_300L || time > 23_850L;
    }
}
