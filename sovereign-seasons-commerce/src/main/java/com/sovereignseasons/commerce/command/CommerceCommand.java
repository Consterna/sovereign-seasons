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

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.commerce.config.CommerceConfig;
import com.sovereignseasons.commerce.market.CommerceStats;
import com.sovereignseasons.commerce.pricing.ProductValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * /commerce command: seasonal market prices for players plus admin tooling.
 */
public class CommerceCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("prices", "stats", "reload");
    private static final int MAX_LINES = 20;

    private final CommerceConfig config;
    private final SeasonService seasons;
    private final CommerceStats stats;
    private final Supplier<World> defaultWorld;
    private final File dataFolder;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CommerceCommand(CommerceConfig config, SeasonService seasons, CommerceStats stats, Supplier<World> defaultWorld, File dataFolder) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasons = seasons;
        this.stats = Objects.requireNonNull(stats, "stats cannot be null");
        this.defaultWorld = defaultWorld;
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 || args[0].isBlank() ? "prices" : args[0].trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "prices", "precios" -> handlePrices(sender, args.length > 1 ? args[1] : null);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            default -> sendMsg(sender, "<red>Subcomando desconocido. Uso: <white>/commerce [prices [estación]|stats|reload]</white></red>");
        }
        return true;
    }

    private void handlePrices(CommandSender sender, String seasonArg) {
        if (!sender.hasPermission("sovereignseasons.commerce.prices")) {
            sendMsg(sender, "<red>No tienes permiso para consultar los precios del mercado.</red>");
            return;
        }
        if (!config.isPricesEnabled()) {
            sendMsg(sender, "<yellow>Los precios estacionales están desactivados en este servidor.</yellow>");
            return;
        }
        Season season = seasonArg != null ? Season.fromString(seasonArg) : currentSeason(sender);
        if (season == null) {
            sendMsg(sender, "<red>Estación desconocida: <white>" + seasonArg + "</white>. Usa spring, summer, autumn o winter.</red>");
            return;
        }
        ProductValues values = config.getProductValues();
        // One line per product (all wool colours share one), strongest changes first
        Map<String, Double> changed = new LinkedHashMap<>();
        for (Material product : values.trackedProducts()) {
            double value = bounded(values.value(product, season));
            if (Math.abs(value - 1.0) > 1e-9) {
                changed.putIfAbsent(label(product) + "|" + format(value), value);
            }
        }
        List<Map.Entry<String, Double>> lines = new ArrayList<>(changed.entrySet());
        lines.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> -Math.abs(Math.log(e.getValue())))
                .thenComparing(Map.Entry::getKey));
        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Mercado</b></gold> <gray>·</gray> " + season.getMiniMessageTag() + "<gray> ━━━━━</gray>");
        if (lines.isEmpty()) {
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Todos los productos a precio normal.</gray>");
            return;
        }
        int shown = 0;
        for (Map.Entry<String, Double> line : lines) {
            if (shown++ >= MAX_LINES) {
                sendMsg(sender, "<dark_gray>• … y " + (lines.size() - MAX_LINES) + " productos más.</dark_gray>");
                break;
            }
            double value = line.getValue();
            String name = line.getKey().substring(0, line.getKey().lastIndexOf('|'));
            String trend = value > 1.0 ? "<green>▲ escaso</green>" : "<red>▼ abundante</red>";
            sendMsg(sender, "<dark_gray>•</dark_gray> <white>" + name + "</white> <gray>×" + format(value) + "</gray> " + trend);
        }
        sendMsg(sender, "<dark_gray>▲ los aldeanos piden menos unidades al comprarlo y más esmeraldas al venderlo.</dark_gray>");
    }

    private Season currentSeason(CommandSender sender) {
        World world = sender instanceof Player player ? player.getWorld() : (defaultWorld != null ? defaultWorld.get() : null);
        if (world == null || seasons == null) {
            return Season.SPRING;
        }
        return seasons.getSeason(world);
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.commerce.admin")) {
            sendMsg(sender, "<red>No tienes permiso para ver las estadísticas.</red>");
            return;
        }
        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Estadísticas de Commerce</b></gold><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Precios estacionales:</gray> " + onOff(config.isPricesEnabled())
                + " <dark_gray>|</dark_gray> <gray>Ferias:</gray> " + onOff(config.isFairsEnabled())
                + " <dark_gray>|</dark_gray> <gray>Venta a granel:</gray> " + onOff(config.isBulkEnabled()));
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Ofertas ajustadas:</gray> <white>" + stats.getAdjustedOffers()
                + "</white> <dark_gray>|</dark_gray> <gray>Ferias abastecidas:</gray> <white>" + stats.getFairsStocked() + "</white>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Cajas vendidas:</gray> <white>" + stats.getCratesSold()
                + "</white> <gray>por</gray> <white>" + stats.getEmeraldsPaid() + "</white> <gray>esmeraldas</gray>"
                + " <dark_gray>|</dark_gray> <gray>Comida podrida rechazada:</gray> <white>" + stats.getRefusedTrades() + "</white>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.commerce.admin")) {
            sendMsg(sender, "<red>No tienes permiso para recargar la configuración.</red>");
            return;
        }
        try {
            InputStream defaultStream = getClass().getClassLoader().getResourceAsStream("config.yml");
            config.load(dataFolder, defaultStream);
            sendMsg(sender, "<green>Configuración de Commerce recargada.</green>");
        } catch (Exception e) {
            sendMsg(sender, "<red>Error al recargar la configuración: <white>" + e.getMessage() + "</white></red>");
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
        } else if (args.length == 2 && args[0].equalsIgnoreCase("prices")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Season season : Season.values()) {
                if (season.getIdentifier().startsWith(prefix)) {
                    suggestions.add(season.getIdentifier());
                }
            }
        }
        return suggestions;
    }

    private double bounded(double value) {
        return Math.max(config.getMinValue(), Math.min(config.getMaxValue(), value));
    }

    private static String label(Material material) {
        if (material.name().endsWith("_WOOL")) {
            return "Lana (cualquier color)";
        }
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static String onOff(boolean value) {
        return value ? "<green>activo</green>" : "<gray>desactivado</gray>";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void sendMsg(CommandSender sender, String miniMessageText) {
        Component component = miniMessage.deserialize(config.getPrefix() + miniMessageText);
        sender.sendMessage(component);
    }
}
