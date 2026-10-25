package com.sovereignseasons.core.command;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
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
import java.util.*;

/**
 * Command handler and tab completer for /season and /seasons.
 */
public class SeasonCommand implements CommandExecutor, TabCompleter {
    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final GreenhouseService greenhouseService;
    private final File dataFolder;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SeasonCommand(
            SeasonsConfig config,
            SeasonService seasonService,
            GreenhouseService greenhouseService,
            File dataFolder
    ) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.greenhouseService = Objects.requireNonNull(greenhouseService, "greenhouseService cannot be null");
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            handleInfo(sender, args);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "greenhouse", "invernadero" -> handleGreenhouse(sender);
            case "set" -> handleSet(sender, args);
            case "setday" -> handleSetDay(sender, args);
            case "stats" -> handleStats(sender);
            case "reload" -> handleReload(sender);
            default -> {
                sendMsg(sender, "<red>Subcomando desconocido. Usa: <yellow>/" + label + " [info|greenhouse|set|setday|stats|reload]</yellow></red>");
            }
        }
        return true;
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sovereignseasons.command.info")) {
            sendMsg(sender, "<red>No tienes permiso para consultar el calendario de estaciones.</red>");
            return;
        }

        World world;
        if (args.length >= 2 && !args[1].isBlank()) {
            world = org.bukkit.Bukkit.getWorld(args[1]);
            if (world == null) {
                sendMsg(sender, "<red>El mundo <white>" + args[1] + "</white> no existe o no está cargado.</red>");
                return;
            }
        } else {
            world = resolveWorld(sender);
        }

        if (world == null) {
            sendMsg(sender, "<red>No se pudo determinar el mundo actual.</red>");
            return;
        }

        if (!seasonService.isEnabledInWorld(world)) {
            sendMsg(sender, "<yellow>Las estaciones no están activadas en el mundo <white>" + world.getName() + "</white>.</yellow>");
            return;
        }

        CalendarDate date = seasonService.getDate(world);
        Season season = date.season();

        String progressBar = buildProgressBar(date.getSeasonProgressPercent(), 16);

        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Calendario de Estaciones</b></gold> <gray>(" + world.getName() + ")</gray><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Estación actual:</gray> " + season.getMiniMessageTag() + " <gray>(" + date.subSeason().getSpanishName() + ")</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Día de la estación:</gray> <white>" + date.dayOfSeason() + "</white><gray>/</gray><white>" + date.totalDaysPerSeason() + "</white> <gray>(" + date.getRemainingDaysInSeason() + " días restantes)</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Progreso:</gray> " + progressBar + " <gray>(" + String.format(Locale.ROOT, "%.1f", date.getSeasonProgressPercent()) + "%)</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Año:</gray> <yellow>" + date.year() + "</yellow> <dark_gray>|</dark_gray> <gray>Días totales:</gray> <white>" + date.totalDaysElapsed() + "</white>");

        if (sender instanceof Player player) {
            boolean inGreenhouse = greenhouseService.isGreenhouse(player.getLocation());
            String ghStatus = inGreenhouse
                    ? "<green>✔ En Invernadero (Cultivos Protegidos)</green>"
                    : (season.isWinter() ? "<aqua>❄ Expuesto al Frío Invernal (Pausado)</aqua>" : "<gray>Expuesto al aire libre</gray>");
            sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Tu ubicación:</gray> " + ghStatus);
        }
        sendMsg(sender, "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>");
    }

    private void handleGreenhouse(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.command.greenhouse")) {
            sendMsg(sender, "<red>No tienes permiso para inspeccionar el estado de invernadero.</red>");
            return;
        }

        if (!(sender instanceof Player player)) {
            sendMsg(sender, "<red>Este comando solo puede ser ejecutado por un jugador en el juego.</red>");
            return;
        }

        Location playerLoc = player.getLocation();
        Location loc = new Location(player.getWorld(), playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ());
        GreenhouseReport report = greenhouseService.inspect(loc);

        sendMsg(sender, "<gray>━━━━━ </gray><green><b>Diagnóstico de Invernadero</b></green><gray> ━━━━━</gray>");
        sendMsg(sender, "<gray>Coordenadas:</gray> <white>X:" + loc.getBlockX() + " Y:" + loc.getBlockY() + " Z:" + loc.getBlockZ() + "</white>");
        sendMsg(sender, "<gray>Estado General:</gray> " + (report.isGreenhouse()
                ? "<green><b>FUNCIONAL</b></green> <gray>(Cultivos protegidos en invierno)</gray>"
                : "<red><b>NO FUNCIONAL</b></red>"));

        // Glass ceiling status
        if (report.glassCeilingValid()) {
            sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Techo de cristal:</gray> <green>✔ Válido</green> <gray>(" + report.glassMaterial().name() + " a " + report.distanceToCeiling() + " bloques de altura)</gray>");
        } else {
            sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Techo de cristal:</gray> <red>✘ Faltante o Bloqueado</red>");
        }

        // Heat source status
        if (report.heatSourceValid()) {
            sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Fuente de calor:</gray> <green>✔ Válido</green> <gray>(" + report.heatSourceMaterial().name() + " a " + String.format(Locale.ROOT, "%.1f", report.distanceToHeatSource()) + " bloques)</gray>");
        } else {
            sendMsg(sender, "<dark_gray>  ├</dark_gray> <gray>Fuente de calor:</gray> <red>✘ No detectada en radio de " + config.getHeatRadius() + " bloques</red>");
        }

        // Cache state
        sendMsg(sender, "<dark_gray>  └</dark_gray> <gray>Evaluación:</gray> <dark_gray>" + (report.cached() ? "Desde caché LRU" : "Escaneo en vivo") + "</dark_gray>");

        if (!report.isGreenhouse() && report.failureReason() != null) {
            sendMsg(sender, "<yellow>Motivo:</yellow> <gray>" + report.failureReason() + "</gray>");
        }
        sendMsg(sender, "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>");
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sovereignseasons.admin")) {
            sendMsg(sender, "<red>No tienes permiso para modificar las estaciones.</red>");
            return;
        }

        if (args.length < 2) {
            sendMsg(sender, "<red>Uso: <yellow>/season set <spring|summer|autumn|winter> [mundo]</yellow></red>");
            return;
        }

        Season target = Season.fromString(args[1]);
        if (target == null) {
            sendMsg(sender, "<red>Estación inválida. Usa: <yellow>spring, summer, autumn, winter</yellow></red>");
            return;
        }

        World world;
        if (args.length >= 3 && !args[2].isBlank()) {
            world = org.bukkit.Bukkit.getWorld(args[2]);
            if (world == null) {
                sendMsg(sender, "<red>El mundo <white>" + args[2] + "</white> no existe o no está cargado.</red>");
                return;
            }
        } else {
            world = resolveWorld(sender);
        }

        if (world == null) {
            sendMsg(sender, "<red>No se encontró un mundo válido.</red>");
            return;
        }

        if (!seasonService.isEnabledInWorld(world)) {
            sendMsg(sender, "<yellow>Las estaciones no están activadas en el mundo <white>" + world.getName() + "</white>.</yellow>");
            return;
        }

        seasonService.setSeason(world, target);
        sendMsg(sender, "<green>Estación del mundo <white>" + world.getName() + "</white> cambiada a: " + target.getMiniMessageTag() + "</green>");
    }

    private void handleSetDay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sovereignseasons.admin")) {
            sendMsg(sender, "<red>No tienes permiso para modificar los días de estación.</red>");
            return;
        }

        if (args.length < 2) {
            sendMsg(sender, "<red>Uso: <yellow>/season setday <número> [mundo]</yellow></red>");
            return;
        }

        int day;
        try {
            day = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sendMsg(sender, "<red>Día inválido. Debe ser un número entero entre 1 y " + config.getDaysPerSeason() + ".</red>");
            return;
        }

        if (day < 1 || day > config.getDaysPerSeason()) {
            sendMsg(sender, "<red>El día debe estar entre 1 y " + config.getDaysPerSeason() + ".</red>");
            return;
        }

        World world;
        if (args.length >= 3 && !args[2].isBlank()) {
            world = org.bukkit.Bukkit.getWorld(args[2]);
            if (world == null) {
                sendMsg(sender, "<red>El mundo <white>" + args[2] + "</white> no existe o no está cargado.</red>");
                return;
            }
        } else {
            world = resolveWorld(sender);
        }

        if (world == null) {
            sendMsg(sender, "<red>No se encontró un mundo válido.</red>");
            return;
        }

        if (!seasonService.isEnabledInWorld(world)) {
            sendMsg(sender, "<yellow>Las estaciones no están activadas en el mundo <white>" + world.getName() + "</white>.</yellow>");
            return;
        }

        seasonService.setDayOfSeason(world, day);
        sendMsg(sender, "<green>Día de la estación ajustado a <white>" + day + "</white> en el mundo <white>" + world.getName() + "</white>.</green>");
    }

    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.admin")) {
            sendMsg(sender, "<red>No tienes permiso para ver estadísticas técnicas.</red>");
            return;
        }

        long hits = greenhouseService.getCacheHits();
        long misses = greenhouseService.getCacheMisses();
        long total = hits + misses;
        double hitRatio = total > 0 ? ((double) hits / (double) total) * 100.0 : 0.0;

        sendMsg(sender, "<gray>━━━━━ </gray><gold><b>Estadísticas de Rendimiento</b></gold><gray> ━━━━━</gray>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Entradas activas en caché LRU:</gray> <white>" + greenhouseService.getCacheSize() + "</white><gray>/</gray><white>" + config.getCacheMaxSize() + "</white>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Aciertos en caché (Hits):</gray> <green>" + hits + "</green>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Fallos en caché (Misses):</gray> <yellow>" + misses + "</yellow>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>Tasa de acierto (Hit Ratio):</gray> <aqua>" + String.format(Locale.ROOT, "%.1f", hitRatio) + "%</aqua>");
        sendMsg(sender, "<dark_gray>•</dark_gray> <gray>TTL de caché:</gray> <white>" + config.getCacheTtlSeconds() + "s</white>");
        sendMsg(sender, "<gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gray>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("sovereignseasons.admin")) {
            sendMsg(sender, "<red>No tienes permiso para recargar la configuración.</red>");
            return;
        }

        InputStream defaultStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        config.load(dataFolder, defaultStream);
        greenhouseService.clearCache();
        if (seasonService instanceof com.sovereignseasons.core.calendar.WorldCalendarManager wcm) {
            wcm.reload();
        }

        sendMsg(sender, "<green>Configuración recargada y caché de invernadero limpiada exitosamente.</green>");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("sovereignseasons.command.info")) {
                subcommands.add("info");
            }
            if (sender.hasPermission("sovereignseasons.command.greenhouse")) {
                subcommands.add("greenhouse");
            }
            if (sender.hasPermission("sovereignseasons.admin")) {
                subcommands.addAll(List.of("set", "setday", "stats", "reload"));
            }
            return filterPrefix(subcommands, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            if (sender.hasPermission("sovereignseasons.command.info")) {
                List<String> worlds = org.bukkit.Bukkit.getWorlds().stream().map(World::getName).toList();
                return filterPrefix(worlds, args[1]);
            }
            return Collections.emptyList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            if (sender.hasPermission("sovereignseasons.admin")) {
                return filterPrefix(List.of("spring", "summer", "autumn", "winter", "primavera", "verano", "otoño", "invierno"), args[1]);
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("setday")) {
            if (sender.hasPermission("sovereignseasons.admin")) {
                List<String> days = new ArrayList<>();
                for (int i = 1; i <= config.getDaysPerSeason(); i++) {
                    days.add(String.valueOf(i));
                }
                return filterPrefix(days, args[1]);
            }
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("setday"))) {
            if (sender.hasPermission("sovereignseasons.admin")) {
                List<String> worlds = org.bukkit.Bukkit.getWorlds().stream().map(World::getName).toList();
                return filterPrefix(worlds, args[2]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(opt);
            }
        }
        return matches;
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        return org.bukkit.Bukkit.getWorlds().isEmpty() ? null : org.bukkit.Bukkit.getWorlds().get(0);
    }

    private void sendMsg(CommandSender sender, String miniMessageText) {
        Component component = miniMessage.deserialize(config.getPrefix() + miniMessageText);
        sender.sendMessage(component);
    }

    private String buildProgressBar(double percent, int length) {
        int filled = (int) Math.round((percent / 100.0) * length);
        filled = Math.max(0, Math.min(length, filled));
        int unfilled = length - filled;

        return "<green>" + "■".repeat(filled) + "</green><dark_gray>" + "■".repeat(unfilled) + "</dark_gray>";
    }
}
