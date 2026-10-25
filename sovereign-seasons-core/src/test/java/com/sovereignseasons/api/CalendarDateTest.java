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
