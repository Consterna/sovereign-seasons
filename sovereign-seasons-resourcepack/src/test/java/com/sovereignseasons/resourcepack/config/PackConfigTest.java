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

package com.sovereignseasons.resourcepack.config;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.resourcepack.command.PackCommand;
import com.sovereignseasons.resourcepack.pack.PackLibrary;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PackConfigTest {

    private static PackConfig loaded(File dir, String yaml) throws Exception {
        Files.writeString(new File(dir, "config.yml").toPath(), yaml, StandardCharsets.UTF_8);
        PackConfig config = new PackConfig(null);
        config.load(dir, null);
        return config;
    }

    @Test
    @DisplayName("The bundled config.yml gives the documented defaults: optional packs, built-in host on 8163")
    void bundledDefaults(@TempDir File dir) {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(bundled).isNotNull();
        PackConfig config = new PackConfig(null);
        config.load(dir, bundled);
        assertThat(config.isBasePackEnabled()).isTrue();
        assertThat(config.isSeasonalPacksEnabled()).isTrue();
        assertThat(config.getSeasonsWithPack()).containsExactlyInAnyOrder(Season.SPRING, Season.AUTUMN, Season.WINTER);
        assertThat(config.isForce()).isFalse();
        assertThat(config.isSeasonIcons()).isTrue();
        assertThat(config.getHostingMode()).isEqualTo(PackConfig.HostingMode.SELF);
        assertThat(config.getPort()).isEqualTo(8163);
        assertThat(config.getPublicUrl()).isEmpty();
        assertThat(config.getSendDelayTicks()).isEqualTo(20);
    }

    @Test
    @DisplayName("Invalid values fall back safely and summer never gets a seasonal pack")
    void invalidValues(@TempDir File dir) throws Exception {
        PackConfig config = loaded(dir, """
                packs:
                  seasons-with-colors: [summer, winter, monsoon]
                  send-delay-ticks: 99999
                hosting:
                  mode: ftp
                  self:
                    port: 70000
                    public-url: "ftp://nope"
                  external:
                    base-url: "not a url"
                """);
        assertThat(config.getSeasonsWithPack()).containsExactly(Season.WINTER);
        assertThat(config.getSendDelayTicks()).isEqualTo(200);
        assertThat(config.getHostingMode()).isEqualTo(PackConfig.HostingMode.SELF);
        assertThat(config.getPort()).isEqualTo(8163);
        assertThat(config.getPublicUrl()).isEmpty();
        assertThat(config.getExternalBaseUrl()).isEmpty();

        PackConfig broken = loaded(dir, "packs: [unclosed\n  : :");
        assertThat(broken.isBasePackEnabled()).isTrue();
        assertThat(broken.getPort()).isEqualTo(8163);
    }

    @Test
    @DisplayName("/seasonpack works from the console for stats and explains the player-only subcommands")
    void command(@TempDir File dir) throws Exception {
        List<String> messages = new ArrayList<>();
        AtomicBoolean permitted = new AtomicBoolean(true);
        CommandSender console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> permitted.get();
                    case "sendMessage" -> {
                        if (args != null && args.length == 1 && args[0] instanceof Component component) {
                            messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                        }
                        yield null;
                    }
                    default -> null;
                });
        PackLibrary library = PackLibrary.load(path -> getClass().getClassLoader().getResourceAsStream(path), null);
        assertThat(library.all().keySet()).containsExactly("base", "spring", "autumn", "winter");
        PackCommand command = new PackCommand(loaded(dir, ""), library, null, () -> 7L);

        command.onCommand(console, null, "seasonpack", new String[]{"stats"});
        assertThat(messages).anyMatch(m -> m.contains("propio (puerto 8163)"));
        assertThat(messages).anyMatch(m -> m.contains("sovereign-seasons-winter.zip") && m.contains("SHA-1"));
        assertThat(messages).anyMatch(m -> m.contains("Descargas servidas: 7"));
        command.onCommand(console, null, "seasonpack", new String[]{"resend"});
        assertThat(messages.get(messages.size() - 1)).contains("jugador");
        command.onCommand(console, null, "seasonpack", new String[]{"nope"});
        assertThat(messages.get(messages.size() - 1)).contains("Subcomando desconocido");
        permitted.set(false);
        command.onCommand(console, null, "seasonpack", new String[]{"stats"});
        assertThat(messages.get(messages.size() - 1)).contains("No tienes permiso");
        assertThat(command.onTabComplete(console, null, "seasonpack", new String[]{"s"})).containsExactly("status", "stats");

        File exported = new File(dir, "packs");
        library.export(exported);
        assertThat(new File(exported, "sovereign-seasons-base.zip")).hasBinaryContent(library.get("base").orElseThrow().data());
    }
}
