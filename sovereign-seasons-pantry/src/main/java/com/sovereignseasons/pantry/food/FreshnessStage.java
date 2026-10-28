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

/**
 * Visible freshness stage of a perishable food item.
 */
public enum FreshnessStage {
    FRESH("Fresco", "<green>"),
    STALE("Rancio", "<yellow>"),
    SPOILING("Pudriéndose", "<red>"),
    SPOILED("Podrido", "<dark_red>");

    private final String displayName;
    private final String colorTag;

    FreshnessStage(String displayName, String colorTag) {
        this.displayName = displayName;
        this.colorTag = colorTag;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorTag() {
        return colorTag;
    }

    /**
     * Classifies a freshness fraction (0.0 = spoiled, 1.0 = brand new).
     *
     * @param freshness  remaining freshness fraction
     * @param freshAbove fraction above which the food is FRESH
     * @param staleAbove fraction above which the food is STALE (below it, SPOILING)
     */
    public static FreshnessStage of(double freshness, double freshAbove, double staleAbove) {
        if (!Double.isFinite(freshness) || freshness <= 0.0) {
            return SPOILED;
        }
        if (freshness > freshAbove) {
            return FRESH;
        }
        if (freshness > staleAbove) {
            return STALE;
        }
        return SPOILING;
    }
}
