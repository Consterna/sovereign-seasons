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

package com.sovereignseasons.pantry.cellar;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sovereignseasons.pantry.config.PantryConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Detects cold underground cellars and nearby ice boxes, with a spatial LRU cache.
 * <p>
 * A position is a cellar when no sky light reaches any of its six neighbours and it is either covered by
 * enough solid blocks or located below the configured deep Y level.
 */
public class CellarDetector {
    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final PantryConfig config;
    private volatile Cache<PantryBlockPos, CellarReport> cache;

    public CellarDetector(PantryConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        rebuildCache();
    }

    public synchronized void rebuildCache() {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(config.getCellarCacheMaxSize())
                .expireAfterWrite(config.getCellarCacheTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    public CellarReport inspect(Location location) {
        if (location == null || location.getWorld() == null) {
            return CellarReport.failure("Ubicación o mundo inválido");
        }
        PantryBlockPos key = PantryBlockPos.from(location);
        CellarReport cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached.asCached();
        }
        CellarReport evaluated = evaluate(location.getWorld(), key.x(), key.y(), key.z());
        cache.put(key, evaluated);
        return evaluated;
    }

    private CellarReport evaluate(World world, int x, int y, int z) {
        int minHeight = safeMinHeight(world);
        int maxHeight = safeMaxHeight(world);
        if (y < minHeight || y >= maxHeight) {
            return CellarReport.failure("Coordenadas fuera de los límites verticales del mundo");
        }
        if (!isChunkLoaded(world, x >> 4, z >> 4)) {
            return CellarReport.failure("El chunk no está cargado");
        }

        int maxSkyLight = 0;
        for (int[] face : FACES) {
            int ny = y + face[1];
            if (ny < minHeight || ny >= maxHeight) {
                continue;
            }
            Block neighbour = blockAt(world, x + face[0], ny, z + face[2]);
            if (neighbour != null) {
                maxSkyLight = Math.max(maxSkyLight, skyLight(neighbour));
            }
        }

        int roofBlocks = 0;
        int top = Math.min(maxHeight - 1, y + config.getRoofScanHeight());
        for (int ry = y + 1; ry <= top; ry++) {
            Block above = blockAt(world, x, ry, z);
            if (above != null && isSolid(above.getType())) {
                roofBlocks++;
            }
        }

        boolean deep = y <= config.getDeepY();
        Material ice = findIce(world, x, y, z, minHeight, maxHeight);
        boolean cellar = config.isCellarEnabled() && maxSkyLight == 0 && (deep || roofBlocks >= config.getMinRoofBlocks());

        String reason = null;
        if (!config.isCellarEnabled()) {
            reason = "Las bodegas están desactivadas";
        } else if (maxSkyLight > 0) {
            reason = "Entra luz del cielo (nivel " + maxSkyLight + ")";
        } else if (!cellar) {
            reason = "Techo insuficiente (" + roofBlocks + "/" + config.getMinRoofBlocks() + " bloques) y por encima de Y=" + config.getDeepY();
        }
        return new CellarReport(cellar, false, maxSkyLight, roofBlocks, deep, ice != null, ice, reason);
    }

    private Material findIce(World world, int x, int y, int z, int minHeight, int maxHeight) {
        int radius = config.getIceRadius();
        if (radius <= 0 || config.getIceMaterials().isEmpty()) {
            return null;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!isChunkLoaded(world, (x + dx) >> 4, (z + dz) >> 4)) {
                    continue;
                }
                for (int dy = -radius; dy <= radius; dy++) {
                    int ny = y + dy;
                    if (ny < minHeight || ny >= maxHeight) {
                        continue;
                    }
                    Block block = blockAt(world, x + dx, ny, z + dz);
                    if (block != null && block.getType() != null && config.getIceMaterials().contains(block.getType())) {
                        return block.getType();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Invalidates every cached position whose evaluation could depend on the block at {@code location}:
     * the column below it (roof) and the surrounding cube (sky light and ice).
     */
    public void invalidateAround(Location location) {
        if (location == null || location.getWorld() == null || cache.size() == 0) {
            return;
        }
        PantryBlockPos origin = PantryBlockPos.from(location);
        int horizontal = Math.max(1, config.getIceRadius());
        int below = config.getRoofScanHeight();
        int above = Math.max(1, config.getIceRadius());
        for (int dx = -horizontal; dx <= horizontal; dx++) {
            for (int dz = -horizontal; dz <= horizontal; dz++) {
                for (int dy = -below; dy <= above; dy++) {
                    cache.invalidate(origin.offset(dx, dy, dz));
                }
            }
        }
    }

    public void clearCache() {
        cache.invalidateAll();
    }

    public long getCacheSize() {
        return cache.size();
    }

    public long getCacheHits() {
        return cache.stats().hitCount();
    }

    public long getCacheMisses() {
        return cache.stats().missCount();
    }

    private static Block blockAt(World world, int x, int y, int z) {
        try {
            return world.getBlockAt(x, y, z);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int skyLight(Block block) {
        try {
            return block.getLightFromSky();
        } catch (RuntimeException e) {
            return 15;
        }
    }

    private static boolean isSolid(Material material) {
        return material != null && material.isSolid();
    }

    private static boolean isChunkLoaded(World world, int chunkX, int chunkZ) {
        try {
            return world.isChunkLoaded(chunkX, chunkZ);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int safeMinHeight(World world) {
        try {
            return world.getMinHeight();
        } catch (RuntimeException e) {
            return -64;
        }
    }

    private static int safeMaxHeight(World world) {
        try {
            return world.getMaxHeight();
        } catch (RuntimeException e) {
            return 320;
        }
    }
}
