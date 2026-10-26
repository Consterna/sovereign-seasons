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
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class WorldCalendarManagerTest {
    private SeasonsConfig config;
    private File dataFolder;

    @BeforeEach
    void setup(@TempDir File tempDir) {
        this.dataFolder = tempDir;
        this.config = new SeasonsConfig(null);
        this.config.setDaysPerSeason(7);
        this.config.setTicksPerDay(24000L);
    }

    private World createMockWorld(String name, UUID uid, AtomicLong fullTime, AtomicLong timeOfDay) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> fullTime.get();
                    case "getTime" -> timeOfDay.get();
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    case "toString" -> "MockWorld[" + name + "]";
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Calculates in-game days, seasons and year transitions from world ticks")
    void shouldCalculateSeasonsFromTicks() {
        WorldCalendarManager manager = new WorldCalendarManager(config, dataFolder, null);
        UUID worldUid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        AtomicLong timeOfDay = new AtomicLong(0L);
        World world = createMockWorld("survival", worldUid, fullTime, timeOfDay);

        // Tick 0 -> Day 1, Spring, Year 1
        CalendarDate dateDay1 = manager.getDate(world);
        assertThat(dateDay1.season()).isEqualTo(Season.SPRING);
        assertThat(dateDay1.dayOfSeason()).isEqualTo(1);
        assertThat(dateDay1.year()).isEqualTo(1);
        assertThat(dateDay1.totalDaysElapsed()).isEqualTo(0);

        // Tick 24,000 -> Day 2, Spring, Year 1
        fullTime.set(24000L);
        CalendarDate dateDay2 = manager.getDate(world);
        assertThat(dateDay2.season()).isEqualTo(Season.SPRING);
        assertThat(dateDay2.dayOfSeason()).isEqualTo(2);
        assertThat(dateDay2.year()).isEqualTo(1);

        // Tick 7 * 24,000 = 168,000 -> Day 1, Summer, Year 1
        fullTime.set(7 * 24000L);
        CalendarDate dateSummer = manager.getDate(world);
        assertThat(dateSummer.season()).isEqualTo(Season.SUMMER);
        assertThat(dateSummer.dayOfSeason()).isEqualTo(1);
        assertThat(dateSummer.year()).isEqualTo(1);

        // Tick 14 * 24,000 = 336,000 -> Day 1, Autumn, Year 1
        fullTime.set(14 * 24000L);
        CalendarDate dateAutumn = manager.getDate(world);
        assertThat(dateAutumn.season()).isEqualTo(Season.AUTUMN);
        assertThat(dateAutumn.dayOfSeason()).isEqualTo(1);
        assertThat(dateAutumn.year()).isEqualTo(1);

        // Tick 21 * 24,000 = 504,000 -> Day 1, Winter, Year 1
        fullTime.set(21 * 24000L);
        CalendarDate dateWinter = manager.getDate(world);
        assertThat(dateWinter.season()).isEqualTo(Season.WINTER);
        assertThat(dateWinter.dayOfSeason()).isEqualTo(1);
        assertThat(dateWinter.year()).isEqualTo(1);
        assertThat(manager.isWinter(world)).isTrue();

        // Tick 28 * 24,000 = 672,000 -> Day 1, Spring, Year 2 (Year Rollover!)
        fullTime.set(28 * 24000L);
        CalendarDate dateYear2 = manager.getDate(world);
        assertThat(dateYear2.season()).isEqualTo(Season.SPRING);
        assertThat(dateYear2.dayOfSeason()).isEqualTo(1);
        assertThat(dateYear2.year()).isEqualTo(2);
        assertThat(manager.isWinter(world)).isFalse();
    }

    @Test
    @DisplayName("Admin setSeason adjusts offset without mutating raw world time")
    void shouldAdjustSeasonViaOffset() {
        WorldCalendarManager manager = new WorldCalendarManager(config, dataFolder, null);
        UUID worldUid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L); // Raw tick 0 (Spring)
        World world = createMockWorld("survival", worldUid, fullTime, new AtomicLong(0L));

        assertThat(manager.getSeason(world)).isEqualTo(Season.SPRING);

        // Admin forces Winter
        manager.setSeason(world, Season.WINTER);
        CalendarDate updated = manager.getDate(world);

        assertThat(updated.season()).isEqualTo(Season.WINTER);
        assertThat(updated.dayOfSeason()).isEqualTo(1);
        assertThat(updated.year()).isEqualTo(1);
        assertThat(fullTime.get()).isEqualTo(0L); // World ticks untouched!

        // Persist and reload
        WorldCalendarManager reloadedManager = new WorldCalendarManager(config, dataFolder, null);
        CalendarDate reloadedDate = reloadedManager.getDate(world);
        assertThat(reloadedDate.season()).isEqualTo(Season.WINTER);
    }

    @Test
    @DisplayName("Admin setDayOfSeason clamps and updates day accurately")
    void shouldAdjustDayOfSeason() {
        WorldCalendarManager manager = new WorldCalendarManager(config, dataFolder, null);
        UUID worldUid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        World world = createMockWorld("survival", worldUid, fullTime, new AtomicLong(0L));

        manager.setDayOfSeason(world, 5);
        CalendarDate updated = manager.getDate(world);

        assertThat(updated.season()).isEqualTo(Season.SPRING);
        assertThat(updated.dayOfSeason()).isEqualTo(5);
    }

    @Test
    @DisplayName("calculateDate computes exact dates across boundaries")
    void shouldComputeExactDates() {
        WorldCalendarManager manager = new WorldCalendarManager(config, dataFolder, null);

        // Day 27 (Winter Day 7, Year 1)
        CalendarDate date27 = manager.calculateDate(27, 0L);
        assertThat(date27.season()).isEqualTo(Season.WINTER);
        assertThat(date27.dayOfSeason()).isEqualTo(7);
        assertThat(date27.year()).isEqualTo(1);

        // Day 28 (Spring Day 1, Year 2)
        CalendarDate date28 = manager.calculateDate(28, 0L);
        assertThat(date28.season()).isEqualTo(Season.SPRING);
        assertThat(date28.dayOfSeason()).isEqualTo(1);
        assertThat(date28.year()).isEqualTo(2);
    }

    @Test
    @DisplayName("tickWorld accurately calculates previous date on day, season, and year rollover")
    void shouldTrackRolloverAccurately() {
        WorldCalendarManager manager = new WorldCalendarManager(config, dataFolder, null);
        UUID worldUid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(27 * 24000L); // Day 27 (Winter Day 7, Year 1)
        World world = createMockWorld("survival", worldUid, fullTime, new AtomicLong(0L));

        // Initial tick records Day 27
        manager.tickWorld(world);

        // Advance to Day 28 (Spring Day 1, Year 2)
        fullTime.set(28 * 24000L);
        manager.tickWorld(world);

        CalendarDate currentDate = manager.getDate(world);
        assertThat(currentDate.season()).isEqualTo(Season.SPRING);
        assertThat(currentDate.dayOfSeason()).isEqualTo(1);
        assertThat(currentDate.year()).isEqualTo(2);
    }
}
