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

package com.sovereignseasons.commerce.market;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.commerce.bridge.FoodFreshness;
import com.sovereignseasons.commerce.config.CommerceConfig;
import com.sovereignseasons.commerce.pricing.ProductValues;
import com.sovereignseasons.commerce.pricing.TradePricing;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Villager and wandering trader markets: seasonal prices, seasonal fairs and the freshness check at the counter.
 * <p>
 * Prices are adjusted in {@link InventoryOpenEvent}, which vanilla fires after a villager computes its
 * reputation discounts and before the offers are sent to the client. Villagers reset those adjustments when the
 * trade closes; wandering traders do not, so their adjustment is set absolutely and cleared on close.
 */
public class MarketListener implements Listener {
    private final CommerceConfig config;
    private final SeasonService seasons;
    private final FoodFreshness freshness;
    private final CommerceStats stats;
    private final NamespacedKey fairSeasonKey;
    private final NamespacedKey fairCountKey;
    private final NamespacedKey pricedKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    /** Merchant inventory each player has open (read in PlayerTradeEvent without touching InventoryView). */
    private final Map<UUID, MerchantInventory> openMarkets = new ConcurrentHashMap<>();

    public MarketListener(Plugin plugin, CommerceConfig config, SeasonService seasons, FoodFreshness freshness, CommerceStats stats) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasons = Objects.requireNonNull(seasons, "seasons cannot be null");
        this.freshness = freshness;
        this.stats = Objects.requireNonNull(stats, "stats cannot be null");
        this.fairSeasonKey = new NamespacedKey(plugin, "fair_season");
        this.fairCountKey = new NamespacedKey(plugin, "fair_count");
        this.pricedKey = new NamespacedKey(plugin, "seasonal_priced");
    }

    // MONITOR: only adjust once no other plugin can still cancel the trade screen (the event itself is not changed)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory inventory) || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Merchant merchant = inventory.getMerchant();
        if (!(merchant instanceof AbstractVillager trader)) {
            return; // virtual merchants of other plugins are left alone
        }
        openMarkets.put(player.getUniqueId(), inventory);
        if (!seasons.isEnabledInWorld(trader.getWorld())) {
            return;
        }
        Season season = seasons.getSeason(trader.getWorld());
        int fairOffers = 0;
        if (trader instanceof WanderingTrader wanderer) {
            if (config.isFairsEnabled()) {
                fairOffers = stockFair(wanderer, season);
            } else {
                removeFair(wanderer); // fairs turned off: take back the offers added while they were on
            }
        }
        int adjusted = 0;
        if (config.isPricesEnabled()) {
            adjusted = applySeasonalPrices(trader, season, fairOffers);
        } else if (trader instanceof WanderingTrader wanderer) {
            clearWandererPrices(wanderer);
        }
        if (adjusted > 0) {
            stats.addAdjustedOffers(adjusted);
        }
        if (fairOffers > 0) {
            player.sendActionBar(miniMessage.deserialize("<gold>Feria de " + season.getDisplayName() + ":</gold> <yellow>ofertas de temporada</yellow>"));
        } else if (adjusted > 0 && config.isShowPriceNotice()) {
            player.sendActionBar(miniMessage.deserialize("<gray>Precios de</gray> " + season.getMiniMessageTag()));
        }
    }

    /**
     * Adds this season's fair offers to a wandering trader, replacing a previous season's ones.
     *
     * @return number of fair offers the trader currently has (always the last offers of its list)
     */
    private int stockFair(WanderingTrader trader, Season season) {
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        String stocked = pdc.get(fairSeasonKey, PersistentDataType.STRING);
        Integer previous = pdc.get(fairCountKey, PersistentDataType.INTEGER);
        if (season.name().equals(stocked)) {
            return previous != null ? Math.max(0, Math.min(previous, trader.getRecipeCount())) : 0;
        }
        List<MerchantRecipe> recipes = withoutFair(trader);
        List<CommerceConfig.FairOffer> offers = config.getFairOffers(season);
        for (CommerceConfig.FairOffer offer : offers) {
            MerchantRecipe recipe = new MerchantRecipe(new ItemStack(offer.item(), offer.amount()), 0, offer.maxUses(), true, 1, 0.05f);
            recipe.addIngredient(new ItemStack(Material.EMERALD, offer.price()));
            recipes.add(recipe);
        }
        trader.setRecipes(recipes);
        pdc.set(fairSeasonKey, PersistentDataType.STRING, season.name());
        pdc.set(fairCountKey, PersistentDataType.INTEGER, offers.size());
        if (!offers.isEmpty()) {
            stats.addFairStocked();
        }
        return offers.size();
    }

    /**
     * Offers of a wandering trader without the fair offers added by Commerce (always the tail of the list).
     */
    private List<MerchantRecipe> withoutFair(WanderingTrader trader) {
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
        Integer previous = pdc.get(fairCountKey, PersistentDataType.INTEGER);
        if (pdc.has(fairSeasonKey, PersistentDataType.STRING) && previous != null) {
            int remove = Math.max(0, Math.min(previous, recipes.size()));
            recipes.subList(recipes.size() - remove, recipes.size()).clear();
        }
        return recipes;
    }

    private void removeFair(WanderingTrader trader) {
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        if (!pdc.has(fairSeasonKey, PersistentDataType.STRING)) {
            return;
        }
        trader.setRecipes(withoutFair(trader));
        pdc.remove(fairSeasonKey);
        pdc.remove(fairCountKey);
    }

    /**
     * @param fairOffers number of fair offers at the end of the list: the fair sets their price, not the season
     * @return number of offers whose price changed
     */
    private int applySeasonalPrices(AbstractVillager trader, Season season, int fairOffers) {
        ProductValues values = config.getProductValues();
        boolean wanderer = trader instanceof WanderingTrader;
        int adjusted = 0;
        List<MerchantRecipe> recipes = trader.getRecipes();
        int priced = Math.max(0, recipes.size() - fairOffers);
        for (int i = 0; i < priced; i++) {
            MerchantRecipe recipe = recipes.get(i);
            List<ItemStack> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty() || ingredients.get(0) == null) {
                continue;
            }
            ItemStack first = ingredients.get(0);
            Material result = recipe.getResult().getType();
            TradePricing.Direction direction = TradePricing.direction(first.getType(), result);
            Material product = TradePricing.product(first.getType(), result);
            int delta = product == null ? 0 : TradePricing.adjustment(direction, first.getAmount(), first.getMaxStackSize(),
                    values.value(product, season), config.getMinValue(), config.getMaxValue());
            if (wanderer) {
                // Wandering traders keep special prices after trading: set them absolutely, cleared on close
                if (recipe.getSpecialPrice() != delta) {
                    recipe.setSpecialPrice(delta);
                    trader.setRecipe(i, recipe);
                }
            } else if (delta != 0) {
                recipe.setSpecialPrice(recipe.getSpecialPrice() + delta);
                trader.setRecipe(i, recipe);
            }
            if (delta != 0) {
                adjusted++;
            }
        }
        if (wanderer) {
            trader.getPersistentDataContainer().set(pricedKey, PersistentDataType.BYTE, (byte) 1);
        }
        return adjusted;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        openMarkets.remove(event.getPlayer().getUniqueId());
        Inventory inventory = event.getInventory();
        if (inventory instanceof MerchantInventory merchantInventory && merchantInventory.getMerchant() instanceof WanderingTrader trader) {
            clearWandererPrices(trader);
        }
    }

    /**
     * Removes the seasonal adjustment of a wandering trader (vanilla never clears it for them).
     */
    private void clearWandererPrices(WanderingTrader trader) {
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        if (!pdc.has(pricedKey, PersistentDataType.BYTE)) {
            return;
        }
        List<MerchantRecipe> recipes = trader.getRecipes();
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);
            if (recipe.getSpecialPrice() != 0) {
                recipe.setSpecialPrice(0);
                trader.setRecipe(i, recipe);
            }
        }
        pdc.remove(pricedKey);
    }

    /**
     * Called on plugin disable. On shutdown listeners are unregistered before players are kicked, so the close
     * event of an open wandering trader never arrives and its adjustment would be saved with the world.
     */
    public void clearOpenWanderers() {
        for (MerchantInventory inventory : openMarkets.values()) {
            try {
                if (inventory.getMerchant() instanceof WanderingTrader trader) {
                    clearWandererPrices(trader);
                }
            } catch (RuntimeException e) {
                // Folia: the entity belongs to another region thread; its next opening sets the price again
            }
        }
        openMarkets.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        openMarkets.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Villagers refuse food that is rotting: the trade is cancelled before any item is taken.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        if (freshness == null || !config.isRefuseSpoilingFood()) {
            return;
        }
        Player player = event.getPlayer();
        MerchantInventory inventory = openMarkets.get(player.getUniqueId());
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < 2; slot++) {
            ItemStack payment = inventory.getItem(slot);
            if (payment != null && freshness.isSpoiling(payment)) {
                event.setCancelled(true);
                stats.addRefusedTrade();
                player.playSound(event.getVillager().getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
                player.sendActionBar(miniMessage.deserialize("<red>El aldeano rechaza comida que se está pudriendo.</red>"));
                return;
            }
        }
    }

    /** Number of players with a market open (for diagnostics and tests). */
    public int openMarketCount() {
        return openMarkets.size();
    }
}
