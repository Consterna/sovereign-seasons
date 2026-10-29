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

package com.sovereignseasons.core;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.core.calendar.CalendarListener;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import com.sovereignseasons.core.command.SeasonCommand;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.sovereignseasons.core.crops.CropGrowthListener;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
import com.sovereignseasons.core.temperature.TemperatureManager;
import com.sovereignseasons.core.comfort.ComfortService;
import com.sovereignseasons.core.integration.SeasonsPlaceholderExpansion;
import com.sovereignseasons.core.nature.SeasonalNatureListener;
import com.sovereignseasons.core.ui.SeasonInterface;
import org.bukkit.entity.Player;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;

/**
 * Main Paper plugin class for Sovereign Seasons Core.
 */
public class SovereignSeasonsCorePlugin extends JavaPlugin implements SovereignSeasonsApi {
    private SeasonsConfig config;
    private WorldCalendarManager calendarManager;
    private GreenhouseDetector greenhouseDetector;
    private TemperatureManager temperatureManager;
    private SeasonInterface seasonInterface;
    private FoliaLib foliaLib;

    @Override
    public void onEnable() {
        this.foliaLib = new FoliaLib(this);

        // 1. Load configuration
        this.config = new SeasonsConfig(getLogger());
        InputStream defConfigStream = getResource("config.yml");
        this.config.load(getDataFolder(), defConfigStream);

        // 2. Initialize subsystems
        this.greenhouseDetector = new GreenhouseDetector(this.config);
        this.calendarManager = new WorldCalendarManager(this.config, getDataFolder(), getLogger());
        this.temperatureManager = new TemperatureManager(this.config, this.calendarManager);

        // 3. Register Public API
        SovereignSeasonsApi.Holder.set(this);
        getServer().getServicesManager().register(SovereignSeasonsApi.class, this, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SeasonService.class, this.calendarManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(GreenhouseService.class, this.greenhouseDetector, this, ServicePriority.Normal);
        getServer().getServicesManager().register(TemperatureService.class, this.temperatureManager, this, ServicePriority.Normal);

        // 4. Register Listeners
        getServer().getPluginManager().registerEvents(new CalendarListener(this.calendarManager, this.foliaLib), this);
        getServer().getPluginManager().registerEvents(new CropGrowthListener(this.config, this.calendarManager, this.greenhouseDetector, this.temperatureManager), this);
        getServer().getPluginManager().registerEvents(new SeasonalNatureListener(this.config, this.calendarManager, this.temperatureManager, getLogger()), this);
        ComfortService comfortService = new ComfortService(this.config, this.calendarManager, this.temperatureManager);
        getServer().getPluginManager().registerEvents(comfortService, this);
        this.seasonInterface = new SeasonInterface(this, this.config, this.calendarManager, this.temperatureManager, this.foliaLib);
        getServer().getPluginManager().registerEvents(this.seasonInterface, this);

        // 5. Register Commands
        SeasonCommand seasonCmd = new SeasonCommand(this.config, this.calendarManager, this.greenhouseDetector, this.temperatureManager, this.seasonInterface, getDataFolder());
        PluginCommand cmd = getCommand("season");
        if (cmd != null) {
            cmd.setExecutor(seasonCmd);
            cmd.setTabCompleter(seasonCmd);
        }

        // 6. Schedule Periodic Calendar Ticker (Runs once every second = 20 ticks)
        foliaLib.getImpl().runTimer(task -> {
            for (World world : Bukkit.getWorlds()) {
                calendarManager.tickWorld(world);
            }
        }, 20L, 20L);

        // 7. Player comfort (cold & heat), dispatched to each player's own thread (Folia-safe)
        long comfortInterval = config.getComfortIntervalTicks();
        foliaLib.getImpl().runTimer(task -> {
            if (!config.isComfortEnabled()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                foliaLib.getImpl().runAtEntity(player, entityTask -> comfortService.tick(player));
            }
        }, comfortInterval, comfortInterval);

        // 8. Season HUD refresh
        long hudInterval = config.getHudUpdateTicks();
        foliaLib.getImpl().runTimer(task -> seasonInterface.forEachHudPlayer(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                foliaLib.getImpl().runAtEntity(player, entityTask -> seasonInterface.updateHud(player));
            }
        }), hudInterval, hudInterval);

        // 9. Optional PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SeasonsPlaceholderExpansion(this, this.calendarManager, this.temperatureManager).register();
            getLogger().info("PlaceholderAPI detected: %sovereignseasons_*% placeholders registered.");
        }

        // Startup banner
        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons Core v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Minecraft In-Game Days Calendar: ACTIVE");
        getLogger().info(" - Winter Crop Pause & Withering: ACTIVE");
        getLogger().info(" - Spatial LRU Greenhouse Detection: ACTIVE");
        getLogger().info(" - Seasonal Temperature Service: ACTIVE");
        getLogger().info(" - Seasonal Crops, Weather & Fauna: " + (config.isSeasonalGrowthEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info(" - Player Cold & Heat Comfort: " + (config.isComfortEnabled() ? "ACTIVE" : "DISABLED"));
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        if (calendarManager != null) {
            calendarManager.saveData();
        }

        if (greenhouseDetector != null) {
            greenhouseDetector.clearCache();
        }

        if (seasonInterface != null) {
            seasonInterface.hideAll();
        }

        if (foliaLib != null) {
            foliaLib.getImpl().cancelAllTasks();
        }

        SovereignSeasonsApi.Holder.set(null);
        try {
            if (getServer() != null && getServer().getServicesManager() != null) {
                getServer().getServicesManager().unregisterAll(this);
            }
        } catch (Throwable ignored) {}

        getLogger().info("Sovereign Seasons Core disabled successfully.");
    }

    @Override
    public SeasonService getSeasonService() {
        return calendarManager;
    }

    @Override
    public GreenhouseService getGreenhouseService() {
        return greenhouseDetector;
    }

    @Override
    public TemperatureService getTemperatureService() {
        return temperatureManager;
    }

    public SeasonsConfig getPluginConfig() {
        return config;
    }
}
