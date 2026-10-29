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


package com.sovereignseasons.commerce.bridge;

import com.sovereignseasons.pantry.api.PantryApi;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;

/**
 * {@link FoodFreshness} backed by the Pantry API. Only referenced after checking that Pantry is installed.
 */
public final class PantryFoodFreshness implements FoodFreshness {
    private final PantryApi api;

    private PantryFoodFreshness(PantryApi api) {
        this.api = Objects.requireNonNull(api, "api cannot be null");
    }

    /**
     * Returns a bridge if the Pantry API is registered, or null otherwise.
     */
    public static FoodFreshness create(ServicesManager services) {
        PantryApi api = services.load(PantryApi.class);
        return api != null ? new PantryFoodFreshness(api) : null;
    }

    @Override
    public boolean isSpoiling(ItemStack item) {
        return item != null && api.isSpoiling(item);
    }
}
