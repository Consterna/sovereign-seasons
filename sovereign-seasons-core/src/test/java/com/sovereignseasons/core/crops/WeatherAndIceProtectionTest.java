package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherAndIceProtectionTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorld;
    private AtomicBoolean isInsideGreenhouse;
    private List<Location> invalidatedLocations;
    private List<Location> invalidatedRadiiLocations;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setWinterGrowthPaused(true);
        config.setPreventFreezeAndSnow(true);

        isInsideGreenhouse = new AtomicBoolean(false);
        invalidatedLocations = new ArrayList<>();
        invalidatedRadiiLocations = new ArrayList<>();

        UUID worldUid = UUID.randomUUID();
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldUid;
                    case "getName" -> "world";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "hashCode" -> worldUid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );

        mockSeasonService = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "isWinter" -> true;
                    default -> null;
                }
        );

        mockGreenhouseService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isGreenhouse" -> {
                        Location loc = (Location) args[0];
                        // If specific coordinate test, or default to flag
                        yield isInsideGreenhouse.get();
                    }
                    case "invalidate" -> {
                        invalidatedLocations.add((Location) args[0]);
                        yield null;
                    }
                    case "invalidateRadius" -> {
                        invalidatedRadiiLocations.add((Location) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private Block createMockBlock(Material material, Location loc) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> mockWorld;
                    case "getType" -> material;
                    case "getLocation" -> loc;
                    default -> null;
                }
        );
    }

    private BlockState createMockBlockState(Block block, Material newType) {
        return (BlockState) Proxy.newProxyInstance(
                BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlock" -> block;
                    case "getType" -> newType;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("Water freezing into ICE is cancelled inside a greenhouse")
    void shouldCancelWaterFreezingInsideGreenhouse() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isInsideGreenhouse.set(true); // Inside greenhouse

        Location waterLoc = new Location(mockWorld, 10, 64, 10);
        Block waterBlock = createMockBlock(Material.WATER, waterLoc);
        BlockState iceState = createMockBlockState(waterBlock, Material.ICE);

        BlockFormEvent event = new BlockFormEvent(waterBlock, iceState);
        listener.onWaterFreeze(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Water freezing into FROSTED_ICE is cancelled inside a greenhouse")
    void shouldCancelFrostedIceInsideGreenhouse() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isInsideGreenhouse.set(true);

        Location waterLoc = new Location(mockWorld, 10, 64, 10);
        Block waterBlock = createMockBlock(Material.WATER, waterLoc);
        BlockState frostedIceState = createMockBlockState(waterBlock, Material.FROSTED_ICE);

        BlockFormEvent event = new BlockFormEvent(waterBlock, frostedIceState);
        listener.onWaterFreeze(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Snow layers forming over greenhouse crops are cancelled")
    void shouldCancelSnowInsideGreenhouse() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isInsideGreenhouse.set(true);

        Location airLoc = new Location(mockWorld, 10, 64, 10);
        Block airBlock = createMockBlock(Material.AIR, airLoc);
        BlockState snowState = createMockBlockState(airBlock, Material.SNOW);

        BlockFormEvent event = new BlockFormEvent(airBlock, snowState);
        listener.onWaterFreeze(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("Water freezing outside greenhouse is permitted")
    void shouldAllowWaterFreezingOutsideGreenhouse() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isInsideGreenhouse.set(false); // Outside greenhouse

        Location waterLoc = new Location(mockWorld, 10, 64, 10);
        Block waterBlock = createMockBlock(Material.WATER, waterLoc);
        BlockState iceState = createMockBlockState(waterBlock, Material.ICE);

        BlockFormEvent event = new BlockFormEvent(waterBlock, iceState);
        listener.onWaterFreeze(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Melon fruit attempting to spread outdoors from a greenhouse stem is cancelled")
    void shouldBlockFruitSpreadingOutdoorsFromGreenhouseEdge() {
        // Custom greenhouse service: stem at X=10 is inside, but fruit target at X=11 is outside!
        GreenhouseService perimeterService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> {
                    if ("isGreenhouse".equals(method.getName())) {
                        Location loc = (Location) args[0];
                        return loc.getBlockX() == 10; // Only X=10 is in greenhouse
                    }
                    return null;
                }
        );

        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, perimeterService);

        Location stemLoc = new Location(mockWorld, 10, 64, 10); // Inside
        Location fruitLoc = new Location(mockWorld, 11, 64, 10); // Outside!

        Block stemBlock = createMockBlock(Material.MELON_STEM, stemLoc);
        Block targetBlock = createMockBlock(Material.DIRT, fruitLoc);
        BlockState fruitState = createMockBlockState(targetBlock, Material.MELON);

        BlockSpreadEvent event = new BlockSpreadEvent(targetBlock, stemBlock, fruitState);
        listener.onFruitSpread(event);

        // Must be cancelled because the fruit would form outside in the cold!
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("BlockFadeEvent on fire heat source invalidates cache radius")
    void shouldInvalidateRadiusWhenFireFades() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        Location fireLoc = new Location(mockWorld, 15, 64, 15);
        Block fireBlock = createMockBlock(Material.FIRE, fireLoc);

        BlockFadeEvent fadeEvent = new BlockFadeEvent(fireBlock, null);
        listener.onBlockFade(fadeEvent);

        assertThat(invalidatedRadiiLocations).contains(fireLoc);
    }

    @Test
    @DisplayName("BlockIgniteEvent on soul fire invalidates cache radius")
    void shouldInvalidateRadiusWhenFireIgnites() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        Location soulFireLoc = new Location(mockWorld, 15, 64, 15);
        Block soulFireBlock = createMockBlock(Material.SOUL_FIRE, soulFireLoc);

        BlockIgniteEvent igniteEvent = new BlockIgniteEvent(soulFireBlock, BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL, (org.bukkit.entity.Entity) null);
        listener.onBlockIgnite(igniteEvent);

        assertThat(invalidatedRadiiLocations).contains(soulFireLoc);
    }

    @Test
    @DisplayName("PlayerInteractEvent on campfire invalidates cache radius")
    void shouldInvalidateRadiusWhenPlayerInteractsWithCampfire() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        Location campfireLoc = new Location(mockWorld, 12, 64, 12);
        Block campfireBlock = createMockBlock(Material.CAMPFIRE, campfireLoc);

        PlayerInteractEvent interactEvent = new PlayerInteractEvent(null, Action.RIGHT_CLICK_BLOCK, null, campfireBlock, null);
        listener.onPlayerInteract(interactEvent);

        assertThat(invalidatedRadiiLocations).contains(campfireLoc);
    }

    @Test
    @DisplayName("Waterlogged campfire is recognized as inactive heat source")
    void shouldRecognizeWaterloggedCampfireAsInactive() {
        GreenhouseDetector detector = new GreenhouseDetector(config);

        Campfire waterloggedCampfireData = (Campfire) Proxy.newProxyInstance(
                Campfire.class.getClassLoader(),
                new Class<?>[]{Campfire.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isLit" -> true;
                    case "isWaterlogged" -> true; // Waterlogged!
                    case "getMaterial" -> Material.CAMPFIRE;
                    default -> null;
                }
        );

        Block waterloggedBlock = (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> mockWorld;
                    case "getX" -> 12;
                    case "getY" -> 64;
                    case "getZ" -> 11;
                    case "getType" -> Material.CAMPFIRE;
                    case "getBlockData" -> waterloggedCampfireData;
                    case "getLocation" -> new Location(mockWorld, 12, 64, 11);
                    default -> null;
                }
        );

        World scanWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> mockWorld.getUID();
                    case "getName" -> "scan_world";
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    case "getBlockAt" -> {
                        int x = (int) args[0];
                        int y = (int) args[1];
                        int z = (int) args[2];
                        if (x == 10 && y == 68 && z == 10) {
                            yield createMockBlock(Material.GLASS, new Location(mockWorld, 10, 68, 10));
                        }
                        if (x == 12 && y == 64 && z == 11) {
                            yield waterloggedBlock;
                        }
                        yield createMockBlock(Material.AIR, new Location(mockWorld, x, y, z));
                    }
                    default -> null;
                }
        );

        Location cropLoc = new Location(scanWorld, 10, 64, 10);
        // Crop has glass above, but campfire is waterlogged: detection must FAIL!
        assertThat(detector.isGreenhouse(cropLoc)).isFalse();
    }
}
