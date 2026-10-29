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

package com.sovereignseasons.commerce.bulk;

import com.sovereignseasons.commerce.bulk.BulkSaleModel.Quote;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BulkSaleModelTest {
    // 22 carrots per emerald, winter (1.4) and the default 15 % bulk bonus: 22 / 1.61 = 13.66 carrots per emerald
    private static final double UNITS = 22.0;

    @Test
    @DisplayName("Pays whole emeralds and only takes the crates needed to pay them")
    void quotes() {
        assertThat(BulkSaleModel.quote(9, 8, 8, UNITS, 1.4, 1.15)).isEqualTo(new Quote(5, 8));
        assertThat(BulkSaleModel.quote(9, 3, 8, UNITS, 1.4, 1.15)).isEqualTo(new Quote(1, 2));
        assertThat(BulkSaleModel.quote(9, 1, 8, UNITS, 1.4, 1.15)).isEqualTo(Quote.NONE);
        // Without season or bonus a crate sells at the vanilla price: 6 crates = 54 carrots = 2 emeralds (5 crates used)
        assertThat(BulkSaleModel.quote(9, 6, 8, UNITS, 1.0, 1.0)).isEqualTo(new Quote(2, 5));
    }

    @Test
    @DisplayName("Never buys more crates than the daily limit of the villager allows")
    void respectsTheDailyLimit() {
        assertThat(BulkSaleModel.quote(9, 10, 4, UNITS, 1.4, 1.15)).isEqualTo(new Quote(2, 4));
        assertThat(BulkSaleModel.quote(9, 10, 0, UNITS, 1.4, 1.15)).isEqualTo(Quote.NONE);
        assertThat(BulkSaleModel.cratesLeft(8, 5L, 3, 5L)).isEqualTo(5);
        assertThat(BulkSaleModel.cratesLeft(8, 4L, 3, 5L)).isEqualTo(8);
        assertThat(BulkSaleModel.cratesLeft(8, 5L, 20, 5L)).isZero();
        assertThat(BulkSaleModel.cratesLeft(0, 5L, 0, 5L)).isZero();
    }

    @Test
    @DisplayName("Rejects nonsensical input instead of paying")
    void rejectsInvalidInput() {
        assertThat(BulkSaleModel.quote(0, 8, 8, UNITS, 1.4, 1.15).isSale()).isFalse();
        assertThat(BulkSaleModel.quote(9, 8, 8, 0.0, 1.4, 1.15).isSale()).isFalse();
        assertThat(BulkSaleModel.quote(9, 8, 8, UNITS, Double.NaN, 1.15).isSale()).isFalse();
        assertThat(BulkSaleModel.quote(9, 8, 8, UNITS, 1.4, 0.0).isSale()).isFalse();
    }

    @Test
    @DisplayName("Uses the own trade of the villager for the crop as the base price")
    void readsTheVillagerPrice() {
        MerchantRecipe carrots = new MerchantRecipe(new ItemStack(Material.EMERALD, 1), 16);
        carrots.addIngredient(new ItemStack(Material.CARROT, 22));
        MerchantRecipe bread = new MerchantRecipe(new ItemStack(Material.BREAD, 6), 16);
        bread.addIngredient(new ItemStack(Material.EMERALD, 1));
        List<MerchantRecipe> recipes = List.of(bread, carrots);
        assertThat(BulkSaleListener.unitsPerEmerald(recipes, Material.CARROT)).hasValue(22.0);
        assertThat(BulkSaleListener.unitsPerEmerald(recipes, Material.POTATO)).isEmpty();
        assertThat(BulkSaleListener.unitsPerEmerald(List.of(), Material.CARROT)).isEmpty();
    }
}
