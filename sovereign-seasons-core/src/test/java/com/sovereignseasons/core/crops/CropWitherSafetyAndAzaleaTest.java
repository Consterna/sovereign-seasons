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

package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.CropWinterWitherEvent;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CropWitherSafetyAndAzaleaTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorld;
    private AtomicBoolean isInsideGreenhouse;
    private AtomicReference<Material> currentBlockType;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setWinterGrowthPaused(true);
        config.setWinterWitherChance(1.0); // 100% chance for test determinism
        config.setPlayWitherEffects(false);

        isInsideGreenhouse = new AtomicBoolean(false);
        currentBlockType = new AtomicReference<>(Material.WHEAT);

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
                    case "isGreenhouse" -> isInsideGreenhouse.get();
                    default -> null;
                }
        );
    }

    private Block createMockBlock(Material type) {
        Location loc = new Location(mockWorld, 0, 64, 0);
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> currentBlockType.get();
                    case "getLocation" -> loc;
                    case "getWorld" -> mockWorld;
                    case "setType" -> {
                        Material newMat = (Material) args[0];
                        if (!newMat.isBlock()) {
                            throw new IllegalArgumentException("Cannot set a block to an item (" + newMat + " is not a block)");
                        }
                        currentBlockType.set(newMat);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private BlockState createMockBlockState(Material type) {
        return (BlockState) Proxy.newProxyInstance(
                BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("CropWinterWitherEvent throws IllegalArgumentException when non-block material is passed to setReplacementMaterial")
    void shouldRejectNonBlockInSetReplacementMaterial() {
        Block mockBlock = createMockBlock(Material.WHEAT);
        CropWinterWitherEvent event = new CropWinterWitherEvent(mockBlock, Material.DEAD_BUSH);

        assertThatThrownBy(() -> event.setReplacementMaterial(Material.DIAMOND_SWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a block");

        // Valid block types must succeed
        event.setReplacementMaterial(Material.AIR);
        assertThat(event.getReplacementMaterial()).isEqualTo(Material.AIR);
    }

    @Test
    @DisplayName("CropGrowthListener onCropGrow safely withers crop to DEAD_BUSH")
    void shouldWitherCropSafelyToDeadBush() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        Block block = createMockBlock(Material.WHEAT);
        BlockState newState = createMockBlockState(Material.WHEAT);

        BlockGrowEvent growEvent = new BlockGrowEvent(block, newState);
        listener.onCropGrow(growEvent);

        assertThat(growEvent.isCancelled()).isTrue();
        assertThat(currentBlockType.get()).isEqualTo(Material.DEAD_BUSH);
    }

    @Test
    @DisplayName("CropGrowthListener cancels bone meal on AZALEA outside greenhouse in winter")
    void shouldCancelBoneMealOnAzaleaOutsideInWinter() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        currentBlockType.set(Material.AZALEA);
        Block azaleaBlock = createMockBlock(Material.AZALEA);

        BlockFertilizeEvent event = new BlockFertilizeEvent(azaleaBlock, null, new ArrayList<>());
        listener.onCropFertilize(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("CropGrowthListener cancels bone meal on FLOWERING_AZALEA outside greenhouse in winter")
    void shouldCancelBoneMealOnFloweringAzaleaOutsideInWinter() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        currentBlockType.set(Material.FLOWERING_AZALEA);
        Block azaleaBlock = createMockBlock(Material.FLOWERING_AZALEA);

        BlockFertilizeEvent event = new BlockFertilizeEvent(azaleaBlock, null, new ArrayList<>());
        listener.onCropFertilize(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("CropGrowthListener permits bone meal on AZALEA inside functional greenhouse in winter")
    void shouldPermitBoneMealOnAzaleaInsideGreenhouse() {
        isInsideGreenhouse.set(true);
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);
        currentBlockType.set(Material.AZALEA);
        Block azaleaBlock = createMockBlock(Material.AZALEA);

        BlockFertilizeEvent event = new BlockFertilizeEvent(azaleaBlock, null, new ArrayList<>());
        listener.onCropFertilize(event);

        assertThat(event.isCancelled()).isFalse();
    }
}
