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

import org.bukkit.Material;

/**
 * Turns a seasonal product value into a villager price adjustment.
 * <p>
 * Vanilla only lets plugins change the amount of the first ingredient of a trade (its "special price"), so:
 * <ul>
 *   <li>when the villager <b>buys</b> a product for emeralds, the product is the first ingredient and a higher
 *       value means fewer units are needed;</li>
 *   <li>when the villager <b>sells</b> a product for emeralds, the emeralds are the first ingredient and a higher
 *       value means more emeralds are asked.</li>
 * </ul>
 */
public final class TradePricing {

    public enum Direction {
        /** The villager pays emeralds for the product (the player sells). */
        VILLAGER_BUYS,
        /** The villager sells the product for emeralds (the player buys). */
        VILLAGER_SELLS,
        /** Neither side is plain emeralds: the trade is left alone. */
        OTHER
    }

    private TradePricing() {
    }

    public static Direction direction(Material firstIngredient, Material result) {
        if (firstIngredient == null || result == null) {
            return Direction.OTHER;
        }
        if (result == Material.EMERALD && firstIngredient != Material.EMERALD) {
            return Direction.VILLAGER_BUYS;
        }
        if (firstIngredient == Material.EMERALD && result != Material.EMERALD) {
            return Direction.VILLAGER_SELLS;
        }
        return Direction.OTHER;
    }

    /**
     * Product whose seasonal value prices the trade, or null for trades that are left alone.
     */
    public static Material product(Material firstIngredient, Material result) {
        return switch (direction(firstIngredient, result)) {
            case VILLAGER_BUYS -> firstIngredient;
            case VILLAGER_SELLS -> result;
            case OTHER -> null;
        };
    }

    /**
     * Change to add to the first ingredient of a trade so it reflects the product's seasonal value.
     *
     * @param baseAmount amount of the first ingredient in the vanilla trade
     * @param maxStack   max stack size of the first ingredient (vanilla never asks for more)
     * @param value      seasonal value of the product (1.0 = vanilla)
     * @param minValue   lowest value allowed
     * @param maxValue   highest value allowed
     */
    public static int adjustment(Direction direction, int baseAmount, int maxStack, double value, double minValue, double maxValue) {
        if (direction == Direction.OTHER || baseAmount <= 0 || !Double.isFinite(value)) {
            return 0;
        }
        double bounded = Math.max(minValue, Math.min(maxValue, value));
        double factor = direction == Direction.VILLAGER_BUYS ? 1.0 / bounded : bounded;
        long target = Math.round(baseAmount * factor);
        int limit = Math.max(1, maxStack);
        int clamped = (int) Math.max(1L, Math.min(limit, target));
        return clamped - baseAmount;
    }
}
