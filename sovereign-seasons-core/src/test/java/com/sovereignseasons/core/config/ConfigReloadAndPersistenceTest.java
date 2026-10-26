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

package com.sovereignseasons.core.config;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConfigReloadAndPersistenceTest {

    private void writeConfigFile(File file, String yamlContent) throws IOException {
        Files.writeString(file.toPath(), yamlContent, StandardCharsets.UTF_8);
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
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Hot reload replaces material lists without retaining stale default entries")
    void shouldReplaceListsWithoutStaleEntries(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        // Verify initial defaults populated
        assertThat(config.getAffectedCrops()).hasSizeGreaterThan(5);
        assertThat(config.getAllowedGlassMaterials()).hasSizeGreaterThan(5);
        assertThat(config.getAllowedHeatSources()).hasSizeGreaterThan(5);

        // Write custom config with single item lists
        String customYaml = """
                crops:
                  affected-crops:
                    - WHEAT
                greenhouse:
                  allowed-glass-materials:
                    - GLASS
                  allowed-heat-sources:
                    - TORCH
                """;
        writeConfigFile(new File(tempDir, "config.yml"), customYaml);

        config.load(tempDir, null);

        // Lists must contain ONLY the customized items
        assertThat(config.getAffectedCrops()).containsExactly(Material.WHEAT);
        assertThat(config.getAllowedGlassMaterials()).containsExactly(Material.GLASS);
        assertThat(config.getAllowedHeatSources()).containsExactly(Material.TORCH);
    }

    @Test
    @DisplayName("Setting affected-crops to empty list in config clears all affected crops without retaining defaults")
    void shouldAllowEmptyAffectedCropsList(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);
        assertThat(config.getAffectedCrops()).isNotEmpty();

        String emptyListYaml = """
                crops:
                  affected-crops: []
                """;
        writeConfigFile(new File(tempDir, "config.yml"), emptyListYaml);

        config.load(tempDir, null);

        assertThat(config.getAffectedCrops()).isEmpty();
    }

    @Test
    @DisplayName("Hot reload changing days-per-season immediately updates date calculations")
    void shouldAdaptDateCalculationsWhenDaysPerSeasonChanges(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);

        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        // Day 10 in 7 days/season is Summer Day 4 (7 days Spring + 3 days Summer)
        AtomicLong fullTime = new AtomicLong(10 * 24000L);
        World world = createMockWorld("world", uid, fullTime);

        CalendarDate date7 = manager.getDate(world);
        assertThat(date7.season()).isEqualTo(Season.SUMMER);
        assertThat(date7.dayOfSeason()).isEqualTo(4);

        // Admin changes days-per-season to 14 in config.yml and reloads
        String reloadYaml = """
                calendar:
                  days-per-season: 14
                """;
        writeConfigFile(new File(tempDir, "config.yml"), reloadYaml);
        config.load(tempDir, null);

        // In 14 days/season, Day 10 is still SPRING (Day 11 of 14)!
        CalendarDate date14 = manager.getDate(world);
        assertThat(date14.season()).isEqualTo(Season.SPRING);
        assertThat(date14.dayOfSeason()).isEqualTo(11);
        assertThat(date14.totalDaysPerSeason()).isEqualTo(14);
    }

    @Test
    @DisplayName("saveData performs atomic file write and leaves no stranded temp files")
    void shouldPerformAtomicSaveWithoutStrandedTempFiles(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        UUID uid = UUID.randomUUID();
        World world = createMockWorld("world", uid, new AtomicLong(0L));
        manager.setSeason(world, Season.AUTUMN);

        File dataFile = new File(tempDir, "calendar_data.yml");
        File tempFile = new File(tempDir, "calendar_data.yml.tmp");

        assertThat(dataFile).exists();
        assertThat(dataFile.length()).isGreaterThan(0);
        assertThat(tempFile).doesNotExist(); // Temp file moved atomically!

        // Reload fresh instance to verify persisted content
        WorldCalendarManager freshManager = new WorldCalendarManager(config, tempDir, null);
        assertThat(freshManager.getSeason(world)).isEqualTo(Season.AUTUMN);
    }

    @Test
    @DisplayName("Corrupt or invalid calendar_data.yml recovers gracefully without throwing unhandled exceptions")
    void shouldHandleCorruptStorageGracefully(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);
        File dataFile = new File(tempDir, "calendar_data.yml");
        writeConfigFile(dataFile, ":- this is completely invalid yaml ::: {[[[[");

        assertThatCode(() -> {
            WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
            UUID uid = UUID.randomUUID();
            World world = createMockWorld("world", uid, new AtomicLong(0L));
            assertThat(manager.getSeason(world)).isEqualTo(Season.SPRING);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Hot reload resyncs WorldCalendarManager so subsequent ticks do not fire spurious SeasonChangeEvents")
    void shouldNotFireSpuriousSeasonChangeEventOnHotReload(@TempDir File tempDir) throws Exception {
        java.util.List<org.bukkit.event.Event> fired = new java.util.ArrayList<>();
        org.bukkit.plugin.PluginManager mockPm = (org.bukkit.plugin.PluginManager) Proxy.newProxyInstance(
                org.bukkit.plugin.PluginManager.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.PluginManager.class},
                (proxy, method, args) -> {
                    if ("callEvent".equals(method.getName())) {
                        fired.add((org.bukkit.event.Event) args[0]);
                    }
                    return null;
                }
        );

        UUID uid = UUID.randomUUID();
        // Day 10 in 7 days/season is Summer Day 4
        AtomicLong fullTime = new AtomicLong(10 * 24000L);
        World world = createMockWorld("world", uid, fullTime);

        org.bukkit.Server mockServer = (org.bukkit.Server) Proxy.newProxyInstance(
                org.bukkit.Server.class.getClassLoader(),
                new Class<?>[]{org.bukkit.Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> mockPm;
                    case "getWorlds" -> java.util.List.of(world);
                    case "getLogger" -> java.util.logging.Logger.getGlobal();
                    default -> null;
                }
        );

        java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, mockServer);

        try {
            SeasonsConfig config = new SeasonsConfig(null);
            config.setDaysPerSeason(7);
            WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

            manager.tickWorld(world);
            assertThat(manager.getSeason(world)).isEqualTo(Season.SUMMER);
            fired.clear();

            // Config reloaded to 14 days per season
            String reloadYaml = """
                    calendar:
                      days-per-season: 14
                    """;
            writeConfigFile(new File(tempDir, "config.yml"), reloadYaml);
            config.load(tempDir, null);
            manager.reload();

            // Next day passes: tick 11
            fullTime.set(11 * 24000L);
            manager.tickWorld(world);

            // Season is still SPRING under 14 days/season! No false season change event must be fired!
            long seasonChanges = fired.stream().filter(e -> e instanceof com.sovereignseasons.api.event.SeasonChangeEvent).count();
            assertThat(seasonChanges).isZero();
        } finally {
            serverField.set(null, null);
        }
    }
}
