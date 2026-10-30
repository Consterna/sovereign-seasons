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

package com.sovereignseasons.resourcepack.config;

import com.sovereignseasons.api.Season;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Strongly typed configuration for the Sovereign Seasons resource pack plugin.
 */
public class PackConfig {

    public enum HostingMode { SELF, EXTERNAL }

    private final Logger logger;

    private boolean basePackEnabled = true;
    private boolean seasonalPacksEnabled = true;
    private final Set<Season> seasonsWithPack = EnumSet.of(Season.SPRING, Season.AUTUMN, Season.WINTER);
    private boolean force = false;
    private String prompt = "<gold>Sovereign Seasons</gold><gray>: cajas, iconos y colores de la estación (opcional).</gray>";
    private int sendDelayTicks = 20;
    private boolean seasonIcons = true;

    private HostingMode hostingMode = HostingMode.SELF;
    private String bindAddress = "0.0.0.0";
    private int port = 8163;
    private String publicUrl = "";
    private String externalBaseUrl = "";

    private String prefix = "<gold><b>Texturas</b></gold> <gray>»</gray> ";

    public PackConfig(Logger logger) {
        this.logger = logger;
    }

    public void load(File dataFolder, InputStream defaultResource) {
        if (dataFolder == null) {
            dataFolder = new File(".");
        }
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File file = new File(dataFolder, "config.yml");
        if (!file.exists() && defaultResource != null) {
            try {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultResource, StandardCharsets.UTF_8));
                defConfig.save(file);
            } catch (IOException e) {
                warn("Could not save default config.yml: " + e.getMessage());
            }
        }
        FileConfiguration config = new YamlConfiguration();
        if (file.exists()) {
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                // Keep running with defaults instead of disabling the plugin on a malformed file
                warn("Malformed config.yml, using defaults: " + e.getMessage());
            }
        }

        this.basePackEnabled = config.getBoolean("packs.base", true);
        this.seasonalPacksEnabled = config.getBoolean("packs.seasonal-colors", true);
        seasonsWithPack.clear();
        if (config.contains("packs.seasons-with-colors")) {
            for (String name : config.getStringList("packs.seasons-with-colors")) {
                Season season = Season.fromString(name);
                if (season == Season.SUMMER) {
                    warn("Summer uses vanilla colours: it has no seasonal pack.");
                } else if (season != null) {
                    seasonsWithPack.add(season);
                } else {
                    warn("Unknown season '" + name + "' in packs.seasons-with-colors");
                }
            }
        } else {
            seasonsWithPack.addAll(EnumSet.of(Season.SPRING, Season.AUTUMN, Season.WINTER));
        }
        this.force = config.getBoolean("packs.force", false);
        String configuredPrompt = config.getString("packs.prompt", prompt);
        this.prompt = configuredPrompt != null ? configuredPrompt : "";
        this.sendDelayTicks = Math.max(0, Math.min(200, config.getInt("packs.send-delay-ticks", 20)));
        this.seasonIcons = config.getBoolean("packs.season-icons", true);

        String mode = config.getString("hosting.mode", "self");
        this.hostingMode = "external".equalsIgnoreCase(mode == null ? "" : mode.trim()) ? HostingMode.EXTERNAL : HostingMode.SELF;
        if (mode != null && !mode.trim().equalsIgnoreCase("self") && !mode.trim().equalsIgnoreCase("external")) {
            warn("Unknown hosting.mode '" + mode + "', using self.");
        }
        String bind = config.getString("hosting.self.bind-address", "0.0.0.0");
        this.bindAddress = bind != null ? bind.trim() : "0.0.0.0";
        int configuredPort = config.getInt("hosting.self.port", 8163);
        if (configuredPort < 1 || configuredPort > 65535) {
            warn("hosting.self.port must be 1-65535, using 8163.");
            configuredPort = 8163;
        }
        this.port = configuredPort;
        this.publicUrl = url(config.getString("hosting.self.public-url", ""), "hosting.self.public-url");
        this.externalBaseUrl = url(config.getString("hosting.external.base-url", ""), "hosting.external.base-url");
        if (hostingMode == HostingMode.EXTERNAL && externalBaseUrl.isEmpty()) {
            warn("hosting.mode is external but hosting.external.base-url is empty: no pack will be sent.");
        }

        String configuredPrefix = config.getString("messages.prefix", prefix);
        this.prefix = configuredPrefix != null ? configuredPrefix : "";
    }

    /**
     * An http(s) base URL without the trailing slash, or "" if empty or invalid.
     */
    private String url(String raw, String key) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                warn(key + " must be an http(s) URL: '" + value + "'");
                return "";
            }
        } catch (URISyntaxException e) {
            warn(key + " is not a valid URL: '" + value + "'");
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    public boolean isBasePackEnabled() { return basePackEnabled; }
    public boolean isSeasonalPacksEnabled() { return seasonalPacksEnabled; }
    public Set<Season> getSeasonsWithPack() { return Collections.unmodifiableSet(seasonsWithPack); }
    public boolean isForce() { return force; }
    public String getPrompt() { return prompt; }
    public int getSendDelayTicks() { return sendDelayTicks; }
    public boolean isSeasonIcons() { return seasonIcons; }
    public HostingMode getHostingMode() { return hostingMode; }
    public String getBindAddress() { return bindAddress; }
    public int getPort() { return port; }
    public String getPublicUrl() { return publicUrl; }
    public String getExternalBaseUrl() { return externalBaseUrl; }
    public String getPrefix() { return prefix; }
}
