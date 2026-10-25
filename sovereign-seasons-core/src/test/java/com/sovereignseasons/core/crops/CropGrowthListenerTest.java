package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CropGrowthListenerTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;

    private AtomicBoolean isWinter;
    private AtomicBoolean isInsideGreenhouse;
    private World mockWorld;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setWinterGrowthPaused(true);
        config.setPlayWitherEffects(false); // disable sound/particles for unit test

        isWinter = new AtomicBoolean(false);
        isInsideGreenhouse = new AtomicBoolean(false);

        UUID worldUid = UUID.randomUUID();
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldUid;
                    case "getName" -> "world";
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
                    case "isWinter" -> isWinter.get();
                    default -> null;
                }
        );

        mockGreenhouseService = (GreenhouseService) Proxy.newProxyInstance(
                GreenhouseService.class.getClassLoader(),
                new Class<?>[]{GreenhouseService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isGreenhouse" -> isInsideGreenhouse.get();
                    default -> null;
                }
        );
    }

    private Block createMockCropBlock(Material material, AtomicReference<Material> currentType) {
        Location loc = new Location(mockWorld, 0, 64, 0);

        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> mockWorld;
                    case "getType" -> currentType.get();
                    case "setType" -> {
                        currentType.set((Material) args[0]);
                        yield null;
                    }
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
    @DisplayName("Crops grow normally when it is not winter")
    void shouldAllowGrowthInNonWinter() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(false); // Spring / Summer / Autumn

        AtomicReference<Material> blockType = new AtomicReference<>(Material.WHEAT);
        Block block = createMockCropBlock(Material.WHEAT, blockType);
        BlockState newState = createMockBlockState(block, Material.WHEAT);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        listener.onCropGrow(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(blockType.get()).isEqualTo(Material.WHEAT);
    }

    @Test
    @DisplayName("Crops are paused in winter when outside greenhouse without withering if wither chance is 0%")
    void shouldPauseGrowthWithoutWithering() {
        config.setWinterWitherChance(0.0); // 0% withering
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(true);
        isInsideGreenhouse.set(false);

        AtomicReference<Material> blockType = new AtomicReference<>(Material.CARROTS);
        Block block = createMockCropBlock(Material.CARROTS, blockType);
        BlockState newState = createMockBlockState(block, Material.CARROTS);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        listener.onCropGrow(event);

        assertThat(event.isCancelled()).isTrue(); // Paused!
        assertThat(blockType.get()).isEqualTo(Material.CARROTS); // Not withered
    }

    @Test
    @DisplayName("Crops wither into DEAD_BUSH in winter outside greenhouse when wither chance triggers")
    void shouldWitherCropIntoDeadBush() {
        config.setWinterWitherChance(1.0); // 100% withering for deterministic test
        config.setWitherMaterial(Material.DEAD_BUSH);
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(true);
        isInsideGreenhouse.set(false);

        AtomicReference<Material> blockType = new AtomicReference<>(Material.POTATOES);
        Block block = createMockCropBlock(Material.POTATOES, blockType);
        BlockState newState = createMockBlockState(block, Material.POTATOES);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        listener.onCropGrow(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(blockType.get()).isEqualTo(Material.DEAD_BUSH); // Successfully withered!
    }

    @Test
    @DisplayName("Crops in winter grow normally when protected inside a greenhouse")
    void shouldAllowGrowthInWinterInsideGreenhouse() {
        config.setWinterWitherChance(1.0);
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(true);
        isInsideGreenhouse.set(true); // Inside greenhouse!

        AtomicReference<Material> blockType = new AtomicReference<>(Material.BEETROOTS);
        Block block = createMockCropBlock(Material.BEETROOTS, blockType);
        BlockState newState = createMockBlockState(block, Material.BEETROOTS);

        BlockGrowEvent event = new BlockGrowEvent(block, newState);
        listener.onCropGrow(event);

        assertThat(event.isCancelled()).isFalse(); // Allowed!
        assertThat(blockType.get()).isEqualTo(Material.BEETROOTS); // Unwithered
    }

    @Test
    @DisplayName("Melon and pumpkin spread is paused in winter outside greenhouse, but allowed inside")
    void shouldHandleFruitSpreadInWinter() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(true);
        isInsideGreenhouse.set(false);

        AtomicReference<Material> stemType = new AtomicReference<>(Material.MELON_STEM);
        Block stemBlock = createMockCropBlock(Material.MELON_STEM, stemType);
        Block targetBlock = createMockCropBlock(Material.DIRT, new AtomicReference<>(Material.DIRT));
        BlockState targetState = createMockBlockState(targetBlock, Material.MELON);

        // Outside greenhouse: cancelled
        BlockSpreadEvent eventOutside = new BlockSpreadEvent(targetBlock, stemBlock, targetState);
        listener.onFruitSpread(eventOutside);
        assertThat(eventOutside.isCancelled()).isTrue();

        // Inside greenhouse: allowed
        isInsideGreenhouse.set(true);
        BlockSpreadEvent eventInside = new BlockSpreadEvent(targetBlock, stemBlock, targetState);
        listener.onFruitSpread(eventInside);
        assertThat(eventInside.isCancelled()).isFalse();
    }

    @Test
    @DisplayName("Saplings and tree growth is paused in winter outside greenhouse")
    void shouldHandleSaplingGrowthInWinter() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        isWinter.set(true);
        isInsideGreenhouse.set(false);

        Location loc = new Location(mockWorld, 0, 64, 0);
        StructureGrowEvent eventOutside = new StructureGrowEvent(loc, org.bukkit.TreeType.TREE, false, null, new ArrayList<>());
        listener.onSaplingGrow(eventOutside);
        assertThat(eventOutside.isCancelled()).isTrue();

        isInsideGreenhouse.set(true);
        StructureGrowEvent eventInside = new StructureGrowEvent(loc, org.bukkit.TreeType.TREE, false, null, new ArrayList<>());
        listener.onSaplingGrow(eventInside);
        assertThat(eventInside.isCancelled()).isFalse();
    }
}
