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

package com.sovereignseasons.pantry.cellar;

import org.bukkit.Material;

/**
 * Detailed result of a cellar evaluation.
 *
 * @param cellar        whether the position qualifies as a cold cellar
 * @param fromCache     whether the result came from the spatial cache
 * @param maxSkyLight   highest sky light level around the position (must be 0)
 * @param roofBlocks    solid blocks counted directly above the position
 * @param deep          whether the position is below the configured deep Y level
 * @param iceNearby     whether an ice block was found within the ice radius
 * @param iceMaterial   material of the ice block found, or null
 * @param failureReason human readable reason when not a cellar, or null
 */
public record CellarReport(
        boolean cellar,
        boolean fromCache,
        int maxSkyLight,
        int roofBlocks,
        boolean deep,
        boolean iceNearby,
        Material iceMaterial,
        String failureReason
) {
    public static CellarReport failure(String reason) {
        return new CellarReport(false, false, 15, 0, false, false, null, reason);
    }

    public CellarReport asCached() {
        return new CellarReport(cellar, true, maxSkyLight, roofBlocks, deep, iceNearby, iceMaterial, failureReason);
    }
}
