package com.sovereignseasons.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Objects;

/**
 * Immutable value representation of an in-game calendar date.
 */
public record CalendarDate(
        Season season,
        SubSeason subSeason,
        int dayOfSeason,
        int totalDaysPerSeason,
        int totalDaysElapsed,
        int year,
        long timeOfDayTicks
) {
    public CalendarDate {
        Objects.requireNonNull(season, "season cannot be null");
        Objects.requireNonNull(subSeason, "subSeason cannot be null");
    }

    /**
     * Percentage progress of the current season (0.0 to 100.0).
     */
    public double getSeasonProgressPercent() {
        if (totalDaysPerSeason <= 0) return 100.0;
        double daysFraction = (double) (dayOfSeason - 1) / (double) totalDaysPerSeason;
        double timeFraction = (double) timeOfDayTicks / 24000.0 / (double) totalDaysPerSeason;
        return Math.min(100.0, Math.max(0.0, (daysFraction + timeFraction) * 100.0));
    }

    /**
     * Remaining in-game days until the next season.
     */
    public int getRemainingDaysInSeason() {
        return Math.max(0, totalDaysPerSeason - dayOfSeason);
    }

    /**
     * Formats this date as a rich MiniMessage component.
     * Example: "Primavera (Día 3/7, Año 1)"
     */
    public Component toFormattedComponent() {
        String template = String.format(
                "%s <gray>(Día <white>%d</white>/<white>%d</white>, Año <white>%d</white>)</gray>",
                season.getMiniMessageTag(),
                dayOfSeason,
                totalDaysPerSeason,
                year
        );
        return MiniMessage.miniMessage().deserialize(template);
    }
}
