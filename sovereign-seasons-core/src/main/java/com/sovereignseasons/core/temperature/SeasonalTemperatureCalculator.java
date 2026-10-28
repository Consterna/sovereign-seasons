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

package com.sovereignseasons.core.temperature;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;

import java.util.Objects;

/**
 * Pure (Bukkit-free) seasonal temperature math.
 * <p>
 * The season offset is interpolated over the season progress so that transitions are continuous:
 * during the first third it blends from the midpoint with the previous season towards the current offset,
 * stays flat during the middle third, and blends towards the midpoint with the next season during the last third.
 */
public class SeasonalTemperatureCalculator {
    private static final double ONE_THIRD = 1.0 / 3.0;
    private static final double TWO_THIRDS = 2.0 / 3.0;

    private final SeasonsConfig config;

    public SeasonalTemperatureCalculator(SeasonsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Converts a vanilla biome temperature (e.g. 0.8 for plains) into degrees Celsius.
     */
    public double biomeToCelsius(double biomeTemperature) {
        if (!Double.isFinite(biomeTemperature)) {
            biomeTemperature = 0.8;
        }
        return biomeTemperature * config.getBiomeTemperatureScale() + config.getBiomeTemperatureOffset();
    }

    /**
     * Seasonal offset in degrees Celsius for the given date. Returns 0 when the date is null.
     */
    public double seasonalOffset(CalendarDate date) {
        if (date == null) {
            return 0.0;
        }
        Season season = date.season();
        double current = config.getSeasonTemperatureOffset(season);
        double progress = date.getSeasonProgressPercent() / 100.0;
        if (!Double.isFinite(progress)) {
            return current;
        }
        progress = Math.max(0.0, Math.min(1.0, progress));

        if (progress < ONE_THIRD) {
            double start = (config.getSeasonTemperatureOffset(season.previous()) + current) / 2.0;
            return lerp(start, current, progress / ONE_THIRD);
        }
        if (progress > TWO_THIRDS) {
            double end = (current + config.getSeasonTemperatureOffset(season.next())) / 2.0;
            return lerp(current, end, (progress - TWO_THIRDS) / ONE_THIRD);
        }
        return current;
    }

    /**
     * Full ambient temperature in degrees Celsius: biome base plus the seasonal offset.
     */
    public double compute(double biomeTemperature, CalendarDate date) {
        double base = biomeToCelsius(biomeTemperature);
        if (!config.isTemperatureEnabled()) {
            return base;
        }
        return base + seasonalOffset(date);
    }

    public TemperatureBand classify(double celsius) {
        if (!Double.isFinite(celsius)) {
            return TemperatureBand.MILD;
        }
        if (celsius < config.getFreezingBelow()) return TemperatureBand.FREEZING;
        if (celsius < config.getColdBelow()) return TemperatureBand.COLD;
        if (celsius < config.getMildBelow()) return TemperatureBand.MILD;
        if (celsius < config.getWarmBelow()) return TemperatureBand.WARM;
        return TemperatureBand.HOT;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
