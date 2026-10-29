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


package com.sovereignseasons.logistics.crates;

import com.sovereignseasons.logistics.config.LogisticsConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class CrateUnpackerTest {
    private final Map<ItemStack, Long> expiries = new IdentityHashMap<>();
    private long now = 100_000L;
    private boolean spoilToRottenFlesh = true;

    private final FreshnessBridge bridge = new FreshnessBridge() {
        @Override public long now() { return now; }
        @Override public boolean isPerishable(Material material) { return material == Material.CARROT; }
        @Override public OptionalLong expiresAt(ItemStack item) { return OptionalLong.empty(); }
        @Override public void setExpiresAt(ItemStack item, long expiresAt) { expiries.put(item, expiresAt); }
        @Override public long granularity() { return 1L; }
        @Override public boolean spoilsToRottenFlesh() { return spoilToRottenFlesh; }
    };

    @Test
    @DisplayName("Unpacked food gets back its expiry, or turns into rotten flesh once it expired inside")
    void unpacksWithFreshness() {
        CrateUnpacker unpacker = new CrateUnpacker(new LogisticsConfig(null), bridge);
        long packedValue = CrateMath.packedValue(200_000L, 50_000L, 0.5);
        ItemStack carrots = unpacker.unpack(new CrateItems.Contents(Material.CARROT, 9, OptionalLong.of(packedValue)));
        assertThat(carrots.getType()).isEqualTo(Material.CARROT);
        assertThat(carrots.getAmount()).isEqualTo(9);
        assertThat(expiries.get(carrots)).isEqualTo(CrateMath.unpackedExpiresAt(packedValue, now, 0.5));

        now = 10_000_000L;
        assertThat(unpacker.unpack(new CrateItems.Contents(Material.CARROT, 9, OptionalLong.of(packedValue))).getType())
                .isEqualTo(Material.ROTTEN_FLESH);
        spoilToRottenFlesh = false;
        assertThat(unpacker.unpack(new CrateItems.Contents(Material.CARROT, 9, OptionalLong.of(packedValue))).getType())
                .isEqualTo(Material.CARROT);
    }

    @Test
    @DisplayName("Repacking never adds freshness, even when the packing tick falls on a rounding tie")
    void repackingNeverRefreshesFood() {
        long granularity = 1000L;
        double rate = 0.5;
        // now = 1000 gives a shift of exactly 500: the tie that rounding to the nearest value turned into +1000
        long now = 1000L;
        long expiresAt = 11_000L;
        long exactInsideCrate = CrateMath.packedValue(expiresAt, now, rate);
        for (int cycle = 0; cycle < 500; cycle++) {
            long packed = CrateMath.floorTo(CrateMath.packedValue(expiresAt, now, rate), granularity);
            now += 2L; // unpack two ticks later and pack again: the fastest possible exploit loop
            expiresAt = CrateMath.floorTo(CrateMath.unpackedExpiresAt(packed, now, rate), granularity);
            assertThat(expiresAt).as("cycle " + cycle).isLessThanOrEqualTo(CrateMath.unpackedExpiresAt(exactInsideCrate, now, rate));
        }
        // Two crates packed from one stack a few ticks apart still get the same value (they keep stacking),
        // unless a granularity boundary falls between them, as it already did when rounding to the nearest value
        assertThat(CrateMath.floorTo(CrateMath.packedValue(200_000L, 50_100L, rate), granularity))
                .isEqualTo(CrateMath.floorTo(CrateMath.packedValue(200_000L, 50_114L, rate), granularity));
        assertThat(CrateMath.floorTo(-1_500L, granularity)).isEqualTo(-2_000L);
        assertThat(CrateMath.floorTo(1_234L, 1L)).isEqualTo(1_234L);
    }

    @Test
    @DisplayName("Without Pantry, or for crates packed without it, items come back untouched")
    void unpacksPlainItems() {
        ItemStack plain = new CrateUnpacker(new LogisticsConfig(null), null)
                .unpack(new CrateItems.Contents(Material.CARROT, 9, OptionalLong.of(5L)));
        assertThat(plain.getType()).isEqualTo(Material.CARROT);
        ItemStack untracked = new CrateUnpacker(new LogisticsConfig(null), bridge)
                .unpack(new CrateItems.Contents(Material.CARROT, 9, OptionalLong.empty()));
        assertThat(expiries).doesNotContainKey(untracked);
    }
}
