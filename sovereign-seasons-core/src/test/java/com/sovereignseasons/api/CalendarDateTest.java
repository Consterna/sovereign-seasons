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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalendarDateTest {

    @Test
    @DisplayName("SubSeason calculation divides season into Early, Mid, and Late")
    void shouldCalculateSubSeason() {
        int daysPerSeason = 7;
        assertThat(SubSeason.calculate(1, daysPerSeason)).isEqualTo(SubSeason.EARLY);
        assertThat(SubSeason.calculate(2, daysPerSeason)).isEqualTo(SubSeason.EARLY);
        assertThat(SubSeason.calculate(3, daysPerSeason)).isEqualTo(SubSeason.MID);
        assertThat(SubSeason.calculate(4, daysPerSeason)).isEqualTo(SubSeason.MID);
        assertThat(SubSeason.calculate(5, daysPerSeason)).isEqualTo(SubSeason.MID);
        assertThat(SubSeason.calculate(6, daysPerSeason)).isEqualTo(SubSeason.LATE);
        assertThat(SubSeason.calculate(7, daysPerSeason)).isEqualTo(SubSeason.LATE);
    }

    @Test
    @DisplayName("CalendarDate calculates remaining days and progress percentage accurately")
    void shouldCalculateRemainingDaysAndProgress() {
        CalendarDate date = new CalendarDate(
                Season.SPRING,
                SubSeason.MID,
                4,
                7,
                3,
                1,
                12000L // Halfway through day 4
        );

        assertThat(date.getRemainingDaysInSeason()).isEqualTo(3);
        // Day 4 with 12000 ticks: (3 + 0.5) / 7 = 3.5 / 7 = 50.0%
        assertThat(date.getSeasonProgressPercent()).isCloseTo(50.0, within(0.01));
        assertThat(date.toFormattedComponent()).isNotNull();
    }
}
