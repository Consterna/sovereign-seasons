package com.sovereignseasons.core.command;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonCommandAuditTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorldA;
    private World mockWorldB;
    private List<String> sentMessages;
    private Map<String, World> serverWorlds;

    @BeforeEach
    void setup(@TempDir File tempDir) {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        sentMessages = new ArrayList<>();
        serverWorlds = new HashMap<>();

        UUID uidA = UUID.randomUUID();
        UUID uidB = UUID.randomUUID();
        mockWorldA = createMockWorld("world_a", uidA);
        mockWorldB = createMockWorld("world_b", uidB);

        serverWorlds.put("world_a", mockWorldA);
        serverWorlds.put("world_b", mockWorldB);

        installMockBukkit();

        Map<UUID, Season> worldSeasons = new HashMap<>();
        worldSeasons.put(uidA, Season.SPRING);
        worldSeasons.put(uidB, Season.AUTUMN);

        Map<UUID, Integer> worldDays = new HashMap<>();
        worldDays.put(uidA, 1);
        worldDays.put(uidB, 4);

        mockSeasonService = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "getDate" -> {
                        World w = (World) args[0];
                        Season s = worldSeasons.getOrDefault(w.getUID(), Season.SPRING);
                        int day = worldDays.getOrDefault(w.getUID(), 1);
                        yield new CalendarDate(s, SubSeason.MID, day, 7, day - 1, 1, 6000L);
                    }
                    case "getSeason" -> {
                        World w = (World) args[0];
                        yield worldSeasons.getOrDefault(w.getUID(), Season.SPRING);
                    }
                    case "setSeason" -> {
                        World w = (World) args[0];
                        Season target = (Season) args[1];
                        worldSeasons.put(w.getUID(), target);
                        yield null;
                    }
                    case "setDayOfSeason" -> {
                        World w = (World) args[0];
                        int day = (int) args[1];
                        worldDays.put(w.getUID(), day);
                        yield null;
                    }
                    default -> null;
                }
        );

        mockGreenhouseService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isGreenhouse" -> true;
                    case "inspect" -> GreenhouseReport.failure((Location) args[0], false, false, null, null, 0, false, null, null, 0, "test");
                    case "getCacheHits" -> 100L;
                    case "getCacheMisses" -> 20L;
                    case "getCacheSize" -> 120L;
                    case "clearCache" -> null;
                    default -> null;
                }
        );
    }

    @AfterEach
    void tearDown() {
        resetBukkitServer();
    }

    private void installMockBukkit() {
        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> {
                        String name = (String) args[0];
                        yield serverWorlds.get(name);
                    }
                    case "getWorlds" -> new ArrayList<>(serverWorlds.values());
                    case "getLogger" -> java.util.logging.Logger.getGlobal();
                    default -> null;
                }
        );

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void resetBukkitServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
    }

    private World createMockWorld(String name, UUID uid) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> 0L;
                    case "getTime" -> 0L;
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private CommandSender createMockSender(Set<String> permissions) {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        String perm = (String) args[0];
                        yield permissions.contains(perm) || permissions.contains("*");
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

    private Player createMockPlayer(Set<String> permissions, World world) {
        Location loc = new Location(world, 0, 64, 0);
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> {
                        String perm = (String) args[0];
                        yield permissions.contains(perm) || permissions.contains("*");
                    }
                    case "getWorld" -> world;
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
    @DisplayName("Users without sovereignseasons.command.info are denied /season info")
    void shouldDenyInfoWithoutPermission(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        // Player has ZERO permissions
        Player player = createMockPlayer(Collections.emptySet(), mockWorldA);

        cmd.onCommand(player, createDummyCommand(), "season", new String[]{"info"});

        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));
    }

    @Test
    @DisplayName("Users without sovereignseasons.command.greenhouse are denied /season greenhouse")
    void shouldDenyGreenhouseWithoutPermission(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        Player player = createMockPlayer(Collections.emptySet(), mockWorldA);

        cmd.onCommand(player, createDummyCommand(), "season", new String[]{"greenhouse"});

        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));
    }

    @Test
    @DisplayName("Users without sovereignseasons.admin are denied admin subcommands")
    void shouldDenyAdminSubcommandsWithoutPermission(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender user = createMockSender(Set.of("sovereignseasons.command.info", "sovereignseasons.command.greenhouse"));

        // set
        cmd.onCommand(user, createDummyCommand(), "season", new String[]{"set", "winter"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));

        // setday
        sentMessages.clear();
        cmd.onCommand(user, createDummyCommand(), "season", new String[]{"setday", "3"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));

        // stats
        sentMessages.clear();
        cmd.onCommand(user, createDummyCommand(), "season", new String[]{"stats"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));

        // reload
        sentMessages.clear();
        cmd.onCommand(user, createDummyCommand(), "season", new String[]{"reload"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no tienes permiso"));
    }

    @Test
    @DisplayName("Admin can target specific worlds in /season set and /season setday")
    void shouldAllowTargetingSpecificWorlds(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockSender(Set.of("sovereignseasons.admin"));

        // Change season of world_b specifically to WINTER
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"set", "winter", "world_b"});
        assertThat(mockSeasonService.getSeason(mockWorldB)).isEqualTo(Season.WINTER);
        // world_a must NOT have been changed (still SPRING)
        assertThat(mockSeasonService.getSeason(mockWorldA)).isEqualTo(Season.SPRING);

        // Change day of world_a specifically to 6
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "6", "world_a"});
        assertThat(mockSeasonService.getDate(mockWorldA).dayOfSeason()).isEqualTo(6);
    }

    @Test
    @DisplayName("Commands targeting non-existent world display clean error message")
    void shouldHandleNonExistentWorldTarget(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockSender(Set.of("sovereignseasons.admin", "sovereignseasons.command.info"));

        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"info", "ghost_world"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no existe"));

        sentMessages.clear();
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"set", "winter", "ghost_world"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no existe"));
    }

    @Test
    @DisplayName("setday rejects out of bounds or non-numeric day arguments")
    void shouldRejectOutOfBoundsDays(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockSender(Set.of("sovereignseasons.admin"));

        // Day 0
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "0"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("debe estar entre 1 y"));

        // Day 99 (daysPerSeason is 7)
        sentMessages.clear();
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "99"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("debe estar entre 1 y"));

        // Not a number
        sentMessages.clear();
        cmd.onCommand(admin, createDummyCommand(), "season", new String[]{"setday", "abc"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("número entero"));
    }

    @Test
    @DisplayName("Tab completion suggests world names for info, set, and setday")
    void shouldTabCompleteWorldNames(@TempDir File tempDir) {
        SeasonCommand cmd = new SeasonCommand(config, mockSeasonService, mockGreenhouseService, tempDir);
        CommandSender admin = createMockSender(Set.of("sovereignseasons.admin", "sovereignseasons.command.info"));

        // /season info <tab>
        List<String> infoTab = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"info", "wor"});
        assertThat(infoTab).contains("world_a", "world_b");

        // /season set winter <tab>
        List<String> setTab = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"set", "winter", "wor"});
        assertThat(setTab).contains("world_a", "world_b");

        // /season setday 3 <tab>
        List<String> setDayTab = cmd.onTabComplete(admin, createDummyCommand(), "season", new String[]{"setday", "3", "wor"});
        assertThat(setDayTab).contains("world_a", "world_b");
    }
}
