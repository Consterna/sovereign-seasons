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

package com.sovereignseasons.api;

/**
 * Sub-division of a season based on elapsed days within that season.
 */
public enum SubSeason {
    EARLY("Temprano", "Early"),
    MID("Medio", "Mid"),
    LATE("Tardío", "Late");

    private final String spanishName;
    private final String englishName;

    SubSeason(String spanishName, String englishName) {
        this.spanishName = spanishName;
        this.englishName = englishName;
    }

    public String getSpanishName() {
        return spanishName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public static SubSeason calculate(int dayOfSeason, int totalDaysPerSeason) {
        if (totalDaysPerSeason <= 1) {
            return MID;
        }
        if (totalDaysPerSeason == 2) {
            return dayOfSeason <= 1 ? EARLY : LATE;
        }

        double oneThird = totalDaysPerSeason / 3.0;
        double twoThirds = (totalDaysPerSeason * 2.0) / 3.0;

        if (dayOfSeason <= Math.round(oneThird)) {
            return EARLY;
        } else if (dayOfSeason <= Math.round(twoThirds)) {
            return MID;
        } else {
            return LATE;
        }
    }
}
