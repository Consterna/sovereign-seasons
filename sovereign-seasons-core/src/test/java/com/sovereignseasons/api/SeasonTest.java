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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonTest {

    @Test
    @DisplayName("Seasons cycle in the correct natural order")
    void shouldCycleSeasonsInOrder() {
        assertThat(Season.SPRING.next()).isEqualTo(Season.SUMMER);
        assertThat(Season.SUMMER.next()).isEqualTo(Season.AUTUMN);
        assertThat(Season.AUTUMN.next()).isEqualTo(Season.WINTER);
        assertThat(Season.WINTER.next()).isEqualTo(Season.SPRING);

        assertThat(Season.SPRING.previous()).isEqualTo(Season.WINTER);
        assertThat(Season.WINTER.previous()).isEqualTo(Season.AUTUMN);
        assertThat(Season.AUTUMN.previous()).isEqualTo(Season.SUMMER);
        assertThat(Season.SUMMER.previous()).isEqualTo(Season.SPRING);
    }

    @ParameterizedTest
    @CsvSource({
            "spring, SPRING",
            "SPRING, SPRING",
            "primavera, SPRING",
            "summer, SUMMER",
            "verano, SUMMER",
            "autumn, AUTUMN",
            "fall, AUTUMN",
            "otono, AUTUMN",
            "otoño, AUTUMN",
            "winter, WINTER",
            "invierno, WINTER"
    })
    @DisplayName("Parses season names in English and Spanish with accents")
    void shouldParseSeasonNames(String input, Season expected) {
        assertThat(Season.fromString(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Returns null for invalid season names")
    void shouldReturnNullForInvalidSeason() {
        assertThat(Season.fromString("invalid")).isNull();
        assertThat(Season.fromString("")).isNull();
        assertThat(Season.fromString(null)).isNull();
    }

    @Test
    @DisplayName("isWinter returns true only for WINTER")
    void shouldIdentifyWinter() {
        assertThat(Season.WINTER.isWinter()).isTrue();
        assertThat(Season.SPRING.isWinter()).isFalse();
        assertThat(Season.SUMMER.isWinter()).isFalse();
        assertThat(Season.AUTUMN.isWinter()).isFalse();
    }

    @Test
    @DisplayName("Generates non-null MiniMessage component for each season")
    void shouldGenerateComponents() {
        for (Season season : Season.values()) {
            assertThat(season.asComponent()).isNotNull();
            assertThat(season.getDisplayName()).isNotBlank();
            assertThat(season.getColor()).isNotNull();
        }
    }
}
