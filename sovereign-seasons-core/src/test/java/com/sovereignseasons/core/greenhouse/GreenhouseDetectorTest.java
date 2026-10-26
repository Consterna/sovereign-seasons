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

package com.sovereignseasons.core.greenhouse;

import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GreenhouseDetectorTest {
    private SeasonsConfig config;
    private UUID worldUid;
    private Map<String, Material> worldBlocks;
    private Map<String, Boolean> campfireStates;
    private World mockWorld;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setHeatRadius(4);
        config.setMaxCeilingHeight(16);
        config.setCacheTtlSeconds(30);

        worldUid = UUID.randomUUID();
        worldBlocks = new HashMap<>();
        campfireStates = new HashMap<>();

        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldUid;
                    case "getName" -> "mock_world";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        yield createMockBlock(mockWorld, x, y, z);
                    }
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private void setBlock(int x, int y, int z, Material mat) {
        worldBlocks.put(posKey(x, y, z), mat);
    }

    private void setCampfire(int x, int y, int z, boolean isLit) {
        setBlock(x, y, z, Material.CAMPFIRE);
        campfireStates.put(posKey(x, y, z), isLit);
    }

    private Block createMockBlock(World world, int x, int y, int z) {
        Material mat = worldBlocks.getOrDefault(posKey(x, y, z), Material.AIR);
        Location loc = new Location(world, x, y, z);

        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getType" -> mat;
                    case "getLocation" -> loc;
                    case "getBlockData" -> createMockBlockData(mat, posKey(x, y, z));
                    default -> null;
                }
        );
    }

    private BlockData createMockBlockData(Material mat, String key) {
        if (mat == Material.CAMPFIRE) {
            boolean lit = campfireStates.getOrDefault(key, true);
            return (Campfire) Proxy.newProxyInstance(
                    Campfire.class.getClassLoader(),
                    new Class<?>[]{Campfire.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isLit" -> lit;
                        case "getMaterial" -> Material.CAMPFIRE;
                        default -> null;
                    }
            );
        }

        return (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> mat;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Identifies valid greenhouse with glass roof and lit heat source")
    void shouldIdentifyValidGreenhouse() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 10, 64, 10);

        // Put glass at Y=68 (4 blocks above crop)
        setBlock(10, 68, 10, Material.GLASS);

        // Put lit campfire at (12, 64, 11) (within radius 4)
        setCampfire(12, 64, 11, true);

        assertThat(detector.isGreenhouse(cropLoc)).isTrue();

        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.isGreenhouse()).isTrue();
        assertThat(report.glassCeilingValid()).isTrue();
        assertThat(report.heatSourceValid()).isTrue();
        assertThat(report.distanceToCeiling()).isEqualTo(4);
        assertThat(report.glassMaterial()).isEqualTo(Material.GLASS);
    }

    @Test
    @DisplayName("Fails greenhouse detection when glass ceiling is missing")
    void shouldFailWhenGlassCeilingMissing() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 10, 64, 10);

        // Only heat source, no glass
        setCampfire(11, 64, 10, true);

        assertThat(detector.isGreenhouse(cropLoc)).isFalse();

        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.glassCeilingValid()).isFalse();
        assertThat(report.heatSourceValid()).isTrue();
        assertThat(report.failureReason()).contains("No se encontró techo de cristal");
    }

    @Test
    @DisplayName("Fails greenhouse detection when solid block obstructs glass roof")
    void shouldFailWhenSolidBlockObstructs() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 10, 64, 10);

        // Solid cobblestone at Y=66, glass at Y=68
        setBlock(10, 66, 10, Material.COBBLESTONE);
        setBlock(10, 68, 10, Material.GLASS);
        setCampfire(11, 64, 10, true);

        assertThat(detector.isGreenhouse(cropLoc)).isFalse();

        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.glassCeilingValid()).isFalse();
        assertThat(report.failureReason()).contains("Obstrucción sólida");
    }

    @Test
    @DisplayName("Fails greenhouse detection when campfire is unlit")
    void shouldFailWhenCampfireIsUnlit() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 10, 64, 10);

        setBlock(10, 68, 10, Material.TINTED_GLASS);
        setCampfire(11, 64, 10, false); // Unlit!

        assertThat(detector.isGreenhouse(cropLoc)).isFalse();
        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.heatSourceValid()).isFalse();
    }

    @Test
    @DisplayName("Spatial LRU cache returns hits without redundant world scans and invalidates on demand")
    void shouldUtilizeSpatialCacheAndInvalidate() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 20, 64, 20);

        setBlock(20, 68, 20, Material.GLASS);
        setCampfire(21, 64, 20, true);

        // First call: cache miss
        assertThat(detector.isGreenhouse(cropLoc)).isTrue();
        assertThat(detector.getCacheMisses()).isEqualTo(1);
        assertThat(detector.getCacheHits()).isEqualTo(0);

        // Second call: cache hit!
        assertThat(detector.isGreenhouse(cropLoc)).isTrue();
        assertThat(detector.getCacheMisses()).isEqualTo(1);
        assertThat(detector.getCacheHits()).isEqualTo(1);

        // Invalidate position
        detector.invalidate(cropLoc);

        // Third call after invalidation: cache miss
        assertThat(detector.isGreenhouse(cropLoc)).isTrue();
        assertThat(detector.getCacheMisses()).isEqualTo(2);
        assertThat(detector.getCacheHits()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fails greenhouse detection when solid non-occluding block (slab, stairs, wall) obstructs glass roof")
    void shouldFailWhenSolidNonOccludingBlockObstructs() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location cropLoc = new Location(mockWorld, 10, 64, 10);

        // Stone slab at Y=66 (solid but not occluding), glass at Y=68, lit campfire at radius 4
        setBlock(10, 66, 10, Material.STONE_SLAB);
        setBlock(10, 68, 10, Material.GLASS);
        setCampfire(11, 64, 10, true);

        assertThat(detector.isGreenhouse(cropLoc)).isFalse();

        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.glassCeilingValid()).isFalse();
        assertThat(report.failureReason()).contains("Obstrucción sólida (STONE_SLAB)");
    }
}
