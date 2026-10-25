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
