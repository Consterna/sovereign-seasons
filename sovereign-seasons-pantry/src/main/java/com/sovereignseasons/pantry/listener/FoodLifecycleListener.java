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

package com.sovereignseasons.pantry.listener;

import com.sovereignseasons.pantry.cellar.CellarDetector;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.FoodDecayService;
import com.sovereignseasons.pantry.food.FreshnessStage;
import com.sovereignseasons.pantry.storage.StorageTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Hooks the food decay model into the item lifecycle:
 * tagging at creation, lazy evaluation when storage is observed, smoking and consumption penalties.
 */
public class FoodLifecycleListener implements Listener {
    private static final Set<InventoryType> TRACKED_CONTAINERS = EnumSet.of(
            InventoryType.CHEST,
            InventoryType.BARREL,
            InventoryType.SHULKER_BOX,
            InventoryType.DISPENSER,
            InventoryType.DROPPER,
            InventoryType.HOPPER
    );
    private static final int FURNACE_RESULT_SLOT = 2;

    private final PantryConfig config;
    private final FoodDecayService decayService;
    private final StorageTracker tracker;
    private final CellarDetector cellarDetector;

    public FoodLifecycleListener(PantryConfig config, FoodDecayService decayService, StorageTracker tracker, CellarDetector cellarDetector) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.decayService = Objects.requireNonNull(decayService, "decayService cannot be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker cannot be null");
        this.cellarDetector = Objects.requireNonNull(cellarDetector, "cellarDetector cannot be null");
    }

    // ---------------------------------------------------------------- evaluation

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        if (!config.isDecayEnabled() || !TRACKED_CONTAINERS.contains(inventory.getType())) {
            return;
        }
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) {
            return; // virtual inventories (menus from other plugins, minecart chests without block)
        }
        tracker.evaluateContainer(inventory, location, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (config.isDecayEnabled()) {
            tracker.evaluatePlayer(event.getPlayer(), false);
        }
    }

    // ---------------------------------------------------------------- tagging at creation

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (decayService.tagIfUntracked(stack, tracker.now())) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && decayService.tagIfUntracked(result, tracker.now())) {
            event.getInventory().setResult(result);
        }
    }

    /**
     * Furnace outputs are tagged when they are taken out, never inside the output slot:
     * a tagged output would no longer match the vanilla recipe result and the furnace would stall.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceResultClick(InventoryClickEvent event) {
        if (!(event.getClickedInventory() instanceof FurnaceInventory furnace) || event.getSlot() != FURNACE_RESULT_SLOT) {
            return;
        }
        ItemStack result = furnace.getItem(FURNACE_RESULT_SLOT);
        if (result != null && tagFurnaceOutput(result, furnace.getType())) {
            furnace.setItem(FURNACE_RESULT_SLOT, result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperExtract(InventoryMoveItemEvent event) {
        if (!(event.getSource() instanceof FurnaceInventory furnace)) {
            return;
        }
        ItemStack moved = event.getItem();
        if (tagFurnaceOutput(moved, furnace.getType())) {
            event.setItem(moved);
        }
    }

    private boolean tagFurnaceOutput(ItemStack item, InventoryType furnaceType) {
        long now = tracker.now();
        if (furnaceType == InventoryType.SMOKER) {
            return decayService.markSmoked(item, now);
        }
        return decayService.tagIfUntracked(item, now);
    }

    // ---------------------------------------------------------------- consumption

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!config.isDecayEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        FreshnessStage stage = decayService.stageOf(event.getItem(), tracker.now());
        int seconds = switch (stage) {
            case STALE -> config.getStaleHungerSeconds();
            case SPOILING, SPOILED -> config.getSpoilingHungerSeconds();
            default -> 0;
        };
        if (seconds > 0) {
            int amplifier = stage == FreshnessStage.STALE ? 0 : 1;
            player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, seconds * 20, amplifier));
        }
    }

    // ---------------------------------------------------------------- cache & clock maintenance

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        cellarDetector.invalidateAround(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location location = event.getBlock().getLocation();
        cellarDetector.invalidateAround(location);
        Material type = event.getBlock().getType();
        if (type.isBlock() && isContainerBlock(event.getBlock().getState(false))) {
            tracker.forgetContainer(location);
        }
    }

    private static boolean isContainerBlock(BlockState state) {
        return state instanceof Container;
    }
}
