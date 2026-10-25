package com.sovereignseasons.api.calendar;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import org.bukkit.World;

import java.util.Optional;

/**
 * Service providing calendar and season state for worlds.
 */
public interface SeasonService {

    /**
     * Gets the current calendar date for the specified world.
     */
    CalendarDate getDate(World world);

    /**
     * Gets the current active season for the specified world.
     */
    default Season getSeason(World world) {
        CalendarDate date = getDate(world);
        return date != null ? date.season() : Season.SPRING;
    }

    /**
     * Checks if the specified world is currently experiencing winter.
     */
    default boolean isWinter(World world) {
        return getSeason(world).isWinter();
    }

    /**
     * Sets the active season for the specified world immediately.
     * Fires SeasonChangeEvent.
     */
    void setSeason(World world, Season season);

    /**
     * Sets the day of the current season for the specified world.
     * Fires DayChangeEvent.
     */
    void setDayOfSeason(World world, int day);

    /**
     * Checks if seasons are enabled in the specified world.
     */
    boolean isEnabledInWorld(World world);
}
