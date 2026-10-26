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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkLoadingAndVerticalBoundsTest {
    private SeasonsConfig config;
    private UUID worldUid;
    private Map<String, Material> worldBlocks;
    private Set<String> loadedChunks;
    private List<String> accessedBlocks;
    private World mockWorld;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setHeatRadius(4);
        config.setMaxCeilingHeight(16);
        config.setCacheTtlSeconds(30);

        worldUid = UUID.randomUUID();
        worldBlocks = new HashMap<>();
        loadedChunks = new HashSet<>();
        accessedBlocks = new ArrayList<>();

        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldUid;
                    case "getName" -> "mock_world_bounds";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "isChunkLoaded" -> {
                        int cx = (int) args[0];
                        int cz = (int) args[1];
                        yield loadedChunks.contains(chunkKey(cx, cz));
                    }
                    case "getBlockAt" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        if (y < -64 || y >= 320) {
                            throw new IllegalArgumentException("Y (" + y + ") is outside of world [-64, 320)");
                        }
                        int cx = x >> 4;
                        int cz = z >> 4;
                        if (!loadedChunks.contains(chunkKey(cx, cz))) {
                            throw new IllegalStateException("Simulated crash: getBlockAt called on unloaded chunk (" + cx + "," + cz + ")");
                        }
                        accessedBlocks.add(x + "," + y + "," + z);
                        yield createMockBlock(mockWorld, x, y, z);
                    }
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
    }

    private String chunkKey(int cx, int cz) {
        return cx + "," + cz;
    }

    private String posKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private void loadChunk(int cx, int cz) {
        loadedChunks.add(chunkKey(cx, cz));
    }

    private void setBlock(int x, int y, int z, Material mat) {
        worldBlocks.put(posKey(x, y, z), mat);
    }

    private Block createMockBlock(World world, int x, int y, int z) {
        Material mat = worldBlocks.getOrDefault(posKey(x, y, z), Material.AIR);
        Location loc = new Location(world, x, y, z);

        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "getType" -> mat;
                    case "getLocation" -> loc;
                    case "getBlockData" -> null;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Queries at Y < minHeight (-65, -100) return failure without throwing IllegalArgumentException")
    void shouldHandleNegativeYOutOfBoundsGracefully() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        loadChunk(0, 0);

        Location locBelow = new Location(mockWorld, 0, -65, 0);
        assertThat(detector.isGreenhouse(locBelow)).isFalse();

        GreenhouseReport report = detector.inspect(locBelow);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.failureReason()).contains("límites verticales");

        Location locVoid = new Location(mockWorld, 0, -100, 0);
        assertThat(detector.isGreenhouse(locVoid)).isFalse();
    }

    @Test
    @DisplayName("Queries at Y >= maxHeight (320, 500) return failure without throwing IllegalArgumentException")
    void shouldHandleExcessiveYOutOfBoundsGracefully() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        loadChunk(0, 0);

        Location locAbove = new Location(mockWorld, 0, 320, 0);
        assertThat(detector.isGreenhouse(locAbove)).isFalse();

        GreenhouseReport report = detector.inspect(locAbove);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.failureReason()).contains("límites verticales");

        Location locSky = new Location(mockWorld, 0, 500, 0);
        assertThat(detector.isGreenhouse(locSky)).isFalse();
    }

    @Test
    @DisplayName("Queries in an unloaded chunk return failure immediately without calling getBlockAt")
    void shouldBypassUnloadedChunkImmediately() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        // Chunk (5, 5) is NOT loaded
        Location loc = new Location(mockWorld, 5 * 16 + 8, 64, 5 * 16 + 8);

        assertThat(detector.isGreenhouse(loc)).isFalse();

        GreenhouseReport report = detector.inspect(loc);
        assertThat(report.isGreenhouse()).isFalse();
        assertThat(report.failureReason()).contains("chunk no está cargado");
        assertThat(accessedBlocks).isEmpty(); // Zero synchronous chunk reads
    }

    @Test
    @DisplayName("Heat source scan near chunk boundary skips unloaded adjacent chunks without crashing")
    void shouldSkipUnloadedAdjacentChunksDuringHeatScan() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        // Load chunk (0, 0) where crop is located (X=15 is near edge)
        loadChunk(0, 0);
        // Chunk (1, 0) is UNLOADED!

        int cropX = 15;
        int cropY = 64;
        int cropZ = 8;
        Location cropLoc = new Location(mockWorld, cropX, cropY, cropZ);

        // Put glass ceiling in loaded chunk
        setBlock(cropX, 68, cropZ, Material.GLASS);

        // Put campfire on the loaded side at (13, 64, 8)
        setBlock(13, 64, 8, Material.TORCH);

        // Query: should detect greenhouse successfully without accessing the unloaded chunk (1, 0)
        assertThat(detector.isGreenhouse(cropLoc)).isTrue();

        GreenhouseReport report = detector.inspect(cropLoc);
        assertThat(report.isGreenhouse()).isTrue();
        assertThat(report.heatSourceValid()).isTrue();
    }

    @Test
    @DisplayName("Evaluates greenhouse properly at negative bedrock coordinates (-500, -64, -700)")
    void shouldFunctionProperlyAtNegativeBedrockCoordinates() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        int bx = -500;
        int by = -64;
        int bz = -700;

        loadChunk(bx >> 4, bz >> 4);

        Location bedrockCrop = new Location(mockWorld, bx, by, bz);

        // Place glass at Y = -54 (10 blocks above bedrock)
        setBlock(bx, -54, bz, Material.GLASS);
        // Place lava at Y = -64 nearby
        setBlock(bx + 2, by, bz + 1, Material.LAVA);

        assertThat(detector.isGreenhouse(bedrockCrop)).isTrue();

        GreenhouseReport report = detector.inspect(bedrockCrop);
        assertThat(report.isGreenhouse()).isTrue();
        assertThat(report.distanceToCeiling()).isEqualTo(10);
        assertThat(report.glassMaterial()).isEqualTo(Material.GLASS);
        assertThat(report.heatSourceMaterial()).isEqualTo(Material.LAVA);
    }

    @Test
    @DisplayName("Evaluates greenhouse properly near top of world (Y=315 with glass at Y=319)")
    void shouldFunctionProperlyNearWorldMaxHeight() {
        GreenhouseDetector detector = new GreenhouseDetector(config);
        int bx = 100;
        int by = 315;
        int bz = 100;

        loadChunk(bx >> 4, bz >> 4);

        Location highCrop = new Location(mockWorld, bx, by, bz);

        // Glass at highest valid block Y = 319 (world maxHeight is 320)
        setBlock(bx, 319, bz, Material.TINTED_GLASS);
        setBlock(bx + 1, by, bz, Material.TORCH);

        assertThat(detector.isGreenhouse(highCrop)).isTrue();

        GreenhouseReport report = detector.inspect(highCrop);
        assertThat(report.isGreenhouse()).isTrue();
        assertThat(report.distanceToCeiling()).isEqualTo(4);
        assertThat(report.glassMaterial()).isEqualTo(Material.TINTED_GLASS);
    }
}
