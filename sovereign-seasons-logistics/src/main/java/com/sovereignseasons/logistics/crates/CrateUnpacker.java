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


package com.sovereignseasons.logistics.crates;

import com.sovereignseasons.logistics.config.LogisticsConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/**
 * Turns the contents of one crate back into items, with their freshness when Pantry is linked. Shared by
 * unpacking and by the public API, so other modules always see exactly what unpacking would give.
 */
public final class CrateUnpacker {
    private final LogisticsConfig config;
    private final FreshnessBridge freshness;

    public CrateUnpacker(LogisticsConfig config, FreshnessBridge freshness) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.freshness = freshness;
    }

    /**
     * Items one crate with these contents unpacks into right now (rotten flesh if the food expired inside).
     */
    public ItemStack unpack(CrateItems.Contents contents) {
        ItemStack items = new ItemStack(contents.content(), contents.count());
        if (freshness != null && contents.packedValue().isPresent() && freshness.isPerishable(contents.content())) {
            long now = freshness.now();
            long expiresAt = CrateMath.floorTo(
                    CrateMath.unpackedExpiresAt(contents.packedValue().getAsLong(), now, config.getPackedDecayRate()),
                    freshness.granularity());
            if (CrateRules.unpackAsRottenFlesh(expiresAt, now, freshness.spoilsToRottenFlesh())) {
                return new ItemStack(Material.ROTTEN_FLESH, contents.count());
            }
            freshness.setExpiresAt(items, expiresAt);
        }
        return items;
    }
}
