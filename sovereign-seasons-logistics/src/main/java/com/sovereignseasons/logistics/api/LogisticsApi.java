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


package com.sovereignseasons.logistics.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Public API of Sovereign Seasons Logistics for other plugins, registered in Bukkit's ServicesManager.
 */
public interface LogisticsApi {

    /**
     * Contents of a crate item.
     *
     * @param content packed material
     * @param count   items in each crate of the stack
     */
    record CrateInfo(Material content, int count) {}

    /**
     * Contents of a crate item, or empty if the item is not a crate.
     */
    Optional<CrateInfo> readCrate(ItemStack item);

    /**
     * Items one crate of the stack would unpack into right now, freshness included (rotten flesh if the food
     * expired inside), or empty if the item is not a crate. The crate itself is not modified.
     */
    Optional<ItemStack> previewContents(ItemStack crate);
}
