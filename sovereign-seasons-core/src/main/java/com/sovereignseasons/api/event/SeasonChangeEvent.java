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
