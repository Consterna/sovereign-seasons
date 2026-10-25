package com.sovereignseasons.api.event;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when a crop in winter fails the wither roll and is about to turn into a dead bush.
 */
public class CropWinterWitherEvent extends BlockEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Material originalMaterial;
    private Material replacementMaterial;
    private boolean cancelled;

    public CropWinterWitherEvent(@NotNull Block theBlock, @NotNull Material replacementMaterial) {
        super(theBlock);
        this.originalMaterial = theBlock.getType();
        this.replacementMaterial = Objects.requireNonNull(replacementMaterial, "replacementMaterial cannot be null");
    }

    @NotNull
    public Material getOriginalMaterial() {
        return originalMaterial;
    }

    @NotNull
    public Material getReplacementMaterial() {
        return replacementMaterial;
    }

    public void setReplacementMaterial(@NotNull Material replacementMaterial) {
        this.replacementMaterial = Objects.requireNonNull(replacementMaterial, "replacementMaterial cannot be null");
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
