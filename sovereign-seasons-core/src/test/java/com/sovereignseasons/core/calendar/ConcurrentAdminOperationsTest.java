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
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentAdminOperationsTest {
    private SeasonsConfig config;
    private World mockWorld;
    private UUID worldUid;
    private AtomicLong fullTime;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        config.setTicksPerDay(24000L);

        worldUid = UUID.randomUUID();
        fullTime = new AtomicLong(0L);
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "getUID" -> worldUid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> fullTime.get();
                    case "getTime" -> fullTime.get() % 24000L;
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );

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
                    case "getWorlds" -> List.of(mockWorld);
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

    @Test
    @DisplayName("Concurrent setSeason, setDayOfSeason, reload, and tickWorld execute without race conditions or deadlocks")
    void shouldExecuteConcurrentAdminOperationsCleanly(@TempDir File tempDir) throws Exception {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        int threadCount = 20;
        int operationsPerThread = 250;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOps = new AtomicInteger(0);
        Season[] seasons = Season.values();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        switch (i % 4) {
                            case 0 -> manager.setSeason(mockWorld, seasons[(threadId + i) % seasons.length]);
                            case 1 -> manager.setDayOfSeason(mockWorld, (i % 7) + 1);
                            case 2 -> manager.tickWorld(mockWorld);
                            case 3 -> manager.reload();
                        }
                        successfulOps.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successfulOps.get()).isEqualTo(threadCount * operationsPerThread);

        // Verification: calendar date is coherent and valid
        CalendarDate finalDate = manager.getDate(mockWorld);
        assertThat(finalDate).isNotNull();
        assertThat(finalDate.dayOfSeason()).isBetween(1, 7);
        assertThat(finalDate.season()).isNotNull();
    }

    @Test
    @DisplayName("High concurrent getDate calls immediately after backward time reset execute offset normalization atomically")
    void shouldNormalizeOffsetAtomicallyUnderHighConcurrency(@TempDir File tempDir) throws Exception {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        // Advance world to day 100
        fullTime.set(100 * 24000L);
        manager.setSeason(mockWorld, Season.SPRING);

        // Admin runs /time set 0
        fullTime.set(0L);

        int threadCount = 16;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        CalendarDate date = manager.getDate(mockWorld);
                        assertThat(date.totalDaysElapsed()).isGreaterThanOrEqualTo(0);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount * iterations);
    }
}
