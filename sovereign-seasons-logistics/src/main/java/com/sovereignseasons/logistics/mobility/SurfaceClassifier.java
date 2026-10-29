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

package com.sovereignseasons.logistics.mobility;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import org.bukkit.Material;

import java.util.Objects;

/**
 * Pure (Bukkit-free) classification of the ground an entity stands on.
 * <p>
 * The "ground" block is the one at {@code floor(y - 0.01)} below the entity's feet: for full blocks it is the
 * block underneath, and for partial blocks (snow layers, dirt paths, slabs, farmland) it is the partial block itself.
 */
public class SurfaceClassifier {
    private final LogisticsConfig config;

    public SurfaceClassifier(LogisticsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * @param ground      block the entity stands on (see class docs)
     * @param snowLayers  snow layers of {@code ground} when it is a snow layer block (1-8), otherwise ignored
     * @param underGround block directly below {@code ground} (used to find roads under thin snow)
     * @param rainingHere whether rain (not snow) is falling on this exact spot
     * @param season      current season, or null if seasons are disabled in the world
     */
    public SurfaceType classify(Material ground, int snowLayers, Material underGround, boolean rainingHere, Season season) {
        if (ground == null) {
            return SurfaceType.NORMAL;
        }

        if (ground == Material.SNOW) {
            int layers = Math.max(1, Math.min(8, snowLayers));
            // Thin snow over a road does not matter; deep snow must be cleared first
            if (config.isRoadsEnabled() && underGround != null && config.getRoadMaterials().contains(underGround)
                    && layers <= config.getRoadMaxSnowLayers()) {
                return SurfaceType.ROAD;
            }
            if (!config.isSnowEnabled()) {
                return SurfaceType.NORMAL;
            }
            return layers >= config.getDeepSnowLayers() ? SurfaceType.DEEP_SNOW : SurfaceType.SNOW;
        }
        if (ground == Material.SNOW_BLOCK || ground == Material.POWDER_SNOW) {
            return config.isSnowEnabled() ? SurfaceType.DEEP_SNOW : SurfaceType.NORMAL;
        }

        if (config.isRoadsEnabled() && config.getRoadMaterials().contains(ground)) {
            return SurfaceType.ROAD;
        }

        if (config.isMudEnabled()) {
            if (ground == Material.MUD) {
                return SurfaceType.MUD;
            }
            if (rainingHere && season != null && config.getMudSeasons().contains(season) && config.getMudMaterials().contains(ground)) {
                return SurfaceType.MUD;
            }
        }
        return SurfaceType.NORMAL;
    }
}
