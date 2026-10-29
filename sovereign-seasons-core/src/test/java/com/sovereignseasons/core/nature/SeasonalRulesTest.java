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
import org.bukkit.event.weather.WeatherChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonalRulesTest {
    private SeasonsConfig config;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
    }

    @Test
    @DisplayName("Summers are drier: half of natural rain starts are rejected")
    void summerShouldBeDrier() {
        assertThat(SeasonalRules.rainAllowed(config, Season.SUMMER, 0.49)).isTrue();
        assertThat(SeasonalRules.rainAllowed(config, Season.SUMMER, 0.51)).isFalse();
        assertThat(SeasonalRules.rainAllowed(config, Season.SPRING, 0.999)).isTrue();
        assertThat(SeasonalRules.rainAllowed(config, Season.WINTER, 0.85)).isFalse();
        assertThat(SeasonalRules.rainAllowed(config, null, 0.999)).isTrue();
    }

    @Test
    @DisplayName("Only natural (or unknown-cause) rain starts are filtered")
    void onlyNaturalRainIsFiltered() {
        assertThat(SeasonalRules.isFilteredRainCause(WeatherChangeEvent.Cause.NATURAL)).isTrue();
        assertThat(SeasonalRules.isFilteredRainCause(WeatherChangeEvent.Cause.UNKNOWN)).isTrue();
        assertThat(SeasonalRules.isFilteredRainCause(WeatherChangeEvent.Cause.COMMAND)).isFalse();
        assertThat(SeasonalRules.isFilteredRainCause(WeatherChangeEvent.Cause.PLUGIN)).isFalse();
        assertThat(SeasonalRules.isFilteredRainCause(WeatherChangeEvent.Cause.SLEEP)).isFalse();
        assertThat(SeasonalRules.isFilteredRainCause(null)).isFalse();
    }

    @Test
    @DisplayName("Twins only happen in spring")
    void twinsOnlyInSpring() {
        assertThat(SeasonalRules.twins(config, Season.SPRING, 0.1)).isTrue();
        assertThat(SeasonalRules.twins(config, Season.SPRING, 0.3)).isFalse();
        assertThat(SeasonalRules.twins(config, Season.SUMMER, 0.0)).isFalse();
        config.setSpringTwinChance(0.0);
        assertThat(SeasonalRules.twins(config, Season.SPRING, 0.0)).isFalse();
    }

    @Test
    @DisplayName("Strays replace skeletons only in cold winter climates")
    void straysInColdWinter() {
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.WINTER, TemperatureBand.COLD, 0.1)).isEqualTo(EntityType.STRAY);
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.WINTER, TemperatureBand.FREEZING, 0.1)).isEqualTo(EntityType.STRAY);
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.WINTER, TemperatureBand.MILD, 0.1)).isNull();
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.AUTUMN, TemperatureBand.FREEZING, 0.1)).isNull();
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.WINTER, TemperatureBand.COLD, 0.9)).isNull();
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.SKELETON, Season.WINTER, null, 0.1)).isNull();
    }

    @Test
    @DisplayName("Husks replace zombies only in hot summer climates")
    void husksInHotSummer() {
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.ZOMBIE, Season.SUMMER, TemperatureBand.HOT, 0.1)).isEqualTo(EntityType.HUSK);
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.ZOMBIE, Season.SUMMER, TemperatureBand.WARM, 0.1)).isNull();
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.ZOMBIE, Season.WINTER, TemperatureBand.HOT, 0.1)).isNull();
        assertThat(SeasonalRules.seasonalVariant(config, EntityType.CREEPER, Season.SUMMER, TemperatureBand.HOT, 0.0)).isNull();
    }
}
