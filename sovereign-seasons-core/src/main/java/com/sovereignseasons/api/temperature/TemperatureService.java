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

package com.sovereignseasons.api.temperature;

import org.bukkit.Location;

/**
 * Service providing seasonal ambient temperature (in degrees Celsius) for world locations.
 * Combines the biome base temperature (altitude adjusted) with a smoothly interpolated season offset.
 * Must be called from the thread owning the location's region (main thread on Paper, region thread on Folia).
 */
public interface TemperatureService {

    /**
     * Gets the ambient temperature in degrees Celsius at the given location.
     * Returns the configured neutral temperature if the location or its world is null.
     */
    double getTemperature(Location location);

    /**
     * Classifies a raw temperature in degrees Celsius using the configured band thresholds.
     */
    TemperatureBand classify(double celsius);

    /**
     * Gets the qualitative temperature band at the given location.
     */
    default TemperatureBand getBand(Location location) {
        return classify(getTemperature(location));
    }
}
