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

package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;

import java.util.Objects;

/**
 * Pure (Bukkit-free) seasonal growth rules outside of winter.
 * <p>
 * Each natural growth attempt succeeds with probability {@code seasonFactor * climateFactor}.
 * A failed attempt only skips that growth step: crops never die from being out of season.
 * Greenhouses ignore both factors; underground farms ignore the season factor only.
 */
public class CropGrowthModel {
    private final SeasonsConfig config;

    public CropGrowthModel(SeasonsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Probability in [0, 1] that a growth attempt is allowed.
     *
     * @param profile     climate preferences of the crop, or null if the crop has none
     * @param season      current season
     * @param band        ambient temperature band, or null if unknown (climate is then ignored)
     * @param greenhouse  whether the crop is inside a functional greenhouse
     * @param underground whether the crop is in an underground farm
     */
    public double growthChance(CropProfile profile, Season season, TemperatureBand band, boolean greenhouse, boolean underground) {
        if (!config.isSeasonalGrowthEnabled() || profile == null || greenhouse) {
            return 1.0;
        }
        double seasonFactor = underground || profile.isFertileIn(season) ? 1.0 : config.getOutOfSeasonFactor();
        double climateFactor = switch (profile.bandDistance(band)) {
            case 0 -> 1.0;
            case 1 -> config.getAdjacentBandFactor();
            default -> config.getFarBandFactor();
        };
        return Math.max(0.0, Math.min(1.0, seasonFactor * climateFactor));
    }
}
