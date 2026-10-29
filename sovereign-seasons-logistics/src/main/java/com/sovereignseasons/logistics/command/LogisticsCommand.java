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
import com.sovereignseasons.logistics.ice.FrozenWaterService;
import com.sovereignseasons.logistics.mobility.MobilityService;
import com.sovereignseasons.logistics.weight.InventoryWeigher;
import com.sovereignseasons.logistics.weight.WeightModel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * /logistics command: mobility and load diagnostics plus admin tooling.
 */
public class LogisticsCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("status", "weight", "stats", "reload");

    private final LogisticsConfig config;
    private final MobilityService mobility;
    private final WeightModel weightModel;
    private final InventoryWeigher weigher;
    private final FrozenWaterService ice;
    private final File dataFolder;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LogisticsCommand(LogisticsConfig config, MobilityService mobility, WeightModel weightModel, InventoryWeigher weigher,
                            FrozenWaterService ice, File dataFolder) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.mobility = mobility;
        this.weightModel = Objects.requireNonNull(weightModel, "weightModel cannot be null");
        this.weigher = weigher;
        this.ice = ice;
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 || args[0].isBlank() ? "status" : args[0].trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender);
            case "weight", "peso" -> handleWeight(sender);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            default -> sendMsg(sender, "<red>Subcomando desconocido. Uso: <white>/logistics [status|weight|stats|reload]</white></red>");
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.logistics.status")) {
            sendMsg(sender, "<red>No tienes permiso para consultar tu estado de viaje.</red>");
            return;
        }
        if (!(sender instanceof Player player) || mobility == null) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        MobilityService.Snapshot snapshot = mobility.getSnapshot(player);
        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Logística</b></gold><gray> ━━━━━</gray>");
        if (snapshot == null) {
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Sin datos todavía (los modos creativo y espectador no se evalúan).</gray>");
            return;
        }
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Terreno:</gray> <white>" + snapshot.surface().getDisplayName() + "</white>"
                + (snapshot.mounted() ? " <gray>(a caballo)</gray>" : "") + (snapshot.blizzard() ? " <aqua>+ Ventisca</aqua>" : ""));
        String effect = snapshot.effect().isNone() ? "<green>Ninguno</green>"
                : (snapshot.effect().slowness() > 0 ? "<red>Lentitud " + roman(snapshot.effect().slowness()) + "</red> " : "")
                + (snapshot.effect().speed() > 0 ? "<green>Velocidad " + roman(snapshot.effect().speed()) + "</green>" : "");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Efecto:</gray> " + effect);
        if (config.isWeightEnabled()) {
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Carga:</gray> <white>" + format(snapshot.weight()) + "/" + format(config.getWeightCapacity())
                    + "</white> <gray>(" + snapshot.load().getDisplayName() + ")</gray>");
        }
    }

    private void handleWeight(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.logistics.status")) {
            sendMsg(sender, "<red>No tienes permiso para consultar tu carga.</red>");
            return;
        }
        if (!(sender instanceof Player player) || weigher == null) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        if (!config.isWeightEnabled()) {
            sendMsg(sender, "<yellow>El sistema de peso de carga está desactivado en este servidor.</yellow>");
            return;
        }
        double weight = weigher.weigh(player.getInventory().getContents());
        WeightModel.Load load = weightModel.load(weight);
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Carga:</gray> <white>" + format(weight) + "/" + format(config.getWeightCapacity())
                + "</white> <gray>(" + load.getDisplayName() + ")</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Consejo:</gray> <white>las mulas, llamas y vagonetas no cuentan, y las cajas pesan menos.</white>");
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.logistics.admin")) {
            sendMsg(sender, "<red>No tienes permiso para ver las estadísticas.</red>");
            return;
        }
        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Estadísticas de Logística</b></gold><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Movilidad estacional:</gray> " + onOff(config.isMobilityEnabled())
                + " <dark_gray>|</dark_gray> <gray>Peso:</gray> " + onOff(config.isWeightEnabled())
                + " <dark_gray>|</dark_gray> <gray>Cajas:</gray> " + onOff(config.isCratesEnabled()));
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Lagos helados:</gray> " + onOff(config.isIceEnabled())
                + (ice != null ? " <gray>(congelados: <white>" + ice.getFrozenCount() + "</white>, derretidos: <white>" + ice.getThawedCount() + "</white>)</gray>" : ""));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.logistics.admin")) {
            sendMsg(sender, "<red>No tienes permiso para recargar la configuración.</red>");
            return;
        }
        try {
            InputStream defaultStream = getClass().getClassLoader().getResourceAsStream("config.yml");
            config.load(dataFolder, defaultStream);
            weightModel.clearCache();
            sendMsg(sender, "<green>Configuración de Logistics recargada.</green>");
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
        }
        return suggestions;
    }

    private static String onOff(boolean value) {
        return value ? "<green>activo</green>" : "<gray>desactivado</gray>";
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(level);
        };
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private void sendMsg(CommandSender sender, String miniMessageText) {
        Component component = miniMessage.deserialize(config.getPrefix() + miniMessageText);
        sender.sendMessage(component);
    }
}
