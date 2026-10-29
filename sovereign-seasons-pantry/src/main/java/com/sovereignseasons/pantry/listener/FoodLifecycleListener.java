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
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.concurrent.ThreadLocalRandom;
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
    private final FoliaLib foliaLib;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public FoodLifecycleListener(PantryConfig config, FoodDecayService decayService, StorageTracker tracker, CellarDetector cellarDetector, FoliaLib foliaLib) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.decayService = Objects.requireNonNull(decayService, "decayService cannot be null");
        this.tracker = Objects.requireNonNull(tracker, "tracker cannot be null");
        this.cellarDetector = Objects.requireNonNull(cellarDetector, "cellarDetector cannot be null");
        this.foliaLib = foliaLib;
    }

    /**
     * Evaluates a player's inventory and warns them when food rotted or is about to.
     * Must run on the player's own thread.
     */
    public void evaluatePlayer(Player player) {
        FoodDecayService.EvaluationResult result = tracker.evaluatePlayer(player, false);
        if (!config.isSpoilageWarnings() || player == null || !player.isOnline()) {
            return;
        }
        if (result.spoiled() > 0) {
            player.sendActionBar(miniMessage.deserialize("<red>☠ " + result.spoiled()
                    + (result.spoiled() == 1 ? " pila de comida se ha podrido" : " pilas de comida se han podrido") + " en tu inventario.</red>"));
        } else if (result.spoiling() > 0) {
            player.sendActionBar(miniMessage.deserialize("<yellow>⚠ Parte de la comida de tu inventario se está echando a perder.</yellow>"));
        }
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
            evaluatePlayer(event.getPlayer());
        }
    }

    // ---------------------------------------------------------------- composting rotten food

    /**
     * Rotten flesh is not compostable in vanilla; here it can be used like a low-value compostable.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCompost(PlayerInteractEvent event) {
        if (!config.isCompostEnabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COMPOSTER || !(block.getBlockData() instanceof Levelled composter)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.ROTTEN_FLESH || composter.getLevel() >= composter.getMaximumLevel() - 1) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        Location center = block.getLocation().add(0.5, 0.8, 0.5);
        boolean success = ThreadLocalRandom.current().nextDouble() < config.getCompostChance();
        block.getWorld().playSound(center, success ? Sound.BLOCK_COMPOSTER_FILL_SUCCESS : Sound.BLOCK_COMPOSTER_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (!success) {
            return;
        }
        int newLevel = composter.getLevel() + 1;
        composter.setLevel(newLevel);
        block.setBlockData(composter);
        block.getWorld().spawnParticle(Particle.COMPOSTER, center, 8, 0.25, 0.1, 0.25, 0.0);
        if (newLevel == composter.getMaximumLevel() - 1) {
            // Vanilla turns a full composter into a ready one shortly after; setting data directly skips that tick
            Runnable ready = () -> markComposterReady(block);
            if (foliaLib != null) {
                foliaLib.getImpl().runAtLocationLater(block.getLocation(), ready, 20L);
            } else {
                ready.run();
            }
        }
    }

    private static void markComposterReady(Block block) {
        if (block.getType() != Material.COMPOSTER || !(block.getBlockData() instanceof Levelled composter)) {
            return;
        }
        if (composter.getLevel() == composter.getMaximumLevel() - 1) {
            composter.setLevel(composter.getMaximumLevel());
            block.setBlockData(composter);
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_COMPOSTER_READY, SoundCategory.BLOCKS, 1.0f, 1.0f);
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
