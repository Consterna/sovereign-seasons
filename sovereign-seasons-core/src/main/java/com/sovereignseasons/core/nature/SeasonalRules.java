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

package com.sovereignseasons.core.nature;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.entity.EntityType;

/**
 * Pure decision rules for seasonal weather and fauna. {@code roll} is a uniform random number in [0, 1).
 */
public final class SeasonalRules {
    private SeasonalRules() {}

    public static boolean rainAllowed(SeasonsConfig config, Season season, double roll) {
        return roll < config.getRainChance(season);
    }

    public static boolean twins(SeasonsConfig config, Season season, double roll) {
        return season == Season.SPRING && roll < config.getSpringTwinChance();
    }

    /**
     * Vanilla variant that should replace a natural spawn, or null to keep the original mob.
     * Strays appear in cold winter climates and husks in hot summer climates, as vanilla does in snowy and desert biomes.
     */
    public static EntityType seasonalVariant(SeasonsConfig config, EntityType type, Season season, TemperatureBand band, double roll) {
        if (type == EntityType.SKELETON && season == Season.WINTER && band != null
                && band.isAtMost(TemperatureBand.COLD) && roll < config.getWinterStrayChance()) {
            return EntityType.STRAY;
        }
        if (type == EntityType.ZOMBIE && season == Season.SUMMER && band == TemperatureBand.HOT
                && roll < config.getSummerHuskChance()) {
            return EntityType.HUSK;
        }
        return null;
    }
}
