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

package com.sovereignseasons.pantry.api;

import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.DecayCalculator;
import com.sovereignseasons.pantry.food.FoodState;
import com.sovereignseasons.pantry.food.FreshnessStage;
import com.sovereignseasons.pantry.food.Preservation;
import com.sovereignseasons.pantry.item.FoodStateCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PantryApiImplTest {
    private final Map<ItemStack, FoodState> states = new IdentityHashMap<>();
    private final AtomicLong clock = new AtomicLong(10_000L);
    private PantryApi api;

    @BeforeEach
    void setup() {
        PantryConfig config = new PantryConfig(null);
        FoodStateCodec codec = new FoodStateCodec() {
            @Override
            public Optional<FoodState> read(ItemStack item) {
                return Optional.ofNullable(states.get(item));
            }

            @Override
            public void write(ItemStack item, FoodState state, FreshnessStage stage) {
                states.put(item, state);
            }
        };
        api = new PantryApiImpl(new DecayCalculator(config), codec, clock::get);
    }

    @Test
    @DisplayName("Exposes the decay clock, perishability and rounded fresh expiries")
    void basics() {
        assertThat(api.now()).isEqualTo(10_000L);
        assertThat(api.isPerishable(Material.CARROT)).isTrue();
        assertThat(api.isPerishable(Material.GOLDEN_CARROT)).isFalse();
        assertThat(api.freshExpiresAt(Material.CARROT, 10_000L)).isEqualTo(10_000L + 12 * 24_000L);
        assertThat(api.roundExpiry(10_400L)).isEqualTo(10_000L);
        assertThat(api.roundExpiry(10_600L)).isEqualTo(11_000L);
    }

    @Test
    @DisplayName("Reads and writes expiries while keeping the preservation method")
    void readWrite() {
        ItemStack carrots = new ItemStack(Material.CARROT, 9);
        assertThat(api.getExpiresAt(carrots)).isEmpty();
        states.put(carrots, new FoodState(50_000L, Preservation.SMOKED));
        assertThat(api.getExpiresAt(carrots)).hasValue(50_000L);
        api.setExpiresAt(carrots, 77_000L);
        assertThat(states.get(carrots)).isEqualTo(new FoodState(77_000L, Preservation.SMOKED));

        ItemStack stone = new ItemStack(Material.STONE, 1);
        api.setExpiresAt(stone, 1L);
        assertThat(states).doesNotContainKey(stone);
        assertThat(api.getExpiresAt(stone)).isEmpty();
        assertThat(api.getExpiresAt(null)).isEmpty();
    }
}
