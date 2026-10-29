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

package com.sovereignseasons.logistics;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.logistics.api.LogisticsApi;
import com.sovereignseasons.logistics.api.LogisticsApiImpl;
import com.sovereignseasons.logistics.command.LogisticsCommand;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.sovereignseasons.logistics.crates.CrateItems;
import com.sovereignseasons.logistics.crates.CrateListener;
import com.sovereignseasons.logistics.crates.CrateUnpacker;
import com.sovereignseasons.logistics.crates.FreshnessBridge;
import com.sovereignseasons.logistics.crates.PantryFreshnessBridge;
import com.sovereignseasons.logistics.ice.FrozenWaterService;
import com.sovereignseasons.logistics.mobility.MobilityService;
import com.sovereignseasons.logistics.weight.InventoryWeigher;
import com.sovereignseasons.logistics.weight.WeightModel;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;

/**
 * Main Paper plugin class for Sovereign Seasons Logistics.
 */
public class SovereignSeasonsLogisticsPlugin extends JavaPlugin {
    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        SeasonService seasonService;
        TemperatureService temperatureService;
        GreenhouseService greenhouseService;
        try {
            SovereignSeasonsApi api = SovereignSeasonsApi.get();
            seasonService = api.getSeasonService();
            temperatureService = api.getTemperatureService();
            greenhouseService = api.getGreenhouseService();
        } catch (RuntimeException | LinkageError e) {
            getLogger().severe("Sovereign Seasons Core 1.1.0+ is required. Disabling Logistics: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.foliaLib = new FoliaLib(this);

        // 1. Load configuration
        LogisticsConfig config = new LogisticsConfig(getLogger());
        InputStream defConfigStream = getResource("config.yml");
        config.load(getDataFolder(), defConfigStream);

        // 2. Optional Pantry integration (only touches Pantry classes when Pantry is installed)
        FreshnessBridge freshness = null;
        if (getServer().getPluginManager().getPlugin("SovereignSeasons-Pantry") != null) {
            try {
                freshness = PantryFreshnessBridge.create(getServer().getServicesManager());
            } catch (LinkageError e) {
                getLogger().warning("Pantry found but its API is unavailable (update Pantry): crates will not track freshness.");
            }
        }

        // 3. Subsystems
        CrateItems crates = new CrateItems(this);
        CrateUnpacker unpacker = new CrateUnpacker(config, freshness);
        WeightModel weightModel = new WeightModel(config);
        InventoryWeigher weigher = new InventoryWeigher(weightModel, crates);
        MobilityService mobility = new MobilityService(config, seasonService, temperatureService, weightModel, weigher);
        FrozenWaterService ice = new FrozenWaterService(this, config, seasonService, temperatureService, greenhouseService, foliaLib);

        // Public API for other modules (Commerce reads crates through it)
        getServer().getServicesManager().register(LogisticsApi.class, new LogisticsApiImpl(crates, unpacker), this, ServicePriority.Normal);

        // 4. Listeners
        getServer().getPluginManager().registerEvents(new CrateListener(config, crates, freshness, unpacker), this);
        getServer().getPluginManager().registerEvents(mobility, this);
        getServer().getPluginManager().registerEvents(ice, this);

        // 5. Command
        LogisticsCommand logisticsCmd = new LogisticsCommand(config, mobility, weightModel, weigher, ice, getDataFolder());
        PluginCommand cmd = getCommand("logistics");
        if (cmd != null) {
            cmd.setExecutor(logisticsCmd);
            cmd.setTabCompleter(logisticsCmd);
        }

        // 6. Periodic tasks, dispatched to each player's own thread (Folia-safe)
        long mobilityInterval = config.getMobilityIntervalTicks();
        foliaLib.getImpl().runTimer(task -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                foliaLib.getImpl().runAtEntity(player, entityTask -> mobility.tick(player));
            }
        }, mobilityInterval, mobilityInterval);

        long iceInterval = config.getIceIntervalTicks();
        foliaLib.getImpl().runTimer(task -> {
            if (!config.isIceEnabled()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                foliaLib.getImpl().runAtEntity(player, entityTask -> ice.sampleAround(player));
            }
        }, iceInterval, iceInterval);

        // Startup banner
        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons Logistics v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Seasonal Snow, Mud, Roads & Blizzards: " + (config.isMobilityEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Carried Weight: " + (config.isWeightEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Bulk Crates: " + (config.isCratesEnabled() ? "ACTIVE" : "DISABLED")
                + (freshness != null ? " (Pantry freshness linked)" : ""));
        getLogger().info(" - Frozen Lakes: " + (config.isIceEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        if (foliaLib != null) {
            foliaLib.getImpl().cancelAllTasks();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("Sovereign Seasons Logistics disabled successfully.");
    }
}
