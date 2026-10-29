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

package com.sovereignseasons.commerce.pricing;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.commerce.pricing.TradePricing.Direction;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PricingModelTest {

    @Test
    @DisplayName("Trades are priced by the product, never by the emeralds")
    void classifiesTrades() {
        assertThat(TradePricing.direction(Material.CARROT, Material.EMERALD)).isEqualTo(Direction.VILLAGER_BUYS);
        assertThat(TradePricing.product(Material.CARROT, Material.EMERALD)).isEqualTo(Material.CARROT);
        assertThat(TradePricing.direction(Material.EMERALD, Material.BREAD)).isEqualTo(Direction.VILLAGER_SELLS);
        assertThat(TradePricing.product(Material.EMERALD, Material.BREAD)).isEqualTo(Material.BREAD);
        assertThat(TradePricing.direction(Material.BOOK, Material.ENCHANTED_BOOK)).isEqualTo(Direction.OTHER);
        assertThat(TradePricing.product(Material.EMERALD, Material.EMERALD)).isNull();
        assertThat(TradePricing.direction(null, Material.EMERALD)).isEqualTo(Direction.OTHER);
    }

    @Test
    @DisplayName("A scarce product needs fewer units when sold to a villager and more emeralds when bought from it")
    void adjustsTheFirstIngredient() {
        // 22 carrots -> 1 emerald: winter (1.4) asks 16, a harvest season (0.8) asks 28
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 22, 64, 1.4, 0.5, 2.0)).isEqualTo(-6);
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 22, 64, 0.8, 0.5, 2.0)).isEqualTo(6);
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 22, 64, 1.0, 0.5, 2.0)).isZero();
        // 3 emeralds -> golden carrots cost 4 in winter; a 1-emerald trade cannot move by rounding
        assertThat(TradePricing.adjustment(Direction.VILLAGER_SELLS, 3, 64, 1.4, 0.5, 2.0)).isEqualTo(1);
        assertThat(TradePricing.adjustment(Direction.VILLAGER_SELLS, 1, 64, 1.4, 0.5, 2.0)).isZero();
        assertThat(TradePricing.adjustment(Direction.VILLAGER_SELLS, 1, 64, 0.5, 0.5, 2.0)).isZero(); // never below 1
    }

    @Test
    @DisplayName("Adjustments respect the value bounds, the stack size and invalid input")
    void boundsAdjustments() {
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 22, 64, 10.0, 0.5, 2.0)).isEqualTo(-11);
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 60, 64, 0.1, 0.5, 2.0)).isEqualTo(4); // 120 capped at 64
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 1, 1, 0.5, 0.5, 2.0)).isZero();
        assertThat(TradePricing.adjustment(Direction.OTHER, 22, 64, 1.4, 0.5, 2.0)).isZero();
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 0, 64, 1.4, 0.5, 2.0)).isZero();
        assertThat(TradePricing.adjustment(Direction.VILLAGER_BUYS, 22, 64, Double.NaN, 0.5, 2.0)).isZero();
    }

    @Test
    @DisplayName("Crops are cheap in season, normal off season and dear in winter; goods use fixed values")
    void productValues() {
        ProductValues values = new ProductValues(
                Map.of(Material.CARROT, EnumSet.of(Season.SPRING, Season.AUTUMN)),
                Map.of(Material.COAL, Map.of(Season.WINTER, 1.3), Material.LEATHER, Map.of()),
                0.8, 1.0, 1.4);
        assertThat(values.value(Material.CARROT, Season.AUTUMN)).isEqualTo(0.8);
        assertThat(values.value(Material.CARROT, Season.SUMMER)).isEqualTo(1.0);
        assertThat(values.value(Material.CARROT, Season.WINTER)).isEqualTo(1.4);
        assertThat(values.value(Material.COAL, Season.WINTER)).isEqualTo(1.3);
        assertThat(values.value(Material.COAL, Season.SUMMER)).isEqualTo(1.0);
        assertThat(values.value(Material.LEATHER, Season.WINTER)).isEqualTo(1.0);
        assertThat(values.value(Material.DIAMOND, Season.WINTER)).isEqualTo(1.0);
        assertThat(values.value(null, Season.WINTER)).isEqualTo(1.0);
        assertThat(values.isTracked(Material.COAL)).isTrue();
        assertThat(values.isTracked(Material.DIAMOND)).isFalse();
        assertThat(values.trackedProducts()).containsExactlyInAnyOrder(Material.CARROT, Material.COAL, Material.LEATHER);
    }

    @Test
    @DisplayName("A crop grown in winter is not scarce in winter")
    void winterCrop() {
        ProductValues values = new ProductValues(Map.of(Material.SWEET_BERRIES, EnumSet.of(Season.WINTER)), Map.of(), 0.8, 1.0, 1.4);
        assertThat(values.value(Material.SWEET_BERRIES, Season.WINTER)).isEqualTo(0.8);
    }
}
