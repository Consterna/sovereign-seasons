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

package com.sovereignseasons.pantry.command;

import com.sovereignseasons.pantry.cellar.CellarDetector;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.DecayCalculator;
import com.sovereignseasons.pantry.food.FoodDecayService;
import com.sovereignseasons.pantry.food.FoodState;
import com.sovereignseasons.pantry.food.FreshnessStage;
import com.sovereignseasons.pantry.item.FoodStateCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PantryCommandTest {
    private final List<String> messages = new ArrayList<>();
    private final AtomicBoolean hasPermission = new AtomicBoolean(true);
    private PantryCommand command;
    private PantryConfig config;
    private CommandSender console;

    @TempDir
    File tempDir;

    @BeforeEach
    void setup() {
        config = new PantryConfig(null);
        FoodStateCodec codec = new FoodStateCodec() {
            @Override
            public Optional<FoodState> read(ItemStack item) {
                return Optional.empty();
            }

            @Override
            public void write(ItemStack item, FoodState state, FreshnessStage stage) {
            }
        };
        FoodDecayService service = new FoodDecayService(config, new DecayCalculator(config), codec);
        command = new PantryCommand(config, service, null, new CellarDetector(config), tempDir);
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

    private String lastMessage() {
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
    }

    @Test
    @DisplayName("Player-only subcommands reject the console gracefully")
    void playerOnlyCommandsShouldRejectConsole() {
        command.onCommand(console, null, "pantry", new String[]{});
        assertThat(lastMessage()).contains("jugador");
        command.onCommand(console, null, "pantry", new String[]{"cellar"});
        assertThat(lastMessage()).contains("jugador");
        command.onCommand(console, null, "pantry", new String[]{"setfresh", "50"});
        assertThat(lastMessage()).contains("jugador");
    }

    @Test
    @DisplayName("Unknown subcommands show usage and permissions are enforced")
    void shouldHandleUnknownAndPermissions() {
        command.onCommand(console, null, "pantry", new String[]{"  FOO  "});
        assertThat(lastMessage()).contains("Subcomando desconocido");

        hasPermission.set(false);
        command.onCommand(console, null, "pantry", new String[]{"stats"});
        assertThat(lastMessage()).contains("No tienes permiso");
        command.onCommand(console, null, "pantry", new String[]{"reload"});
        assertThat(lastMessage()).contains("No tienes permiso");
    }

    @Test
    @DisplayName("Stats and reload work from the console")
    void statsAndReloadShouldWork() {
        command.onCommand(console, null, "pantry", new String[]{"STATS"});
        assertThat(messages).anyMatch(m -> m.contains("Alimentos perecederos configurados: " + config.getShelfLifeTable().size()));

        command.onCommand(console, null, "pantry", new String[]{"reload"});
        assertThat(lastMessage()).contains("recargada");
        assertThat(new File(tempDir, "config.yml")).exists();
    }

    @Test
    @DisplayName("Tab completion filters subcommands and percentages")
    void shouldTabComplete() {
        assertThat(command.onTabComplete(console, null, "pantry", new String[]{""}))
                .containsExactly("inspect", "cellar", "stats", "reload", "setfresh");
        assertThat(command.onTabComplete(console, null, "pantry", new String[]{"se"})).containsExactly("setfresh");
        assertThat(command.onTabComplete(console, null, "pantry", new String[]{"setfresh", "2"})).containsExactly("25");
        assertThat(command.onTabComplete(console, null, "pantry", new String[]{"inspect", "x"})).isEmpty();
    }
}
