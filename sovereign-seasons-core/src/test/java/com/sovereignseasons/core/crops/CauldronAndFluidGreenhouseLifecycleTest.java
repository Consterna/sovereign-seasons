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
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CauldronAndFluidGreenhouseLifecycleTest {
    private SeasonsConfig config;
    private SeasonService mockSeasonService;
    private GreenhouseService mockGreenhouseService;
    private World mockWorld;
    private List<Location> invalidatedLocations;
    private List<Location> invalidatedRadiiLocations;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        config.setHeatRadius(4);
        config.setMaxCeilingHeight(16);

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

    private Block createMockBlock(Material type, int x, int y, int z) {
        Location loc = new Location(mockWorld, x, y, z);
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(),
                new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "getLocation" -> loc;
                    case "getWorld" -> mockWorld;
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    default -> null;
                }
        );
    }

    private BlockState createMockBlockState(Material type, int x, int y, int z) {
        Location loc = new Location(mockWorld, x, y, z);
        return (BlockState) Proxy.newProxyInstance(
                BlockState.class.getClassLoader(),
                new Class<?>[]{BlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> type;
                    case "getLocation" -> loc;
                    case "getWorld" -> mockWorld;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("CauldronLevelChangeEvent filling cauldron with lava invalidates greenhouse heat radius")
    void shouldInvalidateRadiusWhenCauldronFilledWithLava() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block cauldronBlock = createMockBlock(Material.CAULDRON, 10, 64, 10);
        BlockState newLavaState = createMockBlockState(Material.LAVA_CAULDRON, 10, 64, 10);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                cauldronBlock, null, CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY, newLavaState
        );

        listener.onCauldronLevelChange(event);

        assertThat(invalidatedRadiiLocations).hasSize(1);
        assertThat(invalidatedRadiiLocations.get(0).getBlockX()).isEqualTo(10);
        assertThat(invalidatedRadiiLocations.get(0).getBlockY()).isEqualTo(64);
        assertThat(invalidatedRadiiLocations.get(0).getBlockZ()).isEqualTo(10);
    }

    @Test
    @DisplayName("CauldronLevelChangeEvent scooping lava out of cauldron invalidates greenhouse heat radius")
    void shouldInvalidateRadiusWhenLavaScoopedFromCauldron() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block lavaCauldronBlock = createMockBlock(Material.LAVA_CAULDRON, 20, 64, 20);
        BlockState emptyState = createMockBlockState(Material.CAULDRON, 20, 64, 20);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                lavaCauldronBlock, null, CauldronLevelChangeEvent.ChangeReason.BUCKET_FILL, emptyState
        );

        listener.onCauldronLevelChange(event);

        assertThat(invalidatedRadiiLocations).hasSize(1);
        assertThat(invalidatedRadiiLocations.get(0).getBlockX()).isEqualTo(20);
    }

    @Test
    @DisplayName("CauldronLevelChangeEvent with non-heat change (water cauldron) ignores heat invalidation")
    void shouldIgnoreNonHeatCauldronChanges() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block cauldronBlock = createMockBlock(Material.CAULDRON, 5, 64, 5);
        BlockState waterState = createMockBlockState(Material.WATER_CAULDRON, 5, 64, 5);

        CauldronLevelChangeEvent event = new CauldronLevelChangeEvent(
                cauldronBlock, null, CauldronLevelChangeEvent.ChangeReason.BUCKET_EMPTY, waterState
        );

        listener.onCauldronLevelChange(event);

        assertThat(invalidatedRadiiLocations).isEmpty();
    }

    @Test
    @DisplayName("EntityChangeBlockEvent (falling block, Enderman, Wither) invalidates column downwards")
    void shouldInvalidateOnEntityBlockChange() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block glassBlock = createMockBlock(Material.GLASS, 0, 70, 0);
        Entity mockEntity = (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> null
        );

        org.bukkit.block.data.BlockData mockBlockData = (org.bukkit.block.data.BlockData) Proxy.newProxyInstance(
                org.bukkit.block.data.BlockData.class.getClassLoader(),
                new Class<?>[]{org.bukkit.block.data.BlockData.class},
                (proxy, method, args) -> null
        );

        EntityChangeBlockEvent event = new EntityChangeBlockEvent(
                mockEntity, glassBlock, mockBlockData
        );

        listener.onEntityChangeBlock(event);

        // 17 vertical positions invalidated (dy = 0 to 16)
        assertThat(invalidatedLocations).hasSize(17);
        assertThat(invalidatedLocations.get(0).getBlockY()).isEqualTo(70);
        assertThat(invalidatedLocations.get(16).getBlockY()).isEqualTo(54);
    }

    @Test
    @DisplayName("BlockFromToEvent (fluid flow) invalidates source and destination blocks")
    void shouldInvalidateOnFluidFlow() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block fromBlock = createMockBlock(Material.LAVA, 10, 65, 10);
        Block toBlock = createMockBlock(Material.AIR, 10, 64, 10);

        BlockFromToEvent event = new BlockFromToEvent(fromBlock, toBlock);
        listener.onBlockFromTo(event);

        // Lava is heat source: invalidates heat radius
        assertThat(invalidatedRadiiLocations).hasSize(1);
        assertThat(invalidatedRadiiLocations.get(0).getBlockX()).isEqualTo(10);
        assertThat(invalidatedRadiiLocations.get(0).getBlockY()).isEqualTo(65);
    }

    @Test
    @DisplayName("FurnaceBurnEvent on furnace/smoker/blast furnace invalidates greenhouse heat radius")
    void shouldInvalidateRadiusWhenFurnaceBurnsFuel() {
        CropGrowthListener listener = new CropGrowthListener(config, mockSeasonService, mockGreenhouseService);

        Block furnaceBlock = createMockBlock(Material.FURNACE, 30, 64, 30);
        ItemStack coal = new ItemStack(Material.COAL);
        FurnaceBurnEvent event = new FurnaceBurnEvent(furnaceBlock, coal, 1600);

        listener.onFurnaceBurn(event);

        assertThat(invalidatedRadiiLocations).hasSize(1);
        assertThat(invalidatedRadiiLocations.get(0).getBlockX()).isEqualTo(30);
        assertThat(invalidatedRadiiLocations.get(0).getBlockY()).isEqualTo(64);
        assertThat(invalidatedRadiiLocations.get(0).getBlockZ()).isEqualTo(30);
    }
}
