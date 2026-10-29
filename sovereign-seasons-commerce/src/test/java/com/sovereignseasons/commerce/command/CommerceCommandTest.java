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

package com.sovereignseasons.commerce.command;

import com.sovereignseasons.commerce.config.CommerceConfig;
import com.sovereignseasons.commerce.market.CommerceStats;
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

class CommerceCommandTest {
    private final List<String> messages = new ArrayList<>();
    private final AtomicBoolean hasPermission = new AtomicBoolean(true);
    private CommerceConfig config;
    private CommerceStats stats;
    private CommerceCommand command;
    private CommandSender console;

    @TempDir
    File tempDir;

    @BeforeEach
    void setup() {
        config = new CommerceConfig(null);
        stats = new CommerceStats();
        command = new CommerceCommand(config, null, stats, () -> null, tempDir);
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
    @DisplayName("Prices list the products that are scarce or abundant in a season")
    void showsSeasonalPrices() {
        command.onCommand(console, null, "commerce", new String[]{"prices", "winter"});
        assertThat(messages).anyMatch(m -> m.contains("Invierno"));
        assertThat(messages).anyMatch(m -> m.contains("Carrot ×1.40") && m.contains("escaso"));
        assertThat(messages).anyMatch(m -> m.contains("productos más"));

        messages.clear();
        command.onCommand(console, null, "commerce", new String[]{"precios", "autumn"});
        assertThat(messages).anyMatch(m -> m.contains("Carrot ×0.80") && m.contains("abundante"));

        messages.clear();
        command.onCommand(console, null, "commerce", new String[]{"prices", "summer"});
        assertThat(messages).filteredOn(m -> m.contains("Lana (cualquier color) ×0.85")).hasSize(1);
        assertThat(messages).noneMatch(m -> m.contains("White wool"));

        messages.clear();
        command.onCommand(console, null, "commerce", new String[]{}); // console without worlds: spring
        assertThat(messages).anyMatch(m -> m.contains("Primavera"));
    }

    @Test
    @DisplayName("Unknown seasons and subcommands are reported, and disabled prices say so")
    void reportsErrors() {
        command.onCommand(console, null, "commerce", new String[]{"prices", "monsoon"});
        assertThat(last()).contains("Estación desconocida");
        command.onCommand(console, null, "commerce", new String[]{"  NOPE "});
        assertThat(last()).contains("Subcomando desconocido");
        config.setPricesEnabled(false);
        command.onCommand(console, null, "commerce", new String[]{"prices"});
        assertThat(last()).contains("desactivados");
    }

    @Test
    @DisplayName("Permissions are enforced")
    void enforcesPermissions() {
        hasPermission.set(false);
        command.onCommand(console, null, "commerce", new String[]{"prices"});
        assertThat(last()).contains("No tienes permiso");
        command.onCommand(console, null, "commerce", new String[]{"stats"});
        assertThat(last()).contains("No tienes permiso");
        command.onCommand(console, null, "commerce", new String[]{"reload"});
        assertThat(last()).contains("No tienes permiso");
    }

    @Test
    @DisplayName("Stats and reload work from the console")
    void statsAndReload() {
        stats.addBulkSale(3, 2);
        stats.addRefusedTrade();
        command.onCommand(console, null, "commerce", new String[]{"STATS"});
        assertThat(messages).anyMatch(m -> m.contains("Precios estacionales: activo") && m.contains("Venta a granel: activo"));
        assertThat(messages).anyMatch(m -> m.contains("Cajas vendidas: 3 por 2 esmeraldas") && m.contains("rechazada: 1"));
        command.onCommand(console, null, "commerce", new String[]{"reload"});
        assertThat(last()).contains("recargada");
        assertThat(new File(tempDir, "config.yml")).exists();
    }

    @Test
    @DisplayName("Tab completion offers subcommands and seasons")
    void shouldTabComplete() {
        assertThat(command.onTabComplete(console, null, "commerce", new String[]{""})).containsExactly("prices", "stats", "reload");
        assertThat(command.onTabComplete(console, null, "commerce", new String[]{"st"})).containsExactly("stats");
        assertThat(command.onTabComplete(console, null, "commerce", new String[]{"prices", "w"})).containsExactly("winter");
        assertThat(command.onTabComplete(console, null, "commerce", new String[]{"stats", "x"})).isEmpty();
    }
}
