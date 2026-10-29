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

package com.sovereignseasons.pantry;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.pantry.api.PantryApi;
import com.sovereignseasons.pantry.api.PantryApiImpl;
import com.sovereignseasons.pantry.cellar.CellarDetector;
import com.sovereignseasons.pantry.command.PantryCommand;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.DecayCalculator;
import com.sovereignseasons.pantry.food.FoodDecayService;
import com.sovereignseasons.pantry.item.PdcFoodStateCodec;
import com.sovereignseasons.pantry.listener.FoodLifecycleListener;
import com.sovereignseasons.pantry.storage.StorageTracker;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;

/**
 * Main Paper plugin class for Sovereign Seasons Pantry.
 */
public class SovereignSeasonsPantryPlugin extends JavaPlugin {
    private PantryConfig config;
    private CellarDetector cellarDetector;
    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        TemperatureService temperatureService;
        try {
            temperatureService = SovereignSeasonsApi.get().getTemperatureService();
        } catch (RuntimeException | LinkageError e) {
            getLogger().severe("Sovereign Seasons Core (with TemperatureService) is required. Disabling Pantry: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.foliaLib = new FoliaLib(this);

        // 1. Load configuration
        this.config = new PantryConfig(getLogger());
        InputStream defConfigStream = getResource("config.yml");
        this.config.load(getDataFolder(), defConfigStream);

        // 2. Initialize subsystems
        this.cellarDetector = new CellarDetector(this.config);
        DecayCalculator calculator = new DecayCalculator(this.config);
        FoodDecayService decayService = new FoodDecayService(this.config, calculator, new PdcFoodStateCodec(this));
        StorageTracker tracker = new StorageTracker(this, this.config, decayService, this.cellarDetector, temperatureService);

        // Public API for other modules (e.g. Logistics crates keep food freshness while packed)
        getServer().getServicesManager().register(PantryApi.class,
                new PantryApiImpl(this.config, calculator, decayService.getCodec(), tracker::now), this, ServicePriority.Normal);

        // 3. Register Listeners
        FoodLifecycleListener lifecycleListener = new FoodLifecycleListener(this.config, decayService, tracker, this.cellarDetector, this.foliaLib);
        getServer().getPluginManager().registerEvents(lifecycleListener, this);

        // 4. Register Commands
        PantryCommand pantryCmd = new PantryCommand(this.config, decayService, tracker, this.cellarDetector, getDataFolder());
        PluginCommand cmd = getCommand("pantry");
        if (cmd != null) {
            cmd.setExecutor(pantryCmd);
            cmd.setTabCompleter(pantryCmd);
        }

        // 5. Periodic player inventory evaluation, dispatched to each player's own thread (Folia-safe)
        long interval = this.config.getPlayerCheckIntervalTicks();
        foliaLib.getImpl().runTimer(task -> {
            if (!config.isDecayEnabled()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                foliaLib.getImpl().runAtEntity(player, entityTask -> lifecycleListener.evaluatePlayer(player));
            }
        }, interval, interval);

        // Startup banner
        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons Pantry v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Seasonal Food Decay: " + (config.isDecayEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Cold Cellars & Ice Boxes: " + (config.isCellarEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Perishable foods configured: " + config.getShelfLifeTable().size());
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        if (cellarDetector != null) {
            cellarDetector.clearCache();
        }
        if (foliaLib != null) {
            foliaLib.getImpl().cancelAllTasks();
        }
        try {
            getServer().getServicesManager().unregisterAll(this);
        } catch (Throwable ignored) {}
        getLogger().info("Sovereign Seasons Pantry disabled successfully.");
    }

    public PantryConfig getPluginConfig() {
        return config;
    }
}
