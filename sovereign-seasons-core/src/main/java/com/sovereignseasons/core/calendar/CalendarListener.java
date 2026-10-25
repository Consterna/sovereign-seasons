package com.sovereignseasons.core.calendar;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;

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
}
