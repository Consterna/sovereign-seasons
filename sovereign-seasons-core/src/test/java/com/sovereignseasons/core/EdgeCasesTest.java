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

package com.sovereignseasons.core;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class EdgeCasesTest {

    private World createMockWorld(World.Environment env, AtomicLong time) {
        UUID uid = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getName" -> "mock_" + env.name().toLowerCase();
                    case "getEnvironment" -> env;
                    case "getFullTime" -> time.get();
                    case "getTime" -> time.get() % 24000L;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Calendar rejects Nether and End worlds and accepts Overworld")
    void shouldOnlyEnableInOverworld(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        World overworld = createMockWorld(World.Environment.NORMAL, new AtomicLong(0L));
        World nether = createMockWorld(World.Environment.NETHER, new AtomicLong(0L));
        World end = createMockWorld(World.Environment.THE_END, new AtomicLong(0L));

        assertThat(manager.isEnabledInWorld(overworld)).isTrue();
        assertThat(manager.isEnabledInWorld(nether)).isFalse();
        assertThat(manager.isEnabledInWorld(end)).isFalse();
    }

    @Test
    @DisplayName("Handles massive tick numbers without overflow or desynchronization")
    void shouldHandleLargeTickNumbers(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        // 10 years of in-game time: 10 * 28 * 24000 = 6,720,000 ticks
        long tenYearsTicks = 6_720_000L;
        AtomicLong time = new AtomicLong(tenYearsTicks);
        World world = createMockWorld(World.Environment.NORMAL, time);

        CalendarDate date = manager.getDate(world);
        assertThat(date.year()).isEqualTo(11);
        assertThat(date.season()).isEqualTo(Season.SPRING);
        assertThat(date.dayOfSeason()).isEqualTo(1);
    }

    @Test
    @DisplayName("SubSeason calculation handles single-day and two-day seasons without error")
    void shouldHandleBoundarySeasonLengths() {
        assertThat(SubSeason.calculate(1, 1)).isEqualTo(SubSeason.MID);

        assertThat(SubSeason.calculate(1, 2)).isEqualTo(SubSeason.EARLY);
        assertThat(SubSeason.calculate(2, 2)).isEqualTo(SubSeason.LATE);
    }

    @Test
    @DisplayName("Clamps extreme setDayOfSeason values to valid range")
    void shouldClampExtremeDays(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        World world = createMockWorld(World.Environment.NORMAL, new AtomicLong(0L));

        // Negative day
        manager.setDayOfSeason(world, -10);
        assertThat(manager.getDate(world).dayOfSeason()).isEqualTo(1);

        // Day exceeding season length
        manager.setDayOfSeason(world, 999);
        assertThat(manager.getDate(world).dayOfSeason()).isEqualTo(7);
    }

    @Test
    @DisplayName("Greenhouse detection returns false immediately when greenhouse is disabled in config")
    void shouldBypassWhenDisabled() {
        SeasonsConfig config = new SeasonsConfig(null);
        config.setGreenhouseEnabled(false);
        GreenhouseDetector detector = new GreenhouseDetector(config);

        World world = createMockWorld(World.Environment.NORMAL, new AtomicLong(0L));
        Location loc = new Location(world, 0, 64, 0);

        assertThat(detector.isGreenhouse(loc)).isFalse();
    }

    @Test
    @DisplayName("Handles negative world time without index out of bounds or negative day calculations")
    void shouldHandleNegativeWorldTime(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        World world = createMockWorld(World.Environment.NORMAL, new AtomicLong(-100000L));
        CalendarDate date = manager.getDate(world);

        assertThat(date.totalDaysElapsed()).isZero();
        assertThat(date.year()).isEqualTo(1);
        assertThat(date.season()).isEqualTo(Season.SPRING);
        assertThat(date.dayOfSeason()).isEqualTo(1);
    }

    @Test
    @DisplayName("SovereignSeasonsApi.Holder resets to null on unbind and throws clean exception when uninitialized")
    void shouldHandleApiHolderLifecycle() {
        com.sovereignseasons.api.SovereignSeasonsApi.Holder.set(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(com.sovereignseasons.api.SovereignSeasonsApi::get)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("SovereignSeasonsApi has not been initialized yet");

        com.sovereignseasons.api.SovereignSeasonsApi mockApi = (com.sovereignseasons.api.SovereignSeasonsApi) Proxy.newProxyInstance(
                com.sovereignseasons.api.SovereignSeasonsApi.class.getClassLoader(),
                new Class<?>[]{com.sovereignseasons.api.SovereignSeasonsApi.class},
                (proxy, method, args) -> null
        );

        com.sovereignseasons.api.SovereignSeasonsApi.Holder.set(mockApi);
        assertThat(com.sovereignseasons.api.SovereignSeasonsApi.get()).isSameAs(mockApi);

        // Reset to null on plugin disable
        com.sovereignseasons.api.SovereignSeasonsApi.Holder.set(null);
        org.assertj.core.api.Assertions.assertThatThrownBy(com.sovereignseasons.api.SovereignSeasonsApi::get)
                .isInstanceOf(NullPointerException.class);
    }
}
