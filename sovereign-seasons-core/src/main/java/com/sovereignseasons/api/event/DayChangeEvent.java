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

package com.sovereignseasons.api.event;

import com.sovereignseasons.api.CalendarDate;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when an in-game day rolls over in a world.
 */
public class DayChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final CalendarDate previousDate;
    private final CalendarDate newDate;

    public DayChangeEvent(@NotNull World world, @NotNull CalendarDate previousDate, @NotNull CalendarDate newDate) {
        this.world = Objects.requireNonNull(world, "world cannot be null");
        this.previousDate = Objects.requireNonNull(previousDate, "previousDate cannot be null");
        this.newDate = Objects.requireNonNull(newDate, "newDate cannot be null");
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    @NotNull
    public CalendarDate getPreviousDate() {
        return previousDate;
    }

    @NotNull
    public CalendarDate getNewDate() {
        return newDate;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
