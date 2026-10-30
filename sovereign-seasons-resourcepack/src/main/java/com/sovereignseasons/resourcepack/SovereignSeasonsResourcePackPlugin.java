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

package com.sovereignseasons.resourcepack;

import com.sovereignseasons.api.SovereignSeasonsApi;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.ui.SeasonIconProvider;
import com.sovereignseasons.resourcepack.command.PackCommand;
import com.sovereignseasons.resourcepack.config.PackConfig;
import com.sovereignseasons.resourcepack.delivery.PackDelivery;
import com.sovereignseasons.resourcepack.http.PackServer;
import com.sovereignseasons.resourcepack.icons.PackSeasonIcons;
import com.sovereignseasons.resourcepack.pack.PackFile;
import com.sovereignseasons.resourcepack.pack.PackLibrary;
import com.sovereignseasons.resourcepack.pack.PackUrls;
import com.tcoded.folialib.FoliaLib;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Main Paper plugin class for the Sovereign Seasons resource pack: hosts the bundled packs and sends them to
 * players, swapping the seasonal one as seasons change.
 */
public class SovereignSeasonsResourcePackPlugin extends JavaPlugin {
    private FoliaLib foliaLib;
    private PackServer server;

    @Override
    public void onEnable() {
        SeasonService seasonService;
        try {
            seasonService = SovereignSeasonsApi.get().getSeasonService();
        } catch (RuntimeException | LinkageError e) {
            getLogger().severe("Sovereign Seasons Core 1.1.0+ is required. Disabling ResourcePack: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PackConfig config = new PackConfig(getLogger());
        config.load(getDataFolder(), getResource("config.yml"));

        PackLibrary library;
        try {
            library = PackLibrary.load(this::getResource, getLogger());
            library.export(new File(getDataFolder(), "packs"));
        } catch (IOException e) {
            getLogger().severe("Could not read the bundled resource packs: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (config.getHostingMode() == PackConfig.HostingMode.SELF) {
            server = new PackServer(getLogger());
            for (PackFile pack : library.all().values()) {
                server.publish(PackUrls.path(pack), pack.data());
            }
            try {
                server.start(config.getBindAddress(), config.getPort());
            } catch (IOException e) {
                getLogger().severe("Could not start the resource pack server on " + config.getBindAddress() + ":" + config.getPort()
                        + " (" + e.getMessage() + "). Packs will not be sent; use another port or hosting.mode: external.");
                server = null;
            }
        }

        this.foliaLib = new FoliaLib(this);
        boolean canSend = config.getHostingMode() == PackConfig.HostingMode.EXTERNAL ? !config.getExternalBaseUrl().isEmpty() : server != null;
        PackDelivery delivery = null;
        if (canSend) {
            delivery = new PackDelivery(config, library, seasonService, foliaLib);
            getServer().getPluginManager().registerEvents(delivery, this);
            PackDelivery finalDelivery = delivery;
            getServer().getServicesManager().register(SeasonIconProvider.class,
                    new PackSeasonIcons(finalDelivery::hasBasePack, config::isSeasonIcons), this, ServicePriority.Normal);
        }

        PackCommand packCmd = new PackCommand(config, library, delivery, server != null ? server::getServedCount : null);
        PluginCommand cmd = getCommand("seasonpack");
        if (cmd != null) {
            cmd.setExecutor(packCmd);
            cmd.setTabCompleter(packCmd);
        }

        getLogger().info("=================================================");
        getLogger().info(" Sovereign Seasons ResourcePack v" + getDescription().getVersion() + " ENABLED");
        getLogger().info(" - Packs: " + String.join(", ", library.all().keySet()));
        getLogger().info(" - Hosting: " + (config.getHostingMode() == PackConfig.HostingMode.SELF
                ? (server != null ? "built-in server on port " + server.getPort() : "FAILED") : "external " + config.getExternalBaseUrl()));
        getLogger().info(" - Seasonal Colors: " + (config.isSeasonalPacksEnabled() ? "ACTIVE " + config.getSeasonsWithPack() : "DISABLED"));
        getLogger().info("=================================================");
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (foliaLib != null) {
            foliaLib.getImpl().cancelAllTasks();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("Sovereign Seasons ResourcePack disabled successfully.");
    }
}
