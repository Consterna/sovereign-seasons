package com.sovereignseasons.core.calendar;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.event.DayChangeEvent;
import com.sovereignseasons.api.event.SeasonChangeEvent;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarConcurrencyTest {
    private SeasonsConfig config;
    private List<Event> firedEvents;
    private AtomicBoolean cancelSeasonEvent;
    private Season mutateSeasonTarget;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setDaysPerSeason(7);
        config.setTicksPerDay(24000L);

        firedEvents = Collections.synchronizedList(new ArrayList<>());
        cancelSeasonEvent = new AtomicBoolean(false);
        mutateSeasonTarget = null;

        installMockBukkit();
    }

    @AfterEach
    void tearDown() {
        resetBukkitServer();
    }

    private void installMockBukkit() {
        PluginManager mockPluginManager = (PluginManager) Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if ("callEvent".equals(method.getName())) {
                        Event event = (Event) args[0];
                        firedEvents.add(event);

                        if (event instanceof SeasonChangeEvent sce) {
                            if (cancelSeasonEvent.get()) {
                                sce.setCancelled(true);
                            }
                            if (mutateSeasonTarget != null) {
                                sce.setNewSeason(mutateSeasonTarget);
                            }
                        }
                        return null;
                    }
                    return null;
                }
        );

        Server mockServer = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPluginManager" -> mockPluginManager;
                    case "getLogger" -> java.util.logging.Logger.getGlobal();
                    default -> null;
                }
        );

        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, mockServer);
        } catch (Exception e) {
            throw new RuntimeException("Could not install mock Bukkit server", e);
        }
    }

    private void resetBukkitServer() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {}
    }

    private World createMockWorld(String name, UUID uid, AtomicLong fullTime) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUID" -> uid;
                    case "getEnvironment" -> World.Environment.NORMAL;
                    case "getFullTime" -> fullTime.get();
                    case "getTime" -> (fullTime.get() % 24000L + 24000L) % 24000L;
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Concurrent tickWorld calls fire DayChangeEvent exactly once per rollover without duplicates")
    void shouldFireDayChangeEventExactlyOnceConcurrently(@TempDir File tempDir) throws Exception {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        World world = createMockWorld("world", uid, fullTime);

        // Initial tick: establish baseline at Day 0
        manager.tickWorld(world);
        firedEvents.clear();

        // Advance to Day 1
        fullTime.set(24000L);

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    manager.tickWorld(world);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Must fire DayChangeEvent EXACTLY 1 time! No duplicates!
        long dayChangeCount = firedEvents.stream().filter(e -> e instanceof DayChangeEvent).count();
        assertThat(dayChangeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent tickWorld across season rollover fires SeasonChangeEvent exactly once")
    void shouldFireSeasonChangeEventExactlyOnceConcurrently(@TempDir File tempDir) throws Exception {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        // Day 6 (last day of Spring is Day 7, raw index 6)
        AtomicLong fullTime = new AtomicLong(6 * 24000L);
        World world = createMockWorld("world", uid, fullTime);

        manager.tickWorld(world);
        firedEvents.clear();

        // Rollover to Summer Day 1 (raw day index 7)
        fullTime.set(7 * 24000L);

        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    manager.tickWorld(world);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        long seasonChangeCount = firedEvents.stream().filter(e -> e instanceof SeasonChangeEvent).count();
        assertThat(seasonChangeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Cancelling SeasonChangeEvent in setSeason aborts change and preserves current season")
    void shouldAbortSetSeasonWhenCancelled(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        World world = createMockWorld("world", uid, fullTime);

        assertThat(manager.getSeason(world)).isEqualTo(Season.SPRING);

        // Arm listener to cancel SeasonChangeEvent
        cancelSeasonEvent.set(true);

        manager.setSeason(world, Season.WINTER);

        // Verification: season MUST remain SPRING because change was cancelled!
        assertThat(manager.getSeason(world)).isEqualTo(Season.SPRING);
    }

    @Test
    @DisplayName("Mutating SeasonChangeEvent in setSeason adopts the mutated season")
    void shouldAdoptMutatedSeasonInSetSeason(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(0L);
        World world = createMockWorld("world", uid, fullTime);

        // When setting Autumn, listener mutates to Winter
        mutateSeasonTarget = Season.WINTER;

        manager.setSeason(world, Season.AUTUMN);

        // Final season must be WINTER
        assertThat(manager.getSeason(world)).isEqualTo(Season.WINTER);
    }

    @Test
    @DisplayName("Cancelling SeasonChangeEvent during tickWorld rolls back world to final day of previous season")
    void shouldRollbackWhenNaturalSeasonChangeCancelled(@TempDir File tempDir) {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);
        UUID uid = UUID.randomUUID();
        AtomicLong fullTime = new AtomicLong(6 * 24000L); // Day 7 of Spring
        World world = createMockWorld("world", uid, fullTime);

        manager.tickWorld(world);
        assertThat(manager.getSeason(world)).isEqualTo(Season.SPRING);

        // Arm cancel on season transition
        cancelSeasonEvent.set(true);

        // Tick into Summer Day 1
        fullTime.set(7 * 24000L);
        manager.tickWorld(world);

        // Season must remain SPRING on day 7 (rollback applied)!
        CalendarDate date = manager.getDate(world);
        assertThat(date.season()).isEqualTo(Season.SPRING);
        assertThat(date.dayOfSeason()).isEqualTo(7);
    }

    @Test
    @DisplayName("Concurrent operations across multiple worlds do not deadlock or corrupt state")
    void shouldSupportHighConcurrencyAcrossMultipleWorlds(@TempDir File tempDir) throws Exception {
        WorldCalendarManager manager = new WorldCalendarManager(config, tempDir, null);

        int worldCount = 5;
        List<World> worlds = new ArrayList<>();
        for (int i = 0; i < worldCount; i++) {
            UUID uid = UUID.randomUUID();
            AtomicLong time = new AtomicLong(i * 100000L);
            worlds.add(createMockWorld("world_" + i, uid, time));
        }

        int threadCount = 20;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOps = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int op = 0; op < operationsPerThread; op++) {
                        World w = worlds.get(op % worldCount);
                        CalendarDate d = manager.getDate(w);
                        assertThat(d).isNotNull();

                        if (op % 5 == 0) {
                            manager.tickWorld(w);
                        }
                        if (op % 20 == 0) {
                            manager.setDayOfSeason(w, (op % 7) + 1);
                        }
                        successfulOps.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successfulOps.get()).isEqualTo(threadCount * operationsPerThread);
    }
}
