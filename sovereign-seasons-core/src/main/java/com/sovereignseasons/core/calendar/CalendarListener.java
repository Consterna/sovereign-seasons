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

package com.sovereignseasons.core.calendar;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Objects;

/**
 * Listens for world time skips (sleeping, commands) and ticks to ensure calendar synchronization.
 */
public class CalendarListener implements Listener {
    private final WorldCalendarManager calendarManager;
    private final com.tcoded.folialib.FoliaLib foliaLib;

    public CalendarListener(WorldCalendarManager calendarManager) {
        this(calendarManager, null);
    }

    public CalendarListener(WorldCalendarManager calendarManager, com.tcoded.folialib.FoliaLib foliaLib) {
        this.calendarManager = Objects.requireNonNull(calendarManager, "calendarManager cannot be null");
        this.foliaLib = foliaLib;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.isCancelled()) return;
        World world = event.getWorld();
        calendarManager.tickWorld(world);
        if (foliaLib != null) {
            foliaLib.getImpl().runNextTick(task -> calendarManager.tickWorld(world));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        calendarManager.tickWorld(world);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (event.isCancelled()) return;
        World world = event.getWorld();
        calendarManager.saveData();
        calendarManager.unloadWorld(world.getUID());
    }
}
