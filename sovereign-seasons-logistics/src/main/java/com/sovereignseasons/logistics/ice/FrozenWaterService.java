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

package com.sovereignseasons.logistics.ice;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Freezes still surface water around players in freezing winters and melts it back afterwards.
 * Only ice recorded in each chunk's PersistentDataContainer is ever melted, so natural and player-placed ice is safe.
 */
public class FrozenWaterService implements Listener {
    private final LogisticsConfig config;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;
    private final GreenhouseService greenhouseService;
    private final FoliaLib foliaLib;
    private final NamespacedKey registryKey;
    private final AtomicLong frozen = new AtomicLong();
    private final AtomicLong thawed = new AtomicLong();

    public FrozenWaterService(Plugin plugin, LogisticsConfig config, SeasonService seasonService, TemperatureService temperatureService,
                              GreenhouseService greenhouseService, FoliaLib foliaLib) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.greenhouseService = greenhouseService;
        this.foliaLib = foliaLib;
        this.registryKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin cannot be null"), "seasonal_ice");
    }

    /**
     * Samples random columns around a player and schedules freezing/thawing on each column's region thread.
     */
    public void sampleAround(Player player) {
        if (!config.isIceEnabled() || player == null || !player.isOnline()) {
            return;
        }
        World world = player.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }
        Location center = player.getLocation();
        int radius = config.getIceRadius();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < config.getIceSamplesPerPlayer(); i++) {
            int x = center.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + random.nextInt(-radius, radius + 1);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Location column = new Location(world, x, center.getY(), z);
            runAt(column, () -> processColumn(world, x, z));
        }
    }

    private void processColumn(World world, int x, int z) {
        // MOTION_BLOCKING includes fluids, so the top of a lake is the water surface
        Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
        Location location = top.getLocation();
        Season season = seasonService.getSeason(world);
        var band = temperatureService.getBand(location);
        Chunk chunk = top.getChunk();

        if (FreezeRules.shouldThaw(season, band)) {
            thawChunk(chunk);
            return;
        }
        boolean waterSource = top.getType() == Material.WATER && top.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
        if (!waterSource) {
            return;
        }
        boolean greenhouse = greenhouseService != null && greenhouseService.isGreenhouse(location);
        boolean occupied = !world.getNearbyEntities(BoundingBox.of(top)).isEmpty();
        if (FreezeRules.shouldFreeze(season, band, true, true, greenhouse, occupied)) {
            top.setType(Material.ICE, false);
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            long[] registry = pdc.get(registryKey, PersistentDataType.LONG_ARRAY);
            pdc.set(registryKey, PersistentDataType.LONG_ARRAY, IceRegistry.add(registry, IceRegistry.pack(x & 15, top.getY(), z & 15)));
            frozen.incrementAndGet();
        }
    }

    /**
     * Melts up to the configured number of registered ice blocks in a chunk.
     */
    public void thawChunk(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] registry = pdc.get(registryKey, PersistentDataType.LONG_ARRAY);
        if (registry == null || registry.length == 0) {
            return;
        }
        int limit = Math.min(registry.length, config.getIceMaxThawPerChunk());
        for (int i = 0; i < limit; i++) {
            long packed = registry[i];
            Block block = chunk.getBlock(IceRegistry.localX(packed), IceRegistry.y(packed), IceRegistry.localZ(packed));
            if (block.getType() == Material.ICE) {
                block.setType(Material.WATER, true);
                thawed.incrementAndGet();
            }
        }
        long[] rest = IceRegistry.dropFirst(registry, limit);
        if (rest.length == 0) {
            pdc.remove(registryKey);
        } else {
            pdc.set(registryKey, PersistentDataType.LONG_ARRAY, rest);
        }
    }

    // ---------------------------------------------------------------- registry maintenance

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!config.isIceEnabled()) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (!chunk.getPersistentDataContainer().has(registryKey, PersistentDataType.LONG_ARRAY)) {
            return;
        }
        World world = event.getWorld();
        Location center = new Location(world, (chunk.getX() << 4) + 8, world.getSeaLevel(), (chunk.getZ() << 4) + 8);
        runAt(center, () -> {
            if (chunk.isLoaded() && FreezeRules.shouldThaw(seasonService.getSeason(world), temperatureService.getBand(center))) {
                thawChunk(chunk);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIceBreak(BlockBreakEvent event) {
        forget(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIcePlace(BlockPlaceEvent event) {
        forget(event.getBlockPlaced()); // a player-placed block must never be melted by the plugin
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIceMelt(BlockFadeEvent event) {
        forget(event.getBlock());
    }

    private void forget(Block block) {
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        long[] registry = pdc.get(registryKey, PersistentDataType.LONG_ARRAY);
        long packed = IceRegistry.pack(block.getX() & 15, block.getY(), block.getZ() & 15);
        if (IceRegistry.contains(registry, packed)) {
            long[] rest = IceRegistry.remove(registry, packed);
            if (rest.length == 0) {
                pdc.remove(registryKey);
            } else {
                pdc.set(registryKey, PersistentDataType.LONG_ARRAY, rest);
            }
        }
    }

    private void runAt(Location location, Runnable action) {
        if (foliaLib != null) {
            foliaLib.getImpl().runAtLocation(location, task -> action.run());
        } else {
            action.run();
        }
    }

    public long getFrozenCount() {
        return frozen.get();
    }

    public long getThawedCount() {
        return thawed.get();
    }
}
