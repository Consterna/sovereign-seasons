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
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SeasonalTemperatureCalculatorTest {
    private static final int DAYS = 9;

    private SeasonsConfig config;
    private SeasonalTemperatureCalculator calculator;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        calculator = new SeasonalTemperatureCalculator(config);
    }

    private static CalendarDate date(Season season, int day, long timeOfDay) {
        return new CalendarDate(season, SubSeason.calculate(day, DAYS), day, DAYS, 0, 1, timeOfDay);
    }

    @Test
    @DisplayName("Converts vanilla biome temperatures to Celsius with the default scale")
    void shouldConvertBiomeTemperature() {
        assertThat(calculator.biomeToCelsius(0.8)).isCloseTo(15.0, within(1e-9));
        assertThat(calculator.biomeToCelsius(2.0)).isCloseTo(45.0, within(1e-9));
        assertThat(calculator.biomeToCelsius(Double.NaN)).isCloseTo(15.0, within(1e-9));
    }

    @Test
    @DisplayName("Mid-season uses the exact configured offset for every season")
    void shouldUseExactOffsetMidSeason() {
        assertThat(calculator.seasonalOffset(date(Season.SPRING, 5, 0))).isCloseTo(0.0, within(1e-9));
        assertThat(calculator.seasonalOffset(date(Season.SUMMER, 5, 0))).isCloseTo(8.0, within(1e-9));
        assertThat(calculator.seasonalOffset(date(Season.AUTUMN, 5, 0))).isCloseTo(-2.0, within(1e-9));
        assertThat(calculator.seasonalOffset(date(Season.WINTER, 5, 0))).isCloseTo(-12.0, within(1e-9));
    }

    @Test
    @DisplayName("Season boundaries are continuous (end of one season equals start of the next)")
    void shouldBeContinuousAcrossBoundaries() {
        for (Season season : Season.values()) {
            double endOfSeason = calculator.seasonalOffset(date(season, DAYS, 23_999));
            double startOfNext = calculator.seasonalOffset(date(season.next(), 1, 0));
            assertThat(endOfSeason).isCloseTo(startOfNext, within(0.01));
        }
        // Start of spring is the midpoint between winter (-12) and spring (0)
        assertThat(calculator.seasonalOffset(date(Season.SPRING, 1, 0))).isCloseTo(-6.0, within(1e-9));
    }

    @Test
    @DisplayName("Offset evolves monotonically from early winter towards mid winter")
    void shouldInterpolateEarlySeason() {
        double day1 = calculator.seasonalOffset(date(Season.WINTER, 1, 0));
        double day2 = calculator.seasonalOffset(date(Season.WINTER, 2, 0));
        double day3 = calculator.seasonalOffset(date(Season.WINTER, 3, 0));
        assertThat(day1).isGreaterThan(day2);
        assertThat(day2).isGreaterThan(day3);
        assertThat(day3).isGreaterThanOrEqualTo(-12.0);
    }

    @Test
    @DisplayName("Null date yields no seasonal offset and disabled temperature ignores the season")
    void shouldHandleNullDateAndDisabledFeature() {
        assertThat(calculator.seasonalOffset(null)).isZero();
        assertThat(calculator.compute(0.8, null)).isCloseTo(15.0, within(1e-9));

        config.setTemperatureEnabled(false);
        assertThat(calculator.compute(0.8, date(Season.WINTER, 5, 0))).isCloseTo(15.0, within(1e-9));
    }

    @Test
    @DisplayName("Classifies temperatures into bands using inclusive lower bounds")
    void shouldClassifyBands() {
        assertThat(calculator.classify(-0.01)).isEqualTo(TemperatureBand.FREEZING);
        assertThat(calculator.classify(0.0)).isEqualTo(TemperatureBand.COLD);
        assertThat(calculator.classify(10.0)).isEqualTo(TemperatureBand.MILD);
        assertThat(calculator.classify(20.0)).isEqualTo(TemperatureBand.WARM);
        assertThat(calculator.classify(30.0)).isEqualTo(TemperatureBand.HOT);
        assertThat(calculator.classify(Double.NaN)).isEqualTo(TemperatureBand.MILD);
        assertThat(calculator.classify(Double.NEGATIVE_INFINITY)).isEqualTo(TemperatureBand.MILD);
    }

    @Test
    @DisplayName("Plains in mid winter are cold and in mid summer are warm")
    void shouldProduceSensibleSeasonalClimate() {
        assertThat(calculator.classify(calculator.compute(0.8, date(Season.WINTER, 5, 0)))).isEqualTo(TemperatureBand.COLD);
        assertThat(calculator.classify(calculator.compute(0.8, date(Season.SUMMER, 5, 0)))).isEqualTo(TemperatureBand.WARM);
        assertThat(calculator.classify(calculator.compute(0.0, date(Season.WINTER, 5, 0)))).isEqualTo(TemperatureBand.FREEZING);
    }

    @Test
    @DisplayName("Loads temperature section from YAML and rejects non-ascending thresholds")
    void shouldLoadAndValidateTemperatureConfig(@TempDir File tempDir) {
        String yaml = """
                temperature:
                  enabled: true
                  biome-scale: 20.0
                  biome-offset: 0.0
                  season-offsets:
                    winter: -500.0
                    summer: 10.0
                  bands:
                    freezing-below: 15.0
                    cold-below: 5.0
                    mild-below: 20.0
                    warm-below: 30.0
                """;
        SeasonsConfig loaded = new SeasonsConfig(null);
        loaded.load(tempDir, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(loaded.getBiomeTemperatureScale()).isEqualTo(20.0);
        assertThat(loaded.getBiomeTemperatureOffset()).isEqualTo(0.0);
        assertThat(loaded.getSeasonTemperatureOffset(Season.SUMMER)).isEqualTo(10.0);
        assertThat(loaded.getSeasonTemperatureOffset(Season.WINTER)).isEqualTo(-100.0); // clamped
        assertThat(loaded.getSeasonTemperatureOffset(Season.SPRING)).isEqualTo(0.0); // default kept
        // Non-ascending thresholds fall back to defaults
        assertThat(loaded.getFreezingBelow()).isEqualTo(0.0);
        assertThat(loaded.getColdBelow()).isEqualTo(10.0);
        assertThat(loaded.getMildBelow()).isEqualTo(20.0);
        assertThat(loaded.getWarmBelow()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Rejects non-finite and out-of-range values defensively")
    void shouldRejectInvalidValues() {
        config.setBiomeTemperatureScale(Double.NaN);
        config.setSeasonTemperatureOffset(Season.WINTER, Double.POSITIVE_INFINITY);
        config.setSeasonTemperatureOffset(null, 5.0);
        config.setBandThresholds(-10.0, 0.0, 50.0, 150.0);

        assertThat(config.getBiomeTemperatureScale()).isEqualTo(25.0);
        assertThat(config.getSeasonTemperatureOffset(Season.WINTER)).isEqualTo(0.0);
        assertThat(config.getSeasonTemperatureOffset(null)).isEqualTo(0.0);
        assertThat(config.getWarmBelow()).isEqualTo(30.0);
    }

    @Test
    @DisplayName("TemperatureManager falls back safely for null locations, disabled worlds and unloaded chunks")
    void managerShouldFallBackSafely() {
        UUID uid = UUID.randomUUID();
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> uid;
                    case "getName" -> "world";
                    case "isChunkLoaded" -> false;
                    case "hashCode" -> uid.hashCode();
                    case "equals" -> args[0] == proxy;
                    default -> null;
                }
        );
        SeasonService seasons = (SeasonService) Proxy.newProxyInstance(
                SeasonService.class.getClassLoader(),
                new Class<?>[]{SeasonService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isEnabledInWorld" -> true;
                    case "getDate" -> date(Season.WINTER, 5, 0);
                    default -> null;
                }
        );

        TemperatureManager manager = new TemperatureManager(config, seasons);
        assertThat(manager.getTemperature(null)).isCloseTo(15.0, within(1e-9));
        // Unloaded chunk -> temperate biome (15 °C) + mid winter (-12 °C)
        assertThat(manager.getTemperature(new Location(world, 0, 64, 0))).isCloseTo(3.0, within(1e-9));
        assertThat(manager.getBand(new Location(world, 0, 64, 0))).isEqualTo(TemperatureBand.COLD);
    }
}
