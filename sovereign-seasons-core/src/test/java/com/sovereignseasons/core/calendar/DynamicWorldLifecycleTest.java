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

package com.sovereignseasons.core.calendar;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.sovereignseasons.core.crops.CropGrowthListener;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicWorldLifecycleTest {
    private SeasonsConfig config;
    private World mockWorldA;
    private World mockWorldB;
    private UUID uidA;
    private UUID uidB;
    private Map<String, World> serverWorlds;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        config.setTicksPerDay(24000L);

        uidA = UUID.randomUUID();
        uidB = UUID.randomUUID();
        mockWorldA = createMockWorld("world_a", uidA, new AtomicLong(0L));
        mockWorldB = createMockWorld("world_b", uidB, new AtomicLong(100_000L));

        serverWorlds = new HashMap<>();
        serverWorlds.put("world_a", mockWorldA);
        serverWorlds.put("world_b", mockWorldB);

        installMockBukkit();
    }

    @AfterEach
    void tearDown() {
        resetBukkitServer();
    }

    private void installMockBukkit() {
        PluginManager mockPm = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> null
        );

        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> mockPm;
                    case "getWorlds" -> new ArrayList<>(serverWorlds.values());
                    case "getWorld" -> serverWorlds.get((String) args[0]);
                    case "getLogger" -> java.util.logging.Logger.getGlobal();
                    default -> null;
                }
        );

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void resetBukkitServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
    }

    private World createMockWorld(String name, UUID uid, AtomicLong fullTime) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> fullTime.get();
                    case "getTime" -> fullTime.get() % 24000L;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "isChunkLoaded" -> true;
                    case "getBlockAt" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        Location loc = new Location((World) proxy, x, y, z);
                        yield Proxy.newProxyInstance(
                                org.bukkit.block.Block.class.getClassLoader(),
                                new Class<?>[]{org.bukkit.block.Block.class},
                                (p, m, a) -> switch (m.getName()) {
                                    case "getWorld" -> proxy;
                                    case "getType" -> Material.AIR;
                                    case "getLocation" -> loc;
                                    case "getBlockData" -> null;
                                    default -> null;
                                }
                        );
                    }
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Cancelled WorldUnloadEvent preserves tracking state and cache without premature purge")
    void shouldNotPurgeStateWhenWorldUnloadCancelled(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        GreenhouseDetector detector = new GreenhouseDetector(config);
        CalendarListener calListener = new CalendarListener(manager);
        CropGrowthListener cropListener = new CropGrowthListener(config, manager, detector);

        // Seed state
        manager.tickWorld(mockWorldA);
        assertThat(manager.getDate(mockWorldA)).isNotNull();

        Location loc = new Location(mockWorldA, 10, 64, 10);
        detector.isGreenhouse(loc);
        assertThat(detector.getCacheSize()).isEqualTo(1L);

        // Another plugin cancels the WorldUnloadEvent!
        WorldUnloadEvent event = new WorldUnloadEvent(mockWorldA);
        event.setCancelled(true);

        calListener.onWorldUnload(event);
        cropListener.onWorldUnload(event);

        // Tracking state and greenhouse cache MUST still be present because unload was cancelled!
        assertThat(detector.getCacheSize()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Normal WorldUnloadEvent persists offsets and unloads world tracking state cleanly")
    void shouldPersistOffsetsAndUnloadTrackingOnNormalUnload(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        GreenhouseDetector detector = new GreenhouseDetector(config);
        CalendarListener calListener = new CalendarListener(manager);
        CropGrowthListener cropListener = new CropGrowthListener(config, manager, detector);

        // Admin changed season to WINTER in world_a
        manager.setSeason(mockWorldA, Season.WINTER);
        Location loc = new Location(mockWorldA, 10, 64, 10);
        detector.isGreenhouse(loc);
        assertThat(detector.getCacheSize()).isEqualTo(1L);

        // Normal world unload
        WorldUnloadEvent event = new WorldUnloadEvent(mockWorldA);
        calListener.onWorldUnload(event);
        cropListener.onWorldUnload(event);

        // Cache must be purged for world_a
        assertThat(detector.getCacheSize()).isZero();

        // Data file must exist and have persisted the modified season
        File dataFile = new File(tempDir, "calendar_data.yml");
        assertThat(dataFile).exists();

        // Reload fresh instance to verify persisted offset
        WorldCalendarManager freshManager = new WorldCalendarManager(config, tempDir, null);
        assertThat(freshManager.getSeason(mockWorldA)).isEqualTo(Season.WINTER);
    }

    @Test
    @DisplayName("WorldLoadEvent initializes tracking for newly loaded world")
    void shouldInitializeTrackingOnWorldLoad(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        CalendarListener calListener = new CalendarListener(manager);

        UUID uidNew = UUID.randomUUID();
        World worldNew = createMockWorld("dynamic_world", uidNew, new AtomicLong(48_000L));
        serverWorlds.put("dynamic_world", worldNew);

        WorldLoadEvent loadEvent = new WorldLoadEvent(worldNew);
        calListener.onWorldLoad(loadEvent);

        CalendarDate date = manager.getDate(worldNew);
        assertThat(date.totalDaysElapsed()).isEqualTo(2);
        assertThat(date.season()).isEqualTo(Season.SPRING);
    }

    @Test
    @DisplayName("Hot reload removes tracking state for worlds that were removed from enabled-worlds")
    void shouldPurgeTrackingForWorldsDisabledOnReload(@TempDir File tempDir) {
        config.setEnabledWorlds(Set.of("world_a", "world_b"));
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        manager.tickWorld(mockWorldA);
        manager.tickWorld(mockWorldB);

        // Now admin updates config to ONLY enable world_a
        config.setEnabledWorlds(Set.of("world_a"));
        manager.reload();

        // world_a is enabled
        assertThat(manager.isEnabledInWorld(mockWorldA)).isTrue();
        // world_b is disabled
        assertThat(manager.isEnabledInWorld(mockWorldB)).isFalse();
    }
}
