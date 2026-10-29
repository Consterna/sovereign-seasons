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

package com.sovereignseasons.commerce.bulk;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.commerce.bridge.CrateReader;
import com.sovereignseasons.commerce.bridge.FoodFreshness;
import com.sovereignseasons.commerce.config.CommerceConfig;
import com.sovereignseasons.commerce.market.CommerceStats;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Sneak + right-click a villager holding Logistics crates to sell them wholesale, at the villager's own price for
 * that crop plus the seasonal value and a bulk bonus. Each villager buys a limited number of crates per day.
 */
public class BulkSaleListener implements Listener {
    private static final long TICKS_PER_DAY = 24_000L;

    private final CommerceConfig config;
    private final SeasonService seasons;
    private final CrateReader crates;
    private final FoodFreshness freshness;
    private final CommerceStats stats;
    private final NamespacedKey dayKey;
    private final NamespacedKey soldKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BulkSaleListener(Plugin plugin, CommerceConfig config, SeasonService seasons, CrateReader crates,
                            FoodFreshness freshness, CommerceStats stats) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasons = Objects.requireNonNull(seasons, "seasons cannot be null");
        this.crates = Objects.requireNonNull(crates, "crates cannot be null");
        this.freshness = freshness;
        this.stats = Objects.requireNonNull(stats, "stats cannot be null");
        this.dayKey = new NamespacedKey(plugin, "bulk_day");
        this.soldKey = new NamespacedKey(plugin, "bulk_sold");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!config.isBulkEnabled() || event.getHand() != EquipmentSlot.HAND
                || !(event.getRightClicked() instanceof Villager villager)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Optional<CrateReader.Crate> crate = crates.read(hand);
        if (crate.isEmpty()) {
            return;
        }
        event.setCancelled(true); // never open the trade screen with a crate sale
        CrateReader.Crate contents = crate.get();
        String name = displayName(contents.content());

        if (villager.isTrading()) {
            notice(player, "<yellow>Este aldeano está ocupado comerciando.</yellow>");
            return;
        }
        OptionalDouble unitsPerEmerald = unitsPerEmerald(villager.getRecipes(), contents.content());
        if (unitsPerEmerald.isEmpty()) {
            notice(player, "<yellow>Este aldeano no compra " + name + ".</yellow>");
            return;
        }
        Optional<ItemStack> unpacked = crates.preview(hand);
        if (unpacked.isPresent() && (unpacked.get().getType() == Material.ROTTEN_FLESH
                || (freshness != null && config.isRefuseSpoilingFood() && freshness.isSpoiling(unpacked.get())))) {
            reject(player, villager, "<red>El aldeano no quiere cajas de comida que se está pudriendo.</red>");
            return;
        }

        // The day of the seasonal calendar: rewinding the clock or a custom day length cannot reset the limit
        CalendarDate date = seasons.isEnabledInWorld(villager.getWorld()) ? seasons.getDate(villager.getWorld()) : null;
        long today = date != null ? date.totalDaysElapsed() : villager.getWorld().getFullTime() / TICKS_PER_DAY;
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        Long storedDay = pdc.get(dayKey, PersistentDataType.LONG);
        Integer sold = pdc.get(soldKey, PersistentDataType.INTEGER);
        int left = BulkSaleModel.cratesLeft(config.getBulkDailyLimit(), storedDay != null ? storedDay : Long.MIN_VALUE,
                sold != null ? sold : 0, today);
        if (left <= 0) {
            reject(player, villager, "<yellow>Este aldeano ya ha comprado suficientes cajas hoy. Vuelve mañana.</yellow>");
            return;
        }

        double value = seasons.isEnabledInWorld(villager.getWorld())
                ? config.getProductValues().value(contents.content(), seasons.getSeason(villager.getWorld()))
                : 1.0;
        value = Math.max(config.getMinValue(), Math.min(config.getMaxValue(), value));
        BulkSaleModel.Quote quote = BulkSaleModel.quote(contents.count(), hand.getAmount(), left,
                unitsPerEmerald.getAsDouble(), value, config.getBulkBonus());
        if (!quote.isSale()) {
            notice(player, "<yellow>Necesitas más cajas de " + name + " para cobrar una esmeralda.</yellow>");
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - quote.cratesUsed());
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        }
        giveEmeralds(player, quote.emeralds());
        int soldToday = (storedDay != null && storedDay == today && sold != null ? sold : 0) + quote.cratesUsed();
        pdc.set(dayKey, PersistentDataType.LONG, today);
        pdc.set(soldKey, PersistentDataType.INTEGER, soldToday);
        stats.addBulkSale(quote.cratesUsed(), quote.emeralds());
        player.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        notice(player, "<green>Vendidas " + quote.cratesUsed() + " cajas de " + name + " por " + quote.emeralds() + " esmeraldas.</green>");
    }

    /**
     * Units the villager asks for one emerald in its own trade for this crop, or empty if it does not buy it.
     */
    static OptionalDouble unitsPerEmerald(List<MerchantRecipe> recipes, Material crop) {
        for (MerchantRecipe recipe : recipes) {
            List<ItemStack> ingredients = recipe.getIngredients();
            ItemStack result = recipe.getResult();
            if (ingredients.isEmpty() || ingredients.get(0) == null || result.getType() != Material.EMERALD || result.getAmount() <= 0) {
                continue;
            }
            ItemStack first = ingredients.get(0);
            if (first.getType() == crop && first.getAmount() > 0) {
                return OptionalDouble.of((double) first.getAmount() / result.getAmount());
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Gives emeralds in stacks of at most 64: a leftover of {@code addItem} keeps its full amount, and dropping a
     * stack bigger than the item allows breaks item serialization on 1.20.5+.
     */
    private static void giveEmeralds(Player player, int emeralds) {
        int maxStack = Math.max(1, Material.EMERALD.getMaxStackSize());
        for (int left = emeralds; left > 0; left -= maxStack) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(Material.EMERALD, Math.min(left, maxStack)));
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    private void reject(Player player, Villager villager, String message) {
        player.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
        notice(player, message);
    }

    private void notice(Player player, String message) {
        player.sendActionBar(miniMessage.deserialize(message));
    }

    private static String displayName(Material material) {
        String raw = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return switch (material.name()) {
            case "CARROT" -> "zanahorias";
            case "POTATO" -> "patatas";
            case "BEETROOT" -> "remolachas";
            case "APPLE" -> "manzanas";
            case "SWEET_BERRIES" -> "bayas dulces";
            case "GLOW_BERRIES" -> "bayas luminosas";
            case "MELON_SLICE" -> "rodajas de melón";
            case "COCOA_BEANS" -> "granos de cacao";
            case "SUGAR_CANE" -> "caña de azúcar";
            default -> raw;
        };
    }
}
