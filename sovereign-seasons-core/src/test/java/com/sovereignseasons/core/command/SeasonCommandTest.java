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
import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonCommandTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorld;
    private List<String> sentMessages;
    private File tempDir;

    @BeforeEach
    void setup(@TempDir File tempDir) {
        this.tempDir = tempDir;
        this.config = new SeasonsConfig(null);
        this.sentMessages = new ArrayList<>();

        UUID worldUid = UUID.randomUUID();
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldUid;
                    case "getName" -> "world";
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );

        CalendarDate dummyDate = new CalendarDate(Season.SPRING, SubSeason.MID, 3, 7, 2, 1, 6000L);

        mockSeasonService = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "getDate" -> dummyDate;
                    case "getSeason" -> Season.SPRING;
                    case "isWinter" -> false;
                    default -> null;
                }
        );

        mockGreenhouseService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isGreenhouse" -> true;
                    case "getCacheHits" -> 42L;
                    case "getCacheMisses" -> 8L;
                    case "getCacheSize" -> 50L;
                    case "inspect" -> GreenhouseReport.success(
                            new Location(mockWorld, 0, 64, 0),
                            true,
                            new Location(mockWorld, 0, 68, 0),
                            Material.GLASS,
                            4,
                            new Location(mockWorld, 1, 64, 1),
                            Material.CAMPFIRE,
                            1.4
                    );
                    default -> null;
                }
        );
    }

    private CommandSender createMockSender(boolean isAdmin) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        String perm = (String) args[0];
                        if (isAdmin) yield true;
                        yield perm.equals("sovereignseasons.command.info") || perm.equals("sovereignseasons.command.greenhouse");
                    }
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

    private Player createMockPlayer(boolean isAdmin) {
        Location loc = new Location(mockWorld, 10, 64, 10);
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        String perm = (String) args[0];
                        if (isAdmin) yield true;
                        yield perm.equals("sovereignseasons.command.info") || perm.equals("sovereignseasons.command.greenhouse");
                    }
                    case "getWorld" -> mockWorld;
                    case "getLocation" -> loc;
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
    @DisplayName("/season info displays calendar info to players and console")
    void shouldExecuteInfoCommand() {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        Player player = createMockPlayer(false);

        boolean handled = cmd.onCommand(player, createDummyCommand(), "season", new String[]{"info"});
        assertThat(handled).isTrue();
        assertThat(sentMessages).isNotEmpty();
        assertThat(sentMessages.stream().anyMatch(msg -> msg.contains("Calendario de Estaciones"))).isTrue();
    }

    @Test
    @DisplayName("/season stats requires admin permission")
    void shouldEnforceAdminPermissionForStats() {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender nonAdmin = createMockSender(false);

        cmd.onCommand(nonAdmin, createDummyCommand(), "season", new String[]{"stats"});
        assertThat(sentMessages.stream().anyMatch(msg -> msg.contains("No tienes permiso"))).isTrue();

        sentMessages.clear();
        CommandSender admin = createMockSender(true);
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"stats"});
        assertThat(sentMessages.stream().anyMatch(msg -> msg.contains("Estadísticas de Rendimiento"))).isTrue();
    }

    @Test
    @DisplayName("/season greenhouse provides detailed inspection for player")
    void shouldExecuteGreenhouseCommand() {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        Player player = createMockPlayer(true);

        cmd.onCommand(player, createDummyCommand(), "season", new String[]{"greenhouse"});
        assertThat(sentMessages.stream().anyMatch(msg -> msg.contains("Diagnóstico de Invernadero"))).isTrue();
        assertThat(sentMessages.stream().anyMatch(msg -> msg.contains("FUNCIONAL"))).isTrue();
    }

    @Test
    @DisplayName("Tab completion filters subcommands and seasons properly")
    void shouldProvideAccurateTabCompletions() {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockSender(true);
        CommandSender nonAdmin = createMockSender(false);

        // Subcommands for non-admin: only info, greenhouse
        List<String> nonAdminSubs = cmd.onTabComplete(nonAdmin, createDummyCommand(), "season", new String[]{""});
        assertThat(nonAdminSubs).containsExactlyInAnyOrder("info", "greenhouse");

        // Subcommands for admin: includes set, setday, stats, reload
        List<String> adminSubs = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{""});
        assertThat(adminSubs).contains("info", "greenhouse", "set", "setday", "stats", "reload");

        // Prefix matching
        List<String> sFilter = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"se"});
        assertThat(sFilter).containsExactly("set", "setday");

        // Season arguments for /season set
        List<String> seasonArgs = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"set", "w"});
        assertThat(seasonArgs).containsExactly("winter");

        List<String> spanishArgs = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"set", "i"});
        assertThat(spanishArgs).containsExactly("invierno");
    }
}
