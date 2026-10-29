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

package com.sovereignseasons.logistics.ice;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;

/**
 * Pure rules for seasonal lake freezing and thawing.
 */
public final class FreezeRules {
    private FreezeRules() {}

    /**
     * Whether the climate at a spot is cold enough to keep seasonal ice.
     */
    public static boolean isFreezingClimate(Season season, TemperatureBand band) {
        return season == Season.WINTER && band == TemperatureBand.FREEZING;
    }

    /**
     * Whether a sampled water block should freeze.
     *
     * @param waterSource surface block is a still water source
     * @param skyExposed  nothing above the water (it is the highest block of its column)
     * @param greenhouse  the spot is inside a functional greenhouse (core)
     * @param occupied    an entity is inside the water block (never trap anyone)
     */
    public static boolean shouldFreeze(Season season, TemperatureBand band, boolean waterSource, boolean skyExposed,
                                       boolean greenhouse, boolean occupied) {
        return isFreezingClimate(season, band) && waterSource && skyExposed && !greenhouse && !occupied;
    }

    /**
     * Whether seasonal ice registered at a spot should melt back into water.
     */
    public static boolean shouldThaw(Season season, TemperatureBand band) {
        return !isFreezingClimate(season, band);
    }
}
