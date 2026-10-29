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
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Packing (sneak + right-click in the air with a packable crop) and unpacking (right-click with a crate).
 * Interactions are used instead of crafting recipes so automated crafters can never create stateless crates
 * that would "refresh" spoiling food.
 */
public class CrateListener implements Listener {
    private final LogisticsConfig config;
    private final CrateItems crates;
    private final FreshnessBridge freshness;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CrateListener(LogisticsConfig config, CrateItems crates, FreshnessBridge freshness) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.crates = Objects.requireNonNull(crates, "crates cannot be null");
        this.freshness = freshness;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!config.isCratesEnabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        Optional<CrateItems.Contents> contents = crates.read(hand);
        boolean canPack = config.getPackableMaterials().contains(hand.getType()) && hand.getAmount() >= config.getItemsPerCrate();
        Block clicked = event.getClickedBlock();
        boolean interactable = clicked != null && clicked.getType().isInteractable();
        switch (CrateRules.decide(contents.isPresent(), canPack, action == Action.RIGHT_CLICK_AIR, player.isSneaking(), interactable)) {
            case UNPACK -> {
                event.setCancelled(true); // never place the barrel
                unpack(player, hand, contents.get());
            }
            case PACK -> {
                event.setCancelled(true);
                pack(player, hand);
            }
            case IGNORE -> {
                // vanilla behaviour (open the clicked chest, plant the crop...)
            }
        }
    }

    private void pack(Player player, ItemStack hand) {
        int perCrate = config.getItemsPerCrate();
        Material content = hand.getType();
        OptionalLong value = OptionalLong.empty();
        if (freshness != null && freshness.isPerishable(content)) {
            OptionalLong expiresAt = freshness.expiresAt(hand);
            if (expiresAt.isPresent()) {
                long packed = CrateMath.packedValue(expiresAt.getAsLong(), freshness.now(), config.getPackedDecayRate());
                value = OptionalLong.of(freshness.round(packed));
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - perCrate);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        give(player, crates.create(content, perCrate, value, 1));
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, SoundCategory.PLAYERS, 0.8f, 1.1f);
        player.sendActionBar(miniMessage.deserialize("<gold>Empaquetado: " + perCrate + " " + CrateItems.displayName(content) + "</gold>"));
    }

    private void unpack(Player player, ItemStack hand, CrateItems.Contents contents) {
        ItemStack items = new ItemStack(contents.content(), contents.count());
        if (freshness != null && contents.packedValue().isPresent() && freshness.isPerishable(contents.content())) {
            long now = freshness.now();
            long expiresAt = freshness.round(CrateMath.unpackedExpiresAt(contents.packedValue().getAsLong(), now, config.getPackedDecayRate()));
            if (CrateRules.unpackAsRottenFlesh(expiresAt, now, freshness.spoilsToRottenFlesh())) {
                items = new ItemStack(Material.ROTTEN_FLESH, contents.count());
            } else {
                freshness.setExpiresAt(items, expiresAt);
            }
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        give(player, items);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.PLAYERS, 0.8f, 1.1f);
    }

    private static void give(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        Map<Integer, ItemStack> leftover = inventory.addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (crates.isCrate(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFuel(FurnaceBurnEvent event) {
        if (crates.isCrate(event.getFuel())) {
            event.setCancelled(true); // burning a crate would destroy its contents
        }
    }
}
