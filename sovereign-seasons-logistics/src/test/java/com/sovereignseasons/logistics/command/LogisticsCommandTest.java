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

package com.sovereignseasons.logistics.command;

import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.sovereignseasons.logistics.weight.WeightModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsCommandTest {
    private final List<String> messages = new ArrayList<>();
    private final AtomicBoolean hasPermission = new AtomicBoolean(true);
    private LogisticsCommand command;
    private CommandSender console;

    @TempDir
    File tempDir;

    @BeforeEach
    void setup() {
        LogisticsConfig config = new LogisticsConfig(null);
        command = new LogisticsCommand(config, null, new WeightModel(config), null, null, tempDir);
        console = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> hasPermission.get();
                    case "getName" -> "CONSOLE";
                    case "sendMessage" -> {
                        if (args != null && args.length == 1 && args[0] instanceof Component component) {
                            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                        }
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private String last() {
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
    }

    @Test
    @DisplayName("Player-only subcommands reject the console gracefully")
    void playerOnlyCommandsShouldRejectConsole() {
        command.onCommand(console, null, "logistics", new String[]{});
        assertThat(last()).contains("jugador");
        command.onCommand(console, null, "logistics", new String[]{"weight"});
        assertThat(last()).contains("jugador");
    }

    @Test
    @DisplayName("Unknown subcommands show usage and permissions are enforced")
    void shouldHandleUnknownAndPermissions() {
        command.onCommand(console, null, "logistics", new String[]{"  NOPE "});
        assertThat(last()).contains("Subcomando desconocido");
        hasPermission.set(false);
        command.onCommand(console, null, "logistics", new String[]{"stats"});
        assertThat(last()).contains("No tienes permiso");
        command.onCommand(console, null, "logistics", new String[]{"reload"});
        assertThat(last()).contains("No tienes permiso");
    }

    @Test
    @DisplayName("Stats and reload work from the console")
    void statsAndReload() {
        command.onCommand(console, null, "logistics", new String[]{"STATS"});
        assertThat(messages).anyMatch(m -> m.contains("Movilidad estacional: activo") && m.contains("Peso: desactivado"));
        command.onCommand(console, null, "logistics", new String[]{"reload"});
        assertThat(last()).contains("recargada");
        assertThat(new File(tempDir, "config.yml")).exists();
    }

    @Test
    @DisplayName("Tab completion filters subcommands")
    void shouldTabComplete() {
        assertThat(command.onTabComplete(console, null, "logistics", new String[]{""})).containsExactly("status", "weight", "stats", "reload");
        assertThat(command.onTabComplete(console, null, "logistics", new String[]{"st"})).containsExactly("status", "stats");
        assertThat(command.onTabComplete(console, null, "logistics", new String[]{"status", "x"})).isEmpty();
    }
}
