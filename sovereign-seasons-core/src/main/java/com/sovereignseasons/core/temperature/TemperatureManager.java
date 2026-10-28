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
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

/**
 * Bukkit adapter for {@link SeasonalTemperatureCalculator}.
 */
public class TemperatureManager implements TemperatureService {
    private static final double DEFAULT_BIOME_TEMPERATURE = 0.8;

    private final SeasonService seasonService;
    private final SeasonalTemperatureCalculator calculator;

    public TemperatureManager(SeasonsConfig config, SeasonService seasonService) {
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.calculator = new SeasonalTemperatureCalculator(config);
    }

    @Override
    public double getTemperature(Location location) {
        if (location == null || location.getWorld() == null) {
            return calculator.biomeToCelsius(DEFAULT_BIOME_TEMPERATURE);
        }
        World world = location.getWorld();
        double biomeTemperature = readBiomeTemperature(location);
        CalendarDate date = seasonService.isEnabledInWorld(world) ? seasonService.getDate(world) : null;
        return calculator.compute(biomeTemperature, date);
    }

    @Override
    public TemperatureBand classify(double celsius) {
        return calculator.classify(celsius);
    }

    public SeasonalTemperatureCalculator getCalculator() {
        return calculator;
    }

    private static double readBiomeTemperature(Location location) {
        try {
            World world = location.getWorld();
            // Never force-load chunks synchronously just to read a biome
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                return DEFAULT_BIOME_TEMPERATURE;
            }
            Block block = location.getBlock();
            if (block != null) {
                return block.getTemperature();
            }
        } catch (RuntimeException ignored) {
            // Unloaded chunk or mocked world: fall back to a temperate biome.
        }
        return DEFAULT_BIOME_TEMPERATURE;
    }
}
