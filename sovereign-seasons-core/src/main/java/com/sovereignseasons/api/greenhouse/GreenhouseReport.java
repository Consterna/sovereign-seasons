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
