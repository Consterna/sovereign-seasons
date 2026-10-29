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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Climate preferences of a crop: the seasons in which it is fertile and the temperature bands it thrives in.
 * Empty sets mean "no preference" (always fertile / any climate).
 */
public record CropProfile(Set<Season> fertileSeasons, Set<TemperatureBand> idealBands) {
    public CropProfile {
        fertileSeasons = fertileSeasons == null || fertileSeasons.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Season.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(fertileSeasons));
        idealBands = idealBands == null || idealBands.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(TemperatureBand.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(idealBands));
    }

    /**
     * Whether the crop is fertile in the given season. An unknown (null) season never penalizes growth.
     */
    public boolean isFertileIn(Season season) {
        return fertileSeasons.isEmpty() || season == null || fertileSeasons.contains(season);
    }

    /**
     * Number of band steps between {@code band} and the closest ideal band (0 = ideal).
     */
    public int bandDistance(TemperatureBand band) {
        if (idealBands.isEmpty() || band == null) {
            return 0;
        }
        int best = Integer.MAX_VALUE;
        for (TemperatureBand ideal : idealBands) {
            best = Math.min(best, Math.abs(ideal.ordinal() - band.ordinal()));
        }
        return best;
    }
}
