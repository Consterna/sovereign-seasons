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

package com.sovereignseasons.api.greenhouse;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Service responsible for greenhouse detection and spatial caching.
 */
public interface GreenhouseService {

    /**
     * Checks if a location meets all requirements to be considered a functional greenhouse.
     */
    boolean isGreenhouse(Location location);

    /**
     * Checks if a block is inside a functional greenhouse.
     */
    default boolean isGreenhouse(Block block) {
        if (block == null) return false;
        return isGreenhouse(block.getLocation());
    }

    /**
     * Performs a full diagnostic evaluation and returns a detailed report.
     */
    GreenhouseReport inspect(Location location);

    /**
     * Invalidates any cached evaluation for the given location.
     */
    void invalidate(Location location);

    /**
     * Invalidates cached evaluations within a spherical/cubic radius around the given location.
     */
    void invalidateRadius(Location location, int radius);

    /**
     * Clears all cached greenhouse evaluations.
     */
    void clearCache();

    /**
     * Returns total number of cache hits since server start or cache reset.
     */
    long getCacheHits();

    /**
     * Returns total number of cache misses since server start or cache reset.
     */
    long getCacheMisses();

    /**
     * Returns the current number of active cached positions in the spatial LRU cache.
     */
    long getCacheSize();
}
