package com.sovereignseasons.core;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.calendar.CalendarListener;
import com.sovereignseasons.core.calendar.WorldCalendarManager;
import com.sovereignseasons.core.command.SeasonCommand;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.sovereignseasons.core.crops.CropGrowthListener;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;
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

        // 3. Register Public API
        SovereignSeasonsApi.Holder.set(this);
        getServer().getServicesManager().register(SovereignSeasonsApi.class, this, this, ServicePriority.Normal);
        getServer().getServicesManager().register(SeasonService.class, this.calendarManager, this, ServicePriority.Normal);
        getServer().getServicesManager().register(GreenhouseService.class, this.greenhouseDetector, this, ServicePriority.Normal);

        // 4. Register Listeners
        getServer().getPluginManager().registerEvents(new CalendarListener(this.calendarManager), this);
        getServer().getPluginManager().registerEvents(new CropGrowthListener(this.config, this.calendarManager, this.greenhouseDetector), this);

        // 5. Register Commands
        SeasonCommand seasonCmd = new SeasonCommand(this.config, this.calendarManager, this.greenhouseDetector, getDataFolder());
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

        // Startup banner
        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons Core v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Minecraft In-Game Days Calendar: ACTIVE");
        getLogger().info(" - Winter Crop Pause & Withering: ACTIVE");
        getLogger().info(" - Spatial LRU Greenhouse Detection: ACTIVE");
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

        if (foliaLib != null) {
            foliaLib.getImpl().cancelAllTasks();
        }

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

    public SeasonsConfig getPluginConfig() {
        return config;
    }
}
