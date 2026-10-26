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
import org.bukkit.Material;

import java.util.Optional;

/**
 * Detailed diagnostic report for greenhouse status evaluation at a specific location.
 */
public record GreenhouseReport(
        Location queryLocation,
        boolean isGreenhouse,
        boolean cached,
        boolean glassCeilingValid,
        Location glassLocation,
        Material glassMaterial,
        boolean heatSourceValid,
        Location heatSourceLocation,
        Material heatSourceMaterial,
        int distanceToCeiling,
        double distanceToHeatSource,
        String failureReason
) {
    public static GreenhouseReport success(
            Location loc,
            boolean cached,
            Location glassLoc,
            Material glassMat,
            int ceilingDist,
            Location heatLoc,
            Material heatMat,
            double heatDist
    ) {
        return new GreenhouseReport(
                loc,
                true,
                cached,
                true,
                glassLoc,
                glassMat,
                true,
                heatLoc,
                heatMat,
                ceilingDist,
                heatDist,
                null
        );
    }

    public static GreenhouseReport failure(
            Location loc,
            boolean cached,
            boolean glassValid,
            Location glassLoc,
            Material glassMat,
            int ceilingDist,
            boolean heatValid,
            Location heatLoc,
            Material heatMat,
            double heatDist,
            String reason
    ) {
        return new GreenhouseReport(
                loc,
                false,
                cached,
                glassValid,
                glassLoc,
                glassMat,
                heatValid,
                heatLoc,
                heatMat,
                ceilingDist,
                heatDist,
                reason
        );
    }
}
