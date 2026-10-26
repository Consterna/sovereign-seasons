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
