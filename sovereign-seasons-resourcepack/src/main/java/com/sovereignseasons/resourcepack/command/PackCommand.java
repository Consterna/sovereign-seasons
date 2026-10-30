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

package com.sovereignseasons.resourcepack.command;

import com.sovereignseasons.resourcepack.config.PackConfig;
import com.sovereignseasons.resourcepack.delivery.PackDelivery;
import com.sovereignseasons.resourcepack.pack.PackFile;
import com.sovereignseasons.resourcepack.pack.PackLibrary;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * /seasonpack: pack status and a way to get the packs again after declining them, plus admin statistics.
 */
public class PackCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("status", "resend", "stats");
    private static final String INACTIVE = "<red>El envío de resource packs no está activo en este servidor (revisa el alojamiento en la consola).</red>";

    private final PackConfig config;
    private final PackLibrary library;
    private final PackDelivery delivery;
    private final LongSupplier downloadsServed;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PackCommand(PackConfig config, PackLibrary library, PackDelivery delivery, LongSupplier downloadsServed) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.delivery = delivery;
        this.downloadsServed = downloadsServed;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 || args[0].isBlank() ? "status" : args[0].trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "resend", "reenviar" -> handleResend(sender);
            case "stats" -> handleStats(sender);
            default -> send(sender, "<red>Subcomando desconocido. Uso: <white>/seasonpack [status|resend|stats]</white></red>");
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pack.use")) {
            send(sender, "<red>No tienes permiso para consultar el resource pack.</red>");
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        if (delivery == null) {
            send(sender, INACTIVE);
            return;
        }
        send(sender, "<gray>Tu resource pack:</gray> <white>" + delivery.describe(player) + "</white>");
    }

    private void handleResend(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pack.use")) {
            send(sender, "<red>No tienes permiso para recibir el resource pack.</red>");
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        if (delivery == null) {
            send(sender, INACTIVE);
            return;
        }
        delivery.resend(player);
        send(sender, "<green>Resource pack enviado de nuevo.</green>");
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pack.admin")) {
            send(sender, "<red>No tienes permiso para ver las estadísticas.</red>");
            return;
        }
        send(sender, "<gray>━━━━━ </gray><gold><b>Resource pack</b></gold><gray> ━━━━━</gray>");
        send(sender, "<dark_gray>•</dark_gray> <gray>Alojamiento:</gray> <white>"
                + (config.getHostingMode() == PackConfig.HostingMode.SELF ? "propio (puerto " + config.getPort() + ")" : "externo") + "</white>");
        for (PackFile pack : library.all().values()) {
            send(sender, "<dark_gray>•</dark_gray> <white>" + pack.fileName() + "</white> <gray>" + pack.data().length / 1024 + " KB, SHA-1 "
                    + pack.sha1Hex().substring(0, 12) + "…</gray>");
        }
        if (delivery != null) {
            send(sender, "<dark_gray>•</dark_gray> <gray>Enviados:</gray> <white>" + delivery.getSent() + "</white> <dark_gray>|</dark_gray> <gray>Cargados:</gray> <white>"
                    + delivery.getLoaded() + "</white> <dark_gray>|</dark_gray> <gray>Rechazados:</gray> <white>" + delivery.getDeclined()
                    + "</white> <dark_gray>|</dark_gray> <gray>Fallidos:</gray> <white>" + delivery.getFailed() + "</white>");
        }
        if (downloadsServed != null) {
            send(sender, "<dark_gray>•</dark_gray> <gray>Descargas servidas:</gray> <white>" + downloadsServed.getAsLong() + "</white>");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    suggestions.add(sub);
                }
            }
        }
        return suggestions;
    }

    private void send(CommandSender sender, String text) {
        sender.sendMessage(miniMessage.deserialize(config.getPrefix() + text));
    }
}
