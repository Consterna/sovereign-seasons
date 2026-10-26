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

import com.sovereignseasons.api.Season;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when a world transitions from one season to another.
 */
public class SeasonChangeEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final Season previousSeason;
    private Season newSeason;
    private final int year;
    private boolean cancelled;

    public SeasonChangeEvent(@NotNull World world, @NotNull Season previousSeason, @NotNull Season newSeason, int year) {
        this.world = Objects.requireNonNull(world, "world cannot be null");
        this.previousSeason = Objects.requireNonNull(previousSeason, "previousSeason cannot be null");
        this.newSeason = Objects.requireNonNull(newSeason, "newSeason cannot be null");
        this.year = year;
    }

    @NotNull
    public World getWorld() {
        return world;
    }

    @NotNull
    public Season getPreviousSeason() {
        return previousSeason;
    }

    @NotNull
    public Season getNewSeason() {
        return newSeason;
    }

    public void setNewSeason(@NotNull Season newSeason) {
        this.newSeason = Objects.requireNonNull(newSeason, "newSeason cannot be null");
    }

    public int getYear() {
        return year;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
