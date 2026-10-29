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

package com.sovereignseasons.logistics.weight;

import com.sovereignseasons.logistics.crates.CrateItems;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;
import java.util.Optional;

/**
 * Bukkit adapter that adds up the weight of the items a player carries.
 */
public class InventoryWeigher {
    private final WeightModel model;
    private final CrateItems crates;

    public InventoryWeigher(WeightModel model, CrateItems crates) {
        this.model = Objects.requireNonNull(model, "model cannot be null");
        this.crates = Objects.requireNonNull(crates, "crates cannot be null");
    }

    /**
     * Total weight of the given contents (player inventory, armor and off-hand).
     */
    public double weigh(ItemStack[] contents) {
        if (contents == null) {
            return 0.0;
        }
        double total = 0.0;
        for (ItemStack item : contents) {
            total += weigh(item);
        }
        return total;
    }

    public double weigh(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return 0.0;
        }
        Optional<CrateItems.Contents> crate = crates.read(item);
        if (crate.isPresent()) {
            return model.crateWeight(crate.get().content(), crate.get().count(), item.getAmount());
        }
        double weight = model.stackWeight(item.getType(), item.getAmount());
        if (item.hasItemMeta()) {
            weight += nestedContents(item.getItemMeta()) * item.getAmount();
        }
        return weight;
    }

    private double nestedContents(ItemMeta meta) {
        double nested = 0.0;
        if (meta instanceof BlockStateMeta stateMeta && stateMeta.hasBlockState()) {
            BlockState state = stateMeta.getBlockState();
            if (state instanceof ShulkerBox shulker) {
                for (ItemStack inside : shulker.getInventory().getContents()) {
                    nested += weigh(inside);
                }
            }
        } else if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack inside : bundle.getItems()) {
                nested += weigh(inside);
            }
        }
        return model.nestedWeight(nested);
    }
}
