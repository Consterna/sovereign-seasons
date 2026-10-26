package com.sovereignseasons.core.config;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import com.sovereignseasons.core.command.SeasonCommand;
import com.sovereignseasons.core.greenhouse.BlockPos;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ConfigAndCalendarEdgeCasesTest {
    private SeasonsConfig config;
    private World mockWorld;
    private UUID worldUid;
    private AtomicLong fullTime;
    private List<String> sentMessages;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        sentMessages = new ArrayList<>();
        worldUid = UUID.randomUUID();
        fullTime = new AtomicLong(0L);

        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "getUID" -> worldUid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> fullTime.get();
                    case "getTime" -> fullTime.get() % 24000L;
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );

        installMockBukkit();
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
                    case "getWorld" -> mockWorld;
                    case "getWorlds" -> List.of(mockWorld);
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

    private CommandSender createMockAdminSender() {
        return (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission" -> true;
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

    @Test
    @DisplayName("setTicksPerDay clamps values <= 0 to safe minimum 100L preventing ArithmeticException")
    void shouldClampZeroOrNegativeTicksPerDay() {
        config.setTicksPerDay(0L);
        assertThat(config.getTicksPerDay()).isEqualTo(100L);

        config.setTicksPerDay(-500L);
        assertThat(config.getTicksPerDay()).isEqualTo(100L);
    }

    @Test
    @DisplayName("WorldCalendarManager calculateDate and getDate operate smoothly with minimum clamped values")
    void shouldCalculateDateWithoutDivideByZero(@TempDir File tempDir) {
        config.setTicksPerDay(100L);
        config.setDaysPerSeason(1);

        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        CalendarDate date = manager.getDate(mockWorld);

        assertThat(date).isNotNull();
        assertThat(date.season()).isEqualTo(Season.SPRING);
        assertThat(date.dayOfSeason()).isEqualTo(1);
    }

    @Test
    @DisplayName("SeasonsConfig load handles null dataFolder safely without throwing NullPointerException")
    void shouldHandleNullDataFolderInLoad() {
        assertThatNoException().isThrownBy(() -> config.load(null, null));
    }

    @Test
    @DisplayName("GreenhouseDetector invalidateRadius handles negative radius and excessive radius safely")
    void shouldHandleBoundaryRadiiInInvalidateRadius() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location loc = new Location(mockWorld, 0, 64, 0);

        // Negative radius should do nothing safely
        assertThatNoException().isThrownBy(() -> detector.invalidateRadius(loc, -5));

        // Excessive radius should clamp to 32 without stalling
        assertThatNoException().isThrownBy(() -> detector.invalidateRadius(loc, 1000));
    }

    @Test
    @DisplayName("GreenhouseDetector invalidateRadius handles world with null UID safely")
    void shouldHandleNullWorldUidInInvalidateRadius() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        World worldNoUid = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> null;
                    default -> null;
                }
        );

        Location loc = new Location(worldNoUid, 0, 64, 0);
        assertThatNoException().isThrownBy(() -> detector.invalidateRadius(loc, 4));
    }

    @Test
    @DisplayName("BlockPos.from handles world with null UID gracefully with non-null fallback UID")
    void shouldHandleNullWorldUidInBlockPos() {
        World worldNoUid = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> null;
                    default -> null;
                }
        );

        Location loc = new Location(worldNoUid, 15, 64, 15);
        BlockPos pos = BlockPos.from(loc);

        assertThat(pos).isNotNull();
        assertThat(pos.worldUid()).isNotNull();
        assertThat(pos.x()).isEqualTo(15);
    }

    @Test
    @DisplayName("SeasonCommand /season info displays error message when getDate returns null without throwing NPE")
    void shouldHandleNullDateInSeasonInfo(@TempDir File tempDir) {
        SeasonService mockService = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "getDate" -> null; // Simulate service failure
                    default -> null;
                }
        );

        GreenhouseService mockGreenhouse = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> null
        );

        SeasonCommand cmd = new SeasonCommand(config, mockService, mockGreenhouse, tempDir);
        CommandSender admin = createMockAdminSender();

        Command dummyCmd = new Command("season") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return false;
            }
        };

        cmd.onCommand(admin, dummyCmd, "season", new String[]{"info", "world"});
        assertThat(sentMessages).anyMatch(msg -> msg.toLowerCase().contains("no se pudo calcular la fecha"));
    }
}
