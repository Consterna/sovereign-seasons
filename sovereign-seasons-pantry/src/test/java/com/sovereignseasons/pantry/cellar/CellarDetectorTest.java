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

import com.sovereignseasons.pantry.config.PantryConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CellarDetectorTest {
    private PantryConfig config;
    private CellarDetector detector;

    private final Map<String, Material> blocks = new HashMap<>();
    private final Map<String, Integer> skyLight = new HashMap<>();
    private final AtomicBoolean chunksLoaded = new AtomicBoolean(true);
    private final AtomicInteger blockLookups = new AtomicInteger();
    private World world;

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @BeforeEach
    void setup() {
        config = new PantryConfig(null);
        detector = new CellarDetector(config);
        UUID uid = UUID.randomUUID();
        world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getName" -> "world";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "isChunkLoaded" -> chunksLoaded.get();
                    case "getBlockAt" -> {
                        if (args.length == 3) {
                            blockLookups.incrementAndGet();
                            yield block((int) args[0], (int) args[1], (int) args[2]);
                        }
                        yield null;
                    }
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private Block block(int x, int y, int z) {
        String k = key(x, y, z);
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> blocks.getOrDefault(k, Material.AIR);
                    case "getLightFromSky" -> (byte) (int) skyLight.getOrDefault(k, 0);
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    default -> null;
                }
        );
    }

    private void roof(int x, int z, int fromY, int count, Material material) {
        for (int i = 0; i < count; i++) {
            blocks.put(key(x, fromY + i, z), material);
        }
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    @Test
    @DisplayName("Dark room under three solid blocks is a cellar")
    void shouldDetectCoveredCellar() {
        roof(0, 0, 65, 3, Material.STONE);
        CellarReport report = detector.inspect(at(0, 64, 0));
        assertThat(report.cellar()).isTrue();
        assertThat(report.roofBlocks()).isEqualTo(3);
        assertThat(report.maxSkyLight()).isZero();
        assertThat(report.failureReason()).isNull();
    }

    @Test
    @DisplayName("Sky light reaching any neighbour disqualifies the cellar")
    void skyLightShouldDisqualify() {
        roof(0, 0, 65, 5, Material.STONE);
        skyLight.put(key(1, 64, 0), 7);
        CellarReport report = detector.inspect(at(0, 64, 0));
        assertThat(report.cellar()).isFalse();
        assertThat(report.maxSkyLight()).isEqualTo(7);
        assertThat(report.failureReason()).contains("luz del cielo");
    }

    @Test
    @DisplayName("Thin roofs only qualify below the deep Y level")
    void thinRoofShouldRequireDepth() {
        roof(0, 0, 65, 1, Material.OAK_PLANKS);
        assertThat(detector.inspect(at(0, 64, 0)).cellar()).isFalse();

        roof(0, 0, 31, 1, Material.OAK_PLANKS);
        CellarReport deep = detector.inspect(at(0, 30, 0));
        assertThat(deep.cellar()).isTrue();
        assertThat(deep.deep()).isTrue();
    }

    @Test
    @DisplayName("Non-solid blocks such as glass panes or torches do not count as roof")
    void nonSolidBlocksShouldNotCount() {
        roof(0, 0, 65, 3, Material.TORCH);
        assertThat(detector.inspect(at(0, 64, 0)).roofBlocks()).isZero();
    }

    @Test
    @DisplayName("Ice within the radius is reported as an ice box independently of the cellar")
    void shouldDetectIceBox() {
        blocks.put(key(2, 63, -2), Material.PACKED_ICE);
        CellarReport report = detector.inspect(at(0, 64, 0));
        assertThat(report.iceNearby()).isTrue();
        assertThat(report.iceMaterial()).isEqualTo(Material.PACKED_ICE);

        blocks.clear();
        blocks.put(key(3, 64, 0), Material.BLUE_ICE); // outside radius 2
        assertThat(detector.inspect(at(10, 64, 10)).iceNearby()).isFalse();
    }

    @Test
    @DisplayName("Results are cached and invalidated when nearby blocks change")
    void shouldCacheAndInvalidate() {
        roof(0, 0, 65, 3, Material.STONE);
        assertThat(detector.inspect(at(0, 64, 0)).fromCache()).isFalse();
        int lookups = blockLookups.get();
        CellarReport cached = detector.inspect(at(0, 64, 0));
        assertThat(cached.fromCache()).isTrue();
        assertThat(cached.cellar()).isTrue();
        assertThat(blockLookups.get()).isEqualTo(lookups);
        assertThat(detector.getCacheHits()).isEqualTo(1);

        // Breaking the roof 10 blocks above invalidates the position below it
        blocks.remove(key(0, 65, 0));
        blocks.remove(key(0, 66, 0));
        detector.invalidateAround(at(0, 74, 0));
        CellarReport fresh = detector.inspect(at(0, 64, 0));
        assertThat(fresh.fromCache()).isFalse();
        assertThat(fresh.cellar()).isFalse();
    }

    @Test
    @DisplayName("Unloaded chunks, invalid heights and null locations fail safely without block lookups")
    void shouldFailSafely() {
        chunksLoaded.set(false);
        CellarReport unloaded = detector.inspect(at(0, 64, 0));
        assertThat(unloaded.cellar()).isFalse();
        assertThat(unloaded.failureReason()).contains("chunk");
        assertThat(blockLookups.get()).isZero();

        chunksLoaded.set(true);
        assertThat(detector.inspect(at(0, 400, 0)).failureReason()).contains("límites");
        assertThat(detector.inspect(null).cellar()).isFalse();
        assertThat(detector.inspect(new Location(null, 0, 0, 0)).cellar()).isFalse();
        detector.invalidateAround(null);
    }

    @Test
    @DisplayName("Disabling cellars in config always reports no cellar")
    void disabledCellarsShouldNeverQualify() {
        config.setCellarEnabled(false);
        roof(0, 0, 65, 5, Material.STONE);
        CellarReport report = detector.inspect(at(0, 64, 0));
        assertThat(report.cellar()).isFalse();
        assertThat(report.failureReason()).contains("desactivadas");
    }
}
