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

package com.sovereignseasons.pantry.food;

import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.pantry.config.PantryConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DecayCalculatorTest {
    private static final long DAY = 24_000L;

    private PantryConfig config;
    private DecayCalculator calculator;

    @BeforeEach
    void setup() {
        config = new PantryConfig(null);
        calculator = new DecayCalculator(config);
    }

    @Test
    @DisplayName("Shelf life is expressed in in-game days and non-listed foods never spoil")
    void shouldComputeShelfLife() {
        assertThat(calculator.shelfLifeTicks(Material.BREAD)).isEqualTo(7 * DAY);
        assertThat(calculator.shelfLifeTicks(Material.BEEF)).isEqualTo(3 * DAY);
        assertThat(calculator.isPerishable(Material.GOLDEN_APPLE)).isFalse();
        assertThat(calculator.isPerishable(Material.DRIED_KELP)).isFalse();
        assertThat(calculator.isPerishable(Material.ROTTEN_FLESH)).isFalse();
        assertThat(calculator.isPerishable(Material.STONE)).isFalse();
        assertThat(calculator.isPerishable(null)).isFalse();
    }

    @Test
    @DisplayName("Decay rate combines temperature band, cellar, ice box and smoking")
    void shouldComposeRates() {
        assertThat(calculator.rate(StorageContext.open(TemperatureBand.MILD), Preservation.NONE)).isEqualTo(1.0);
        assertThat(calculator.rate(StorageContext.open(TemperatureBand.HOT), Preservation.NONE)).isEqualTo(2.0);
        assertThat(calculator.rate(StorageContext.open(TemperatureBand.FREEZING), Preservation.NONE)).isEqualTo(0.25);
        assertThat(calculator.rate(new StorageContext(TemperatureBand.COLD, true, false), Preservation.NONE)).isEqualTo(0.25);
        assertThat(calculator.rate(new StorageContext(TemperatureBand.MILD, true, true), Preservation.NONE)).isEqualTo(0.25);
        assertThat(calculator.rate(StorageContext.open(TemperatureBand.MILD), Preservation.SMOKED)).isEqualTo(0.25);
        assertThat(calculator.rate(null, Preservation.NONE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Disabling cellars removes both cellar and ice bonuses")
    void shouldIgnoreCellarWhenDisabled() {
        config.setCellarEnabled(false);
        assertThat(calculator.rate(new StorageContext(TemperatureBand.MILD, true, true), Preservation.NONE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Baseline rate never moves the expiry; cold extends it and heat shortens it")
    void shouldAdvanceExpiry() {
        FoodState fresh = calculator.createFresh(Material.BREAD, Preservation.NONE, 0L);
        assertThat(fresh.expiresAt()).isEqualTo(7 * DAY);

        assertThat(calculator.advance(fresh, DAY, 1.0).expiresAt()).isEqualTo(7 * DAY);
        assertThat(calculator.advance(fresh, DAY, 0.5).expiresAt()).isEqualTo(7 * DAY + DAY / 2);
        assertThat(calculator.advance(fresh, DAY, 2.0).expiresAt()).isEqualTo(6 * DAY);
        assertThat(calculator.advance(fresh, DAY, 0.0).expiresAt()).isEqualTo(8 * DAY);
    }

    @Test
    @DisplayName("Negative elapsed time (clock rewinds) and non-finite rates are ignored")
    void shouldIgnoreInvalidAdvance() {
        FoodState fresh = calculator.createFresh(Material.BREAD, Preservation.NONE, 0L);
        assertThat(calculator.advance(fresh, -5_000L, 2.0)).isEqualTo(fresh);
        assertThat(calculator.advance(fresh, 0L, 2.0)).isEqualTo(fresh);
        assertThat(calculator.advance(fresh, DAY, Double.NaN)).isEqualTo(fresh);
    }

    @Test
    @DisplayName("Expiries are rounded to the configured granularity so close items stack")
    void shouldRoundExpiries() {
        FoodState a = calculator.createFresh(Material.BREAD, Preservation.NONE, 10_100L);
        FoodState b = calculator.createFresh(Material.BREAD, Preservation.NONE, 10_400L);
        FoodState c = calculator.createFresh(Material.BREAD, Preservation.NONE, 10_600L);
        assertThat(a).isEqualTo(b);
        assertThat(c.expiresAt()).isEqualTo(a.expiresAt() + 1_000L);

        config.setExpiryGranularityTicks(1L);
        assertThat(calculator.createFresh(Material.BREAD, Preservation.NONE, 10_123L).expiresAt()).isEqualTo(7 * DAY + 10_123L);
    }

    @Test
    @DisplayName("Freshness and stages follow remaining life over shelf life")
    void shouldComputeFreshnessAndStage() {
        FoodState state = new FoodState(10 * DAY, Preservation.NONE); // apple: 10 days
        assertThat(calculator.freshness(state, Material.APPLE, 0L)).isCloseTo(1.0, within(1e-9));
        assertThat(calculator.stage(state, Material.APPLE, 0L)).isEqualTo(FreshnessStage.FRESH);
        assertThat(calculator.stage(state, Material.APPLE, 6 * DAY)).isEqualTo(FreshnessStage.STALE);
        assertThat(calculator.stage(state, Material.APPLE, 9 * DAY)).isEqualTo(FreshnessStage.SPOILING);
        assertThat(calculator.stage(state, Material.APPLE, 10 * DAY)).isEqualTo(FreshnessStage.SPOILED);
        assertThat(calculator.isSpoiled(state, Material.APPLE, 10 * DAY)).isTrue();
        assertThat(calculator.isSpoiled(state, Material.APPLE, 10 * DAY - 1)).isFalse();
        assertThat(calculator.freshness(state, Material.APPLE, 20 * DAY)).isZero();
        // Non-perishables are always fully fresh
        assertThat(calculator.freshness(state, Material.GOLDEN_APPLE, 20 * DAY)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Extreme values saturate instead of overflowing")
    void shouldSaturateOnOverflow() {
        FoodState nearMax = new FoodState(Long.MAX_VALUE - 10, Preservation.NONE);
        FoodState advanced = calculator.advance(nearMax, Long.MAX_VALUE / 2, 0.0);
        assertThat(advanced.expiresAt()).isPositive();

        FoodState created = calculator.createFresh(Material.BREAD, Preservation.NONE, Long.MAX_VALUE - 5);
        assertThat(created.expiresAt()).isPositive();
    }

    @Test
    @DisplayName("Smoked food lasts four times longer at baseline temperature")
    void smokedFoodShouldLastLonger() {
        FoodState smoked = calculator.createFresh(Material.COOKED_BEEF, Preservation.SMOKED, 0L);
        double rate = calculator.rate(StorageContext.open(TemperatureBand.MILD), smoked.preservation());
        // After 6 days (the normal shelf life) a smoked steak still has 75% freshness
        FoodState after = calculator.advance(smoked, 6 * DAY, rate);
        assertThat(calculator.freshness(after, Material.COOKED_BEEF, 6 * DAY)).isCloseTo(0.75, within(1e-6));
    }

    @Test
    @DisplayName("FreshnessStage classification handles invalid input")
    void stageShouldHandleInvalidInput() {
        assertThat(FreshnessStage.of(Double.NaN, 0.5, 0.15)).isEqualTo(FreshnessStage.SPOILED);
        assertThat(FreshnessStage.of(-1.0, 0.5, 0.15)).isEqualTo(FreshnessStage.SPOILED);
        assertThat(FreshnessStage.of(0.51, 0.5, 0.15)).isEqualTo(FreshnessStage.FRESH);
        assertThat(FreshnessStage.of(0.5, 0.5, 0.15)).isEqualTo(FreshnessStage.STALE);
        assertThat(FreshnessStage.of(0.15, 0.5, 0.15)).isEqualTo(FreshnessStage.SPOILING);
    }
}
