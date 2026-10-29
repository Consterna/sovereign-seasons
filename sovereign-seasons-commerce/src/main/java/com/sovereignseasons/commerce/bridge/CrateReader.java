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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Read access to Logistics crates. Free of Logistics types so Commerce loads without Logistics installed;
 * the Logistics-backed implementation lives in {@link LogisticsCrateReader}.
 */
public interface CrateReader {

    /**
     * Contents of each crate in a stack.
     */
    record Crate(Material content, int count) {}

    Optional<Crate> read(ItemStack item);

    /**
     * Items one crate of the stack would unpack into right now (rotten flesh if the food expired inside).
     */
    Optional<ItemStack> preview(ItemStack crate);
}
