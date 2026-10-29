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

package com.sovereignseasons.logistics;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.sovereignseasons.logistics.crates.CrateMath;
import com.sovereignseasons.logistics.ice.FreezeRules;
import com.sovereignseasons.logistics.ice.IceRegistry;
import com.sovereignseasons.logistics.weight.WeightModel;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LogisticsModelsTest {

    @Test
    @DisplayName("Weight rules: first matching pattern wins, default otherwise")
    void weightRules() {
        WeightModel model = new WeightModel(new LogisticsConfig(null));
        assertThat(model.itemWeight(Material.IRON_BLOCK)).isEqualTo(9.0);
        assertThat(model.itemWeight(Material.COBBLESTONE)).isEqualTo(1.0);
        assertThat(model.itemWeight(Material.DIAMOND_ORE)).isEqualTo(1.0);
        assertThat(model.itemWeight(Material.STONE_BRICKS)).isEqualTo(1.0);
        assertThat(model.itemWeight(Material.IRON_PICKAXE)).isEqualTo(2.0);
        assertThat(model.itemWeight(Material.CARROT)).isEqualTo(0.25);
        assertThat(model.itemWeight(Material.COOKED_BEEF)).isEqualTo(0.25);
        assertThat(model.itemWeight(Material.FEATHER)).isEqualTo(0.5);
        assertThat(model.itemWeight(Material.AIR)).isZero();
        assertThat(model.itemWeight(null)).isZero();
    }

    @Test
    @DisplayName("Crates and nested containers weigh less than loose goods")
    void reducedWeights() {
        WeightModel model = new WeightModel(new LogisticsConfig(null));
        assertThat(model.stackWeight(Material.CARROT, 9)).isCloseTo(2.25, within(1e-9));
        assertThat(model.crateWeight(Material.CARROT, 9, 1)).isCloseTo(1.35, within(1e-9));
        assertThat(model.crateWeight(Material.CARROT, 9, 4)).isCloseTo(5.4, within(1e-9));
        assertThat(model.nestedWeight(100.0)).isCloseTo(50.0, within(1e-9));
        assertThat(model.stackWeight(Material.COBBLESTONE, -3)).isZero();
    }

    @Test
    @DisplayName("Load levels follow the capacity thresholds and only slow when enabled")
    void loadLevels() {
        LogisticsConfig config = new LogisticsConfig(null);
        WeightModel model = new WeightModel(config);
        assertThat(model.load(600)).isEqualTo(WeightModel.Load.LIGHT);
        assertThat(model.load(601)).isEqualTo(WeightModel.Load.HEAVY);
        assertThat(model.load(851)).isEqualTo(WeightModel.Load.OVERLOADED);
        assertThat(model.slownessLevel(2000)).isZero(); // disabled by default
        config.setWeightEnabled(true);
        assertThat(model.slownessLevel(100)).isZero();
        assertThat(model.slownessLevel(700)).isEqualTo(1);
        assertThat(model.slownessLevel(2000)).isEqualTo(2);
        config.setWeightThresholds(0.9, 0.5); // invalid -> defaults
        assertThat(config.getHeavyThreshold()).isEqualTo(0.6);
        assertThat(config.getOverloadedThreshold()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Crate freshness: same stack packed at different moments yields the same value")
    void crateValueIsStackable() {
        long expiresAt = 200_000L;
        long a = CrateMath.packedValue(expiresAt - 0, 100_000L, 0.5);
        // The same stack 1000 ticks later has the same expiry at baseline rate (it was in the open in between)
        long b = CrateMath.packedValue(expiresAt, 101_000L, 0.5);
        assertThat(a - b).isEqualTo(500L);
        // Two stacks with identical state packed at the same moment are identical
        assertThat(CrateMath.packedValue(expiresAt, 100_000L, 0.5)).isEqualTo(a);
    }

    @Test
    @DisplayName("Crate freshness: food decays at the packed rate while inside")
    void crateDecaysAtPackedRate() {
        long packedAt = 100_000L;
        long expiresAt = packedAt + 72_000L; // 3 days left
        long value = CrateMath.packedValue(expiresAt, packedAt, 0.5);
        // 4 days later at rate 0.5 only 2 days were consumed: 1 day left
        long unpackedAt = packedAt + 96_000L;
        long after = CrateMath.unpackedExpiresAt(value, unpackedAt, 0.5);
        assertThat(after - unpackedAt).isEqualTo(24_000L);
        // Immediate unpacking returns the original expiry
        assertThat(CrateMath.unpackedExpiresAt(value, packedAt, 0.5)).isEqualTo(expiresAt);
        // Rate 1.0 behaves like the open air; non-finite rates fall back to it
        assertThat(CrateMath.unpackedExpiresAt(CrateMath.packedValue(expiresAt, packedAt, 1.0), unpackedAt, 1.0)).isEqualTo(expiresAt);
        assertThat(CrateMath.unpackedExpiresAt(CrateMath.packedValue(expiresAt, packedAt, Double.NaN), unpackedAt, Double.NaN)).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("Lakes freeze only in freezing winters, on free open still water outside greenhouses")
    void freezeRules() {
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.FREEZING, true, true, false, false)).isTrue();
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.COLD, true, true, false, false)).isFalse();
        assertThat(FreezeRules.shouldFreeze(Season.AUTUMN, TemperatureBand.FREEZING, true, true, false, false)).isFalse();
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.FREEZING, false, true, false, false)).isFalse();
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.FREEZING, true, false, false, false)).isFalse();
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.FREEZING, true, true, true, false)).isFalse();
        assertThat(FreezeRules.shouldFreeze(Season.WINTER, TemperatureBand.FREEZING, true, true, false, true)).isFalse();
        assertThat(FreezeRules.shouldThaw(Season.SPRING, TemperatureBand.FREEZING)).isTrue();
        assertThat(FreezeRules.shouldThaw(Season.WINTER, TemperatureBand.MILD)).isTrue();
        assertThat(FreezeRules.shouldThaw(Season.WINTER, TemperatureBand.FREEZING)).isFalse();
        assertThat(FreezeRules.shouldThaw(null, null)).isTrue();
    }

    @Test
    @DisplayName("Ice registry packs positions losslessly, stays sorted and ignores duplicates")
    void iceRegistry() {
        long a = IceRegistry.pack(15, -64, 0);
        long b = IceRegistry.pack(0, 319, 15);
        long c = IceRegistry.pack(7, 62, 9);
        assertThat(IceRegistry.localX(a)).isEqualTo(15);
        assertThat(IceRegistry.y(a)).isEqualTo(-64);
        assertThat(IceRegistry.localZ(b)).isEqualTo(15);
        assertThat(IceRegistry.y(b)).isEqualTo(319);
        assertThat(IceRegistry.y(IceRegistry.pack(3, 2040, 3))).isEqualTo(2040);

        long[] registry = IceRegistry.add(null, c);
        registry = IceRegistry.add(registry, a);
        registry = IceRegistry.add(registry, b);
        registry = IceRegistry.add(registry, c);
        assertThat(registry).hasSize(3).isSorted();
        assertThat(IceRegistry.contains(registry, a)).isTrue();
        registry = IceRegistry.remove(registry, a);
        assertThat(IceRegistry.contains(registry, a)).isFalse();
        assertThat(IceRegistry.remove(registry, a)).hasSize(2);
        assertThat(IceRegistry.dropFirst(registry, 1)).hasSize(1);
        assertThat(IceRegistry.dropFirst(registry, 5)).isEmpty();
        assertThat(IceRegistry.remove(null, a)).isEmpty();
        assertThat(IceRegistry.contains(null, a)).isFalse();
    }
}
