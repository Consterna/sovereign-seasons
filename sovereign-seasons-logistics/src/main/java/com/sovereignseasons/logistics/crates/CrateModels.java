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

import java.util.List;

/**
 * Custom model data of crate items, read by the Sovereign Seasons resource pack to draw each crate with its own
 * model (the barrel item model picks a crate model by this number). Without the pack crates look like barrels.
 * <p>
 * {@code ItemMeta#setCustomModelData(Integer)} stores it as the first float of the {@code custom_model_data}
 * component on 1.21.4+ and as the old integer before, and the pack handles both.
 */
public final class CrateModels {
    /** Generic crate (contents without a model of their own). */
    public static final int BASE = 7_301_000;

    /** Crops with their own crate model, in the order of the pack's models: BASE + 1 + index. */
    public static final List<String> CROPS = List.of(
            "CARROT", "POTATO", "BEETROOT", "APPLE", "SWEET_BERRIES", "GLOW_BERRIES", "MELON_SLICE", "COCOA_BEANS", "SUGAR_CANE");

    /** First value after the crate models; the pack maps it and anything above back to the plain barrel. */
    public static final int END = BASE + 1 + CROPS.size();

    private CrateModels() {
    }

    public static int of(Material content) {
        int index = content == null ? -1 : CROPS.indexOf(content.name());
        return index < 0 ? BASE : BASE + 1 + index;
    }
}
