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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalLong;

/**
 * Public API of Sovereign Seasons Pantry, registered in Bukkit's ServicesManager.
 * Lets other modules (e.g. Logistics crates) carry food freshness across their own storage.
 * <p>
 * Expiries are decay-clock ticks assuming a baseline decay rate of 1.0 (see the Pantry README).
 */
public interface PantryApi {

    /**
     * Current decay-clock tick (full time of the primary world).
     */
    long now();

    /**
     * Whether the material spoils at all.
     */
    boolean isPerishable(Material material);

    /**
     * Expiry of a food stack, or empty if the stack is not perishable or has never been tracked.
     */
    OptionalLong getExpiresAt(ItemStack item);

    /**
     * Expiry a freshly produced item of this material would get at {@code now}.
     */
    long freshExpiresAt(Material material, long now);

    /**
     * Stores an expiry on a perishable stack (keeping its preservation method) and refreshes its lore.
     * Does nothing for non-perishable stacks.
     */
    void setExpiresAt(ItemStack item, long expiresAt);

    /**
     * Rounds an expiry to Pantry's configured granularity, so stacks produced close in time keep stacking.
     */
    long roundExpiry(long expiresAt);
}
