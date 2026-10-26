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
        Objects.requireNonNull(replacementMaterial, "replacementMaterial cannot be null");
        this.replacementMaterial = replacementMaterial.isBlock() ? replacementMaterial : Material.DEAD_BUSH;
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
        Objects.requireNonNull(replacementMaterial, "replacementMaterial cannot be null");
        if (!replacementMaterial.isBlock()) {
            throw new IllegalArgumentException("replacementMaterial must be a block, but got: " + replacementMaterial);
        }
        this.replacementMaterial = replacementMaterial;
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
