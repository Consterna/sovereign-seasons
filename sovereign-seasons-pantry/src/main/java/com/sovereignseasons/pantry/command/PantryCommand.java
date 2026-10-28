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
import com.sovereignseasons.pantry.cellar.CellarReport;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.DecayCalculator;
import com.sovereignseasons.pantry.food.FoodDecayService;
import com.sovereignseasons.pantry.food.FoodState;
import com.sovereignseasons.pantry.food.FreshnessStage;
import com.sovereignseasons.pantry.food.StorageContext;
import com.sovereignseasons.pantry.storage.StorageTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * /pantry command: diagnostics for food freshness and cellars, plus admin tooling.
 */
public class PantryCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("inspect", "cellar", "stats", "reload", "setfresh");
    private static final int TARGET_RANGE = 5;

    private final PantryConfig config;
    private final FoodDecayService decayService;
    private final StorageTracker tracker;
    private final CellarDetector cellarDetector;
    private final File dataFolder;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PantryCommand(PantryConfig config, FoodDecayService decayService, StorageTracker tracker, CellarDetector cellarDetector, File dataFolder) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.decayService = Objects.requireNonNull(decayService, "decayService cannot be null");
        this.tracker = tracker;
        this.cellarDetector = Objects.requireNonNull(cellarDetector, "cellarDetector cannot be null");
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        String sub = args.length == 0 || args[0].isBlank() ? "inspect" : args[0].trim().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "inspect" -> handleInspect(sender);
            case "cellar" -> handleCellar(sender);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            case "setfresh" -> handleSetFresh(sender, args);
            default -> sendMsg(sender, "<red>Subcomando desconocido. Uso: <white>/pantry [inspect|cellar|stats|reload|setfresh]</white></red>");
        }
        return true;
    }

    private void handleInspect(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pantry.inspect")) {
            sendMsg(sender, "<red>No tienes permiso para inspeccionar alimentos.</red>");
            return;
        }
        if (!(sender instanceof Player player) || tracker == null) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        DecayCalculator calculator = decayService.getCalculator();
        if (item == null || item.getType().isAir() || !calculator.isPerishable(item.getType())) {
            sendMsg(sender, "<yellow>El objeto en tu mano no es perecedero.</yellow>");
            return;
        }

        long now = tracker.now();
        Optional<FoodState> stored = decayService.getCodec().read(item);
        StorageContext context = tracker.resolveContext(player.getLocation());
        double shelfDays = calculator.shelfLifeTicks(item.getType()) / (double) config.getTicksPerDay();

        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Despensa</b></gold> <gray>(" + item.getType().name() + ")</gray><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Vida útil base:</gray> <white>" + format(shelfDays) + " días</white>");
        if (stored.isEmpty()) {
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Estado:</gray> <green>Recién obtenido (aún sin registrar)</green>");
        } else {
            FoodState state = stored.get();
            double freshness = calculator.freshness(state, item.getType(), now);
            FreshnessStage stage = calculator.stage(state, item.getType(), now);
            double rate = calculator.rate(context, state.preservation());
            double remainingDays = (state.expiresAt() - now) / (double) config.getTicksPerDay();
            String eta = rate <= 0.0 ? "nunca (conservación total)" : format(Math.max(0.0, remainingDays / rate)) + " días";
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Frescura:</gray> " + stage.getColorTag() + format(freshness * 100.0) + "% (" + stage.getDisplayName() + ")");
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Conservación:</gray> <white>" + (state.preservation().getDisplayName().isEmpty() ? "Ninguna" : state.preservation().getDisplayName()) + "</white>");
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Ritmo aquí:</gray> <white>x" + format(rate) + "</white> <dark_gray>|</dark_gray> <gray>Se pudre en:</gray> <white>" + eta + "</white>");
        }
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Entorno:</gray> " + context.band().getColorTag() + context.band().getDisplayName()
                + (context.cellar() ? " <aqua>+ Bodega</aqua>" : "") + (context.iceNearby() ? " <aqua>+ Hielo</aqua>" : ""));
    }

    private void handleCellar(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pantry.cellar")) {
            sendMsg(sender, "<red>No tienes permiso para inspeccionar bodegas.</red>");
            return;
        }
        if (!(sender instanceof Player player)) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        Block target = player.getTargetBlockExact(TARGET_RANGE);
        Location location = target != null ? target.getLocation() : player.getLocation().getBlock().getLocation();
        CellarReport report = cellarDetector.inspect(location);

        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Diagnóstico de Bodega</b></gold><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Bloque:</gray> <white>" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "</white>"
                + (report.fromCache() ? " <dark_gray>(caché)</dark_gray>" : ""));
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Resultado:</gray> " + (report.cellar() ? "<green>✔ Bodega fría funcional</green>" : "<red>✘ No es una bodega</red>"));
        sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Luz del cielo:</gray> <white>" + report.maxSkyLight() + "</white>");
        sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Bloques de techo:</gray> <white>" + report.roofBlocks() + "</white><gray>/</gray><white>" + config.getMinRoofBlocks() + "</white>"
                + (report.deep() ? " <aqua>(profundidad suficiente)</aqua>" : ""));
        sendMsg(sender, "<dark_gray>  └</dark_gray> <gray>Hielo cercano:</gray> " + (report.iceNearby() ? "<aqua>✔ " + report.iceMaterial().name() + "</aqua>" : "<gray>No</gray>"));
        if (report.failureReason() != null) {
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Motivo:</gray> <yellow>" + report.failureReason() + "</yellow>");
        }
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pantry.admin")) {
            sendMsg(sender, "<red>No tienes permiso para ver las estadísticas.</red>");
            return;
        }
        long hits = cellarDetector.getCacheHits();
        long misses = cellarDetector.getCacheMisses();
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (hits * 100.0) / total;
        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Estadísticas de Despensa</b></gold><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Alimentos perecederos configurados:</gray> <white>" + config.getShelfLifeTable().size() + "</white>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Caché de bodegas:</gray> <white>" + cellarDetector.getCacheSize() + "</white><gray>/</gray><white>" + config.getCellarCacheMaxSize() + "</white>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Aciertos / fallos:</gray> <green>" + hits + "</green><gray> / </gray><red>" + misses + "</red> <gray>(" + format(hitRate) + "%)</gray>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.pantry.admin")) {
            sendMsg(sender, "<red>No tienes permiso para recargar la configuración.</red>");
            return;
        }
        try {
            InputStream defaultStream = getClass().getClassLoader().getResourceAsStream("config.yml");
            config.load(dataFolder, defaultStream);
            cellarDetector.rebuildCache();
            sendMsg(sender, "<green>Configuración de Pantry recargada y caché de bodegas vaciada.</green>");
        } catch (Exception e) {
            sendMsg(sender, "<red>Error al recargar la configuración: <white>" + e.getMessage() + "</white></red>");
        }
    }

    private void handleSetFresh(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sovereignseasons.pantry.admin")) {
            sendMsg(sender, "<red>No tienes permiso para modificar la frescura.</red>");
            return;
        }
        if (!(sender instanceof Player player) || tracker == null) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }
        if (args.length < 2 || args[1].isBlank()) {
            sendMsg(sender, "<red>Uso: <white>/pantry setfresh <0-100></white></red>");
            return;
        }
        int percent;
        try {
            percent = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            sendMsg(sender, "<red>Porcentaje inválido. Debe ser un número entero entre 0 y 100.</red>");
            return;
        }
        if (percent < 0 || percent > 100) {
            sendMsg(sender, "<red>El porcentaje debe estar entre 0 y 100.</red>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!decayService.setFreshness(item, percent / 100.0, tracker.now())) {
            sendMsg(sender, "<yellow>El objeto en tu mano no es perecedero.</yellow>");
            return;
        }
        player.getInventory().setItemInMainHand(item);
        sendMsg(sender, "<green>Frescura establecida en <white>" + percent + "%</white>.</green>");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(prefix)) {
                    suggestions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].trim().equalsIgnoreCase("setfresh")) {
            for (String value : List.of("0", "25", "50", "75", "100")) {
                if (value.startsWith(args[1].trim())) {
                    suggestions.add(value);
                }
            }
        }
        return suggestions;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void sendMsg(CommandSender sender, String miniMessageText) {
        Component component = miniMessage.deserialize(config.getPrefix() + miniMessageText);
        sender.sendMessage(component);
    }
}
