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
import com.sovereignseasons.api.event.DayChangeEvent;
import com.sovereignseasons.api.event.SeasonChangeEvent;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.world.TimeSkipEvent;
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

class TimeDesyncAndDaylightTest {
    private SeasonsConfig config;
    private List<Event> firedEvents;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        config.setTicksPerDay(24000L);
        firedEvents = Collections.synchronizedList(new ArrayList<>());
        installMockBukkit();
    }

    @AfterEach
    void tearDown() {
        resetBukkitServer();
    }

    private void installMockBukkit() {
        PluginManager mockPluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if ("callEvent".equals(method.getName())) {
                        firedEvents.add((Event) args[0]);
                    }
                    return null;
                }
        );

        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> mockPluginManager;
                    case "getLogger" -> java.util.logging.Logger.getGlobal();
                    default -> null;
                }
        );

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Exception e) {
            throw new RuntimeException("Could not install mock Bukkit server", e);
        }
    }

    private void resetBukkitServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
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
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("doDaylightCycle false keeps calendar date, season, and progress completely stable without drift")
    void shouldRemainStableWhenDaylightCycleStopped(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(100_000L); // Middle of some day
        AtomicLong timeOfDay = new AtomicLong(4_000L);
        World world = createMockWorld("static_world", uid, fullTime, timeOfDay);

        // Initial tick establishes state
        manager.tickWorld(world);
        firedEvents.clear();

        CalendarDate initialDate = manager.getDate(world);

        // Simulate 100 ticks with time frozen (/gamerule doDaylightCycle false)
        for (int i = 0; i < 100; i++) {
            manager.tickWorld(world);
            CalendarDate checkDate = manager.getDate(world);
            assertThat(checkDate.season()).isEqualTo(initialDate.season());
            assertThat(checkDate.dayOfSeason()).isEqualTo(initialDate.dayOfSeason());
            assertThat(checkDate.totalDaysElapsed()).isEqualTo(initialDate.totalDaysElapsed());
            assertThat(checkDate.year()).isEqualTo(initialDate.year());
            assertThat(checkDate.getSeasonProgressPercent()).isEqualTo(initialDate.getSeasonProgressPercent());
        }

        // Zero rollover events should have been dispatched
        assertThat(firedEvents).isEmpty();
    }

    @Test
    @DisplayName("Player sleeping in beds triggers TimeSkipEvent and advances calendar immediately")
    void shouldAdvanceCalendarOnBedSleep(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        CalendarListener listener = new CalendarListener(manager);

        UUID uid = UUID.randomUUID();
        // Day 6 evening: tick 6 * 24000 + 13000 = 157,000 (Spring Day 7 night)
        AtomicLong fullTime = new AtomicLong(157_000L);
        AtomicLong timeOfDay = new AtomicLong(13_000L);
        World world = createMockWorld("world", uid, fullTime, timeOfDay);

        manager.tickWorld(world);
        firedEvents.clear();

        CalendarDate beforeSleep = manager.getDate(world);
        assertThat(beforeSleep.season()).isEqualTo(Season.SPRING);
        assertThat(beforeSleep.dayOfSeason()).isEqualTo(7);

        // Player sleeps! Vanilla advances time to morning of next day (tick 168,000 = Summer Day 1)
        long skipAmount = 11_000L;
        fullTime.addAndGet(skipAmount);
        timeOfDay.set(0L);

        TimeSkipEvent timeSkipEvent = new TimeSkipEvent(world, TimeSkipEvent.SkipReason.NIGHT_SKIP, skipAmount);
        listener.onTimeSkip(timeSkipEvent);

        // Calendar should immediately reflect Summer Day 1!
        CalendarDate afterSleep = manager.getDate(world);
        assertThat(afterSleep.season()).isEqualTo(Season.SUMMER);
        assertThat(afterSleep.dayOfSeason()).isEqualTo(1);
        assertThat(afterSleep.year()).isEqualTo(1);

        // Must have fired DayChangeEvent and SeasonChangeEvent!
        boolean hasDayChange = firedEvents.stream().anyMatch(e -> e instanceof DayChangeEvent);
        boolean hasSeasonChange = firedEvents.stream().anyMatch(e -> e instanceof SeasonChangeEvent);
        assertThat(hasDayChange).isTrue();
        assertThat(hasSeasonChange).isTrue();
    }

    @Test
    @DisplayName("Massive time skips (/time add 10,000,000) calculate exact calendar dates without overflow")
    void shouldHandleMassiveTimeJump(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        World world = createMockWorld("world", uid, fullTime, new AtomicLong(0L));

        manager.tickWorld(world);

        // Advance 10,000,000 ticks = 416.66 days = 14 full years (each 28 days) + 24.66 days
        // Year 15: Day 25 = Winter Day 4
        long massiveJump = 10_000_000L;
        fullTime.set(massiveJump);

        CalendarDate date = manager.getDate(world);
        assertThat(date.totalDaysElapsed()).isEqualTo((int) (massiveJump / 24000L));
        assertThat(date.year()).isEqualTo(15);
        assertThat(date.season()).isEqualTo(Season.WINTER);
        assertThat(date.dayOfSeason()).isEqualTo(4);

        manager.tickWorld(world);
        assertThat(firedEvents).isNotEmpty();
    }

    @Test
    @DisplayName("Backwards time reset (/time set 0 after years) recalculates cleanly without errors")
    void shouldHandleBackwardsTimeReset(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(5_000_000L); // Far into year 7
        World world = createMockWorld("world", uid, fullTime, new AtomicLong(0L));

        manager.tickWorld(world);
        firedEvents.clear();

        CalendarDate beforeReset = manager.getDate(world);
        assertThat(beforeReset.year()).isGreaterThan(1);

        // Admin runs /time set 0
        fullTime.set(0L);

        CalendarDate afterReset = manager.getDate(world);
        assertThat(afterReset.totalDaysElapsed()).isZero();
        assertThat(afterReset.year()).isEqualTo(1);
        assertThat(afterReset.season()).isEqualTo(Season.SPRING);
        assertThat(afterReset.dayOfSeason()).isEqualTo(1);

        // Calling tickWorld on backwards reset does not crash
        manager.tickWorld(world);
        assertThat(firedEvents).isNotEmpty();
    }

    @Test
    @DisplayName("Irregular daytime ticks (negative or >= 24000) normalize safely into [0, 24000)")
    void shouldNormalizeIrregularDaytimeTicks(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(24_000L);

        // Negative daytime ticks
        AtomicLong negativeDayTime = new AtomicLong(-1_500L);
        World worldNeg = createMockWorld("world_neg", uid, fullTime, negativeDayTime);
        CalendarDate dateNeg = manager.getDate(worldNeg);
        assertThat(dateNeg.timeOfDayTicks()).isEqualTo(22_500L); // ( -1500 % 24000 + 24000 ) % 24000 = 22500
        assertThat(dateNeg.getSeasonProgressPercent()).isBetween(0.0, 100.0);

        // Excessive daytime ticks (e.g. 50,000)
        AtomicLong excessiveDayTime = new AtomicLong(50_000L);
        World worldExcess = createMockWorld("world_excess", uid, fullTime, excessiveDayTime);
        CalendarDate dateExcess = manager.getDate(worldExcess);
        assertThat(dateExcess.timeOfDayTicks()).isEqualTo(2_000L); // 50000 % 24000 = 2000
        assertThat(dateExcess.getSeasonProgressPercent()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Backward time reset (/time set 0) after /season set unfreezes offset and advances future days normally")
    void shouldNotFreezeCalendarWhenWorldTimeResetAfterSeasonSet(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        // Day 100: 100 * 24000L = 2,400,000L
        AtomicLong fullTime = new AtomicLong(100 * 24000L);
        World world = createMockWorld("world", uid, fullTime, new AtomicLong(0L));

        manager.tickWorld(world);

        // Admin runs /season set spring while world is on day 100 (Day 100 is Year 4; Spring Day 1 is day 84)
        manager.setSeason(world, Season.SPRING);
        CalendarDate dateAfterSet = manager.getDate(world);
        assertThat(dateAfterSet.season()).isEqualTo(Season.SPRING);
        assertThat(dateAfterSet.totalDaysElapsed()).isEqualTo(84);

        // Admin runs /time set 0 (resets rawInGameDay to 0)
        fullTime.set(0L);
        manager.tickWorld(world);

        // Next day in Minecraft passes: fullTime becomes 24000L (raw day 1)
        fullTime.set(24000L);
        manager.tickWorld(world);

        // Calendar MUST NOT be frozen at day 0! It must advance to day 1!
        CalendarDate dateNextDay = manager.getDate(world);
        assertThat(dateNextDay.totalDaysElapsed()).isEqualTo(1);
        assertThat(dateNextDay.dayOfSeason()).isEqualTo(2);
        assertThat(dateNextDay.season()).isEqualTo(Season.SPRING);
    }
}
