package com.sovereignseasons.core.greenhouse;

import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialCacheStressTest {
    private SeasonsConfig config;
    private UUID worldUid;
    private World mockWorld;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setCacheMaxSize(5000);
        config.setCacheTtlSeconds(30);
        config.setHeatRadius(4);
        config.setMaxCeilingHeight(16);

        worldUid = UUID.randomUUID();
        mockWorld = createMockWorld(worldUid, "stress_world");
    }

    private World createMockWorld(UUID uid, String name) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getName" -> name;
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        yield createMockAirBlock(mockWorld, x, y, z);
                    }
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private Block createMockAirBlock(World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        BlockData airData = (BlockData) Proxy.newProxyInstance(
                BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, args) -> null
        );

        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getType" -> Material.AIR;
                    case "getLocation" -> loc;
                    case "getBlockData" -> airData;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Cache size stays strictly bounded at cacheMaxSize during 50,000 insertions without memory leak")
    void shouldKeepMemoryBoundedUnderHeavyLoad() {
        GreenhouseDetector detector = new GreenhouseDetector(config);

        // 50,000 distinct coordinates
        for (int i = 0; i < 50_000; i++) {
            Location loc = new Location(mockWorld, i % 1000, 64, i / 1000);
            detector.isGreenhouse(loc);
        }

        // Must NOT exceed cacheMaxSize (5,000)!
        assertThat(detector.getCacheSize()).isLessThanOrEqualTo(config.getCacheMaxSize());
        assertThat(detector.getCacheMisses()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("Cache TTL evicts expired evaluations after configured seconds")
    void shouldEvictExpiredEntriesAfterTtl() throws InterruptedException {
        config.setCacheTtlSeconds(1); // 1 second TTL
        GreenhouseDetector detector = new GreenhouseDetector(config);
        Location loc = new Location(mockWorld, 10, 64, 10);

        // First evaluation: Miss
        detector.isGreenhouse(loc);
        assertThat(detector.getCacheHits()).isZero();
        assertThat(detector.getCacheMisses()).isEqualTo(1L);

        // Immediate subsequent evaluation: Hit
        detector.isGreenhouse(loc);
        assertThat(detector.getCacheHits()).isEqualTo(1L);

        // Wait for TTL to expire
        Thread.sleep(1100);

        // Evaluation after expiry: Miss again!
        detector.isGreenhouse(loc);
        assertThat(detector.getCacheMisses()).isEqualTo(2L);
    }

    @Test
    @DisplayName("invalidateRadius clears cubic zone without affecting distant cached positions")
    void shouldInvalidateCubicRadiusAccurately() {
        GreenhouseDetector detector = new GreenhouseDetector(config);

        // Seed 3 locations:
        // locA at (0, 64, 0)
        // locB at (3, 64, 3) (within radius 4)
        // locC at (20, 64, 20) (outside radius 4)
        Location locA = new Location(mockWorld, 0, 64, 0);
        Location locB = new Location(mockWorld, 3, 64, 3);
        Location locC = new Location(mockWorld, 20, 64, 20);

        detector.isGreenhouse(locA);
        detector.isGreenhouse(locB);
        detector.isGreenhouse(locC);

        assertThat(detector.getCacheSize()).isEqualTo(3L);

        // Invalidate radius 4 around (0, 64, 0)
        detector.invalidateRadius(locA, 4);

        // locA and locB should now be evicted (misses when queried)
        long initialMisses = detector.getCacheMisses();
        detector.isGreenhouse(locA);
        detector.isGreenhouse(locB);
        assertThat(detector.getCacheMisses()).isEqualTo(initialMisses + 2);

        // locC should still be in cache (hit!)
        long hitsBefore = detector.getCacheHits();
        detector.isGreenhouse(locC);
        assertThat(detector.getCacheHits()).isEqualTo(hitsBefore + 1);
    }

    @Test
    @DisplayName("Concurrent multithreaded queries and invalidations execute without race conditions or exceptions")
    void shouldHandleMultithreadedConcurrentAccess() throws Exception {
        GreenhouseDetector detector = new GreenhouseDetector(config);

        int threadCount = 16;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOps = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        Location loc = new Location(mockWorld, (threadId * 10) + (i % 20), 64, i % 20);
                        if (i % 10 == 0) {
                            detector.invalidate(loc);
                        } else if (i % 25 == 0) {
                            detector.invalidateRadius(loc, 3);
                        } else {
                            detector.isGreenhouse(loc);
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

    @Test
    @DisplayName("invalidateWorld purges all cached entries for an unloaded world preventing memory leaks")
    void shouldPurgeWorldEntriesOnUnload() {
        GreenhouseDetector detector = new GreenhouseDetector(config);

        UUID worldA_Uid = UUID.randomUUID();
        UUID worldB_Uid = UUID.randomUUID();
        World worldA = createMockWorld(worldA_Uid, "world_a");
        World worldB = createMockWorld(worldB_Uid, "world_b");

        // Seed 100 locations in worldA and 100 in worldB
        for (int i = 0; i < 100; i++) {
            detector.isGreenhouse(new Location(worldA, i, 64, i));
            detector.isGreenhouse(new Location(worldB, i, 64, i));
        }

        assertThat(detector.getCacheSize()).isEqualTo(200L);

        // Unload worldA
        detector.invalidateWorld(worldA_Uid);

        // Only worldB entries should remain
        assertThat(detector.getCacheSize()).isEqualTo(100L);

        // Querying worldA location is now a cache miss
        long missesBefore = detector.getCacheMisses();
        detector.isGreenhouse(new Location(worldA, 0, 64, 0));
        assertThat(detector.getCacheMisses()).isEqualTo(missesBefore + 1);

        // Querying worldB location is still a cache hit
        long hitsBefore = detector.getCacheHits();
        detector.isGreenhouse(new Location(worldB, 0, 64, 0));
        assertThat(detector.getCacheHits()).isEqualTo(hitsBefore + 1);
    }
}
