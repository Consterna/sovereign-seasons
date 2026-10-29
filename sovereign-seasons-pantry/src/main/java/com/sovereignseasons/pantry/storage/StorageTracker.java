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

package com.sovereignseasons.pantry.storage;

import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.pantry.cellar.CellarDetector;
import com.sovereignseasons.pantry.cellar.CellarReport;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.FoodDecayService;
import com.sovereignseasons.pantry.food.StorageContext;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

/**
 * Bukkit glue: resolves storage contexts, keeps per-container and per-player "last evaluation" ticks,
 * and runs decay evaluations on real inventories.
 * <p>
 * Container ticks are stored in the chunk's PersistentDataContainer (no BlockState snapshots, so inventory
 * contents are never overwritten); player ticks are stored on the player.
 */
public class StorageTracker {
    private final Plugin plugin;
    private final PantryConfig config;
    private final FoodDecayService decayService;
    private final CellarDetector cellarDetector;
    private final TemperatureService temperatureService;
    private final NamespacedKey playerKey;

    public StorageTracker(Plugin plugin, PantryConfig config, FoodDecayService decayService, CellarDetector cellarDetector, TemperatureService temperatureService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.decayService = Objects.requireNonNull(decayService, "decayService cannot be null");
        this.cellarDetector = Objects.requireNonNull(cellarDetector, "cellarDetector cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.playerKey = new NamespacedKey(plugin, "last_eval");
    }

    /**
     * Global decay clock: full time of the primary world, shared by all dimensions so that items
     * carried between worlds keep consistent expiries.
     */
    public long now() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return 0L;
        }
        return worlds.get(0).getFullTime();
    }

    public StorageContext resolveContext(Location location) {
        TemperatureBand band = temperatureService.getBand(location);
        CellarReport report = cellarDetector.inspect(location);
        // Cellars cannot compensate extreme heat (deserts in summer, the Nether)
        boolean cellar = report.cellar() && band != TemperatureBand.HOT;
        return new StorageContext(band, cellar, report.iceNearby());
    }

    public FoodDecayService.EvaluationResult evaluateContainer(Inventory inventory, Location location, boolean force) {
        if (inventory == null || location == null || location.getWorld() == null) {
            return FoodDecayService.EvaluationResult.EMPTY;
        }
        long now = now();
        NamespacedKey key = containerKey(location);
        PersistentDataContainer chunkData = location.getChunk().getPersistentDataContainer();
        long last = chunkData.getOrDefault(key, PersistentDataType.LONG, 0L);
        if (!force && !decayService.isDue(last, now)) {
            return FoodDecayService.EvaluationResult.EMPTY;
        }
        long elapsed = last > 0L && now > last ? now - last : 0L;
        ItemStack[] contents = inventory.getContents();
        StorageContext context = resolveContext(location)
                .withContainerMultiplier(config.getContainerMultiplier(inventory.getType().name()));
        FoodDecayService.EvaluationResult result = decayService.evaluate(contents, context, elapsed, now);
        if (result.changedAnything()) {
            inventory.setContents(contents);
        }
        chunkData.set(key, PersistentDataType.LONG, now);
        return result;
    }

    public FoodDecayService.EvaluationResult evaluatePlayer(Player player, boolean force) {
        if (player == null || !player.isOnline()) {
            return FoodDecayService.EvaluationResult.EMPTY;
        }
        long now = now();
        PersistentDataContainer data = player.getPersistentDataContainer();
        long last = data.getOrDefault(playerKey, PersistentDataType.LONG, 0L);
        if (!force && !decayService.isDue(last, now)) {
            return FoodDecayService.EvaluationResult.EMPTY;
        }
        long elapsed = last > 0L && now > last ? now - last : 0L;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        FoodDecayService.EvaluationResult result = decayService.evaluate(contents, resolveContext(player.getLocation()), elapsed, now);
        if (result.changedAnything()) {
            inventory.setContents(contents);
        }
        data.set(playerKey, PersistentDataType.LONG, now);
        return result;
    }

    /**
     * Drops the stored evaluation tick of a container that was broken.
     */
    public void forgetContainer(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Chunk chunk = location.getChunk();
        chunk.getPersistentDataContainer().remove(containerKey(location));
    }

    private NamespacedKey containerKey(Location location) {
        // NamespacedKey keys allow [a-z0-9/._-], so negative coordinates are valid
        return new NamespacedKey(plugin, "c_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ());
    }
}
