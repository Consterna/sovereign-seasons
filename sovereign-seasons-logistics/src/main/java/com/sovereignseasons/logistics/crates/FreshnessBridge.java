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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalLong;

/**
 * Food freshness access used by crates. Free of Pantry types so Logistics loads without Pantry installed;
 * the Pantry-backed implementation lives in {@link PantryFreshnessBridge} and is only loaded when Pantry is present.
 */
public interface FreshnessBridge {

    long now();

    boolean isPerishable(Material material);

    /**
     * Expiry of a stack; untracked perishable stacks count as freshly produced now.
     */
    OptionalLong expiresAt(ItemStack item);

    void setExpiresAt(ItemStack item, long expiresAt);

    long round(long expiresAt);
}
