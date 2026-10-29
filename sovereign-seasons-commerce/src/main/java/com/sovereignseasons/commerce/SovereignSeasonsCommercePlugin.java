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

package com.sovereignseasons.commerce;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.commerce.bridge.CrateReader;
import com.sovereignseasons.commerce.bridge.FoodFreshness;
import com.sovereignseasons.commerce.bridge.LogisticsCrateReader;
import com.sovereignseasons.commerce.bridge.PantryFoodFreshness;
import com.sovereignseasons.commerce.bulk.BulkSaleListener;
import com.sovereignseasons.commerce.command.CommerceCommand;
import com.sovereignseasons.commerce.config.CommerceConfig;
import com.sovereignseasons.commerce.market.CommerceStats;
import com.sovereignseasons.commerce.market.MarketListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.List;

/**
 * Main Paper plugin class for Sovereign Seasons Commerce. Everything is event driven (no scheduled tasks), so
 * each handler already runs on the thread that owns the entity it touches, as Folia requires.
 */
public class SovereignSeasonsCommercePlugin extends JavaPlugin {
    private MarketListener market;

    @Override
    public void onEnable() {
        SeasonService seasonService;
        try {
            seasonService = SovereignSeasonsApi.get().getSeasonService();
        } catch (RuntimeException | LinkageError e) {
            getLogger().severe("Sovereign Seasons Core 1.1.0+ is required. Disabling Commerce: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 1. Load configuration
        CommerceConfig config = new CommerceConfig(getLogger());
        InputStream defConfigStream = getResource("config.yml");
        config.load(getDataFolder(), defConfigStream);

        // 2. Optional integrations (only touch their classes when the plugins are installed)
        FoodFreshness freshness = null;
        if (getServer().getPluginManager().getPlugin("SovereignSeasons-Pantry") != null) {
            try {
                freshness = PantryFoodFreshness.create(getServer().getServicesManager());
            } catch (LinkageError e) {
                getLogger().warning("Pantry found but its API is unavailable (update Pantry): spoiling food will not be refused.");
            }
        }
        CrateReader crates = null;
        if (getServer().getPluginManager().getPlugin("SovereignSeasons-Logistics") != null) {
            try {
                crates = LogisticsCrateReader.create(getServer().getServicesManager());
            } catch (LinkageError e) {
                getLogger().warning("Logistics found but its API is unavailable (update Logistics): bulk crate sales are off.");
            }
        }

        // 3. Listeners
        CommerceStats stats = new CommerceStats();
        this.market = new MarketListener(this, config, seasonService, freshness, stats);
        getServer().getPluginManager().registerEvents(market, this);
        if (crates != null) {
            getServer().getPluginManager().registerEvents(new BulkSaleListener(this, config, seasonService, crates, freshness, stats), this);
        }

        // 4. Command
        CommerceCommand commerceCmd = new CommerceCommand(config, seasonService, stats, () -> {
            List<World> worlds = Bukkit.getWorlds();
            return worlds.isEmpty() ? null : worlds.get(0);
        }, getDataFolder());
        PluginCommand cmd = getCommand("commerce");
        if (cmd != null) {
            cmd.setExecutor(commerceCmd);
            cmd.setTabCompleter(commerceCmd);
        }

        // Startup banner
        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons Commerce v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Seasonal Villager Prices: " + (config.isPricesEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Seasonal Wandering Trader Fairs: " + (config.isFairsEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Spoiling Food Refused: " + (freshness != null && config.isRefuseSpoilingFood() ? "ACTIVE (Pantry linked)" : "DISABLED"));
        getLogger().info(" - Bulk Crate Sales: " + (crates != null && config.isBulkEnabled() ? "ACTIVE (Logistics linked)" : "DISABLED"));
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        if (market != null) {
            market.clearOpenWanderers();
        }
        getLogger().info("Sovereign Seasons Commerce disabled successfully.");
    }
}
