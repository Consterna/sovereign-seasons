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

package com.sovereignseasons.core.command;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class MalformedCommandEdgeCasesTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorldA;
    private List<String> sentMessages;
    private Map<String, World> serverWorlds;
    private Map<UUID, Season> worldSeasons;
    private Map<UUID, Integer> worldDays;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        sentMessages = new ArrayList<>();
        serverWorlds = new HashMap<>();
        worldSeasons = new HashMap<>();
        worldDays = new HashMap<>();

        UUID uidA = UUID.randomUUID();
        mockWorldA = createMockWorld("world_a", uidA);
        serverWorlds.put("world_a", mockWorldA);
        worldSeasons.put(uidA, Season.SPRING);
        worldDays.put(uidA, 1);

        installMockBukkit();

        mockSeasonService = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "getDate" -> {
                        World w = (World) args[0];
                        Season s = worldSeasons.getOrDefault(w.getUID(), Season.SPRING);
                        int day = worldDays.getOrDefault(w.getUID(), 1);
                        yield new CalendarDate(s, SubSeason.MID, day, 7, day - 1, 1, 6000L);
                    }
                    case "getSeason" -> {
                        World w = (World) args[0];
                        yield worldSeasons.getOrDefault(w.getUID(), Season.SPRING);
                    }
                    case "setSeason" -> {
                        World w = (World) args[0];
                        Season target = (Season) args[1];
                        worldSeasons.put(w.getUID(), target);
                        yield null;
                    }
                    case "setDayOfSeason" -> {
                        World w = (World) args[0];
                        int day = (int) args[1];
                        worldDays.put(w.getUID(), day);
                        yield null;
                    }
                    default -> null;
                }
        );

        mockGreenhouseService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "clearCache" -> null;
                    case "getCacheHits" -> 0L;
                    case "getCacheMisses" -> 0L;
                    case "getCacheSize" -> 0L;
                    default -> null;
                }
        );
    }

    @AfterEach
    void tearDown() {
        resetBukkitServer();
    }

    private void installMockBukkit() {
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> serverWorlds.get((String) args[0]);
                    case "getWorlds" -> new ArrayList<>(serverWorlds.values());
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

    private World createMockWorld(String name, UUID uid) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> 0L;
                    case "getTime" -> 0L;
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private CommandSender createMockAdminSender() {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> true;
                    case "sendMessage" -> {
                        if (args[0] instanceof Component comp) {
                            sentMessages.add(comp.toString());
                        } else if (args[0] instanceof String str) {
                            sentMessages.add(str);
                        }
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private Command createDummyCommand() {
        return new Command("season") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };
    }

    @Test
    @DisplayName("Empty or blank arguments (/season '' or /season '   ') display calendar info cleanly without error")
    void shouldHandleEmptyOrBlankSubcommands(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockAdminSender();

        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{""});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("calendario de estaciones"));

        sentMessages.clear();
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"   "});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("calendario de estaciones"));
    }

    @Test
    @DisplayName("Trailing and leading whitespace in arguments are stripped gracefully")
    void shouldHandleWhitespaceInArguments(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockAdminSender();

        // /season set   winter   world_a
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"set", "  winter  ", "  world_a  "});
        assertThat(worldSeasons.get(mockWorldA.getUID())).isEqualTo(Season.WINTER);
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("cambiada a"));

        // /season setday   5   world_a
        sentMessages.clear();
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "  5  ", "  world_a  "});
        assertThat(worldDays.get(mockWorldA.getUID())).isEqualTo(5);
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("ajustado a"));
    }

    @Test
    @DisplayName("setday accepts numbers with leading plus sign (+4)")
    void shouldAcceptLeadingPlusInSetDay(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockAdminSender();

        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "+4", "world_a"});
        assertThat(worldDays.get(mockWorldA.getUID())).isEqualTo(4);
    }

    @Test
    @DisplayName("setday gracefully rejects massive overflow integers without unhandled exception")
    void shouldRejectHugeIntegerOverflowInSetDay(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockAdminSender();

        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "999999999999999999999999999999", "world_a"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("debe ser un número entero"));
    }

    @Test
    @DisplayName("Reload handles non-existent or corrupted data folder without crashing")
    void shouldHandleReloadGracefullyUnderIOException(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, new File(tempDir, "non_existent_subfolder"));
        CommandSender admin = createMockAdminSender();

        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"reload"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("recargada"));
    }
}
