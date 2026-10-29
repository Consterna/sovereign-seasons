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

package com.sovereignseasons.pantry.config;

import com.sovereignseasons.api.temperature.TemperatureBand;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Strongly typed configuration for Sovereign Seasons Pantry.
 */
public class PantryConfig {
    private final Logger logger;

    // Decay
    private boolean decayEnabled = true;
    private long ticksPerDay = 24000L;
    private long minEvaluationIntervalTicks = 6000L;
    private long expiryGranularityTicks = 1000L;
    private long playerCheckIntervalTicks = 1200L;
    private boolean spoilToRottenFlesh = true;
    private final Map<TemperatureBand, Double> bandRates = new EnumMap<>(TemperatureBand.class);

    // Stages
    private double freshAbove = 0.5;
    private double staleAbove = 0.15;

    // Shelf life (in-game days per material; missing materials never spoil)
    private final Map<Material, Double> shelfLifeDays = new EnumMap<>(Material.class);

    // Cellar
    private boolean cellarEnabled = true;
    private double cellarMultiplier = 0.5;
    private int minRoofBlocks = 3;
    private int roofScanHeight = 16;
    private int deepY = 40;
    private int iceRadius = 2;
    private double iceMultiplier = 0.5;
    private final Set<Material> iceMaterials = EnumSet.noneOf(Material.class);
    private int cellarCacheTtlSeconds = 60;
    private int cellarCacheMaxSize = 5000;

    // Preservation
    private double smokedMultiplier = 0.25;

    // Container types (lowercase InventoryType name -> decay multiplier)
    private final Map<String, Double> containerMultipliers = new HashMap<>();

    // Composting rotten food
    private boolean compostEnabled = true;
    private double compostChance = 0.3;

    // Spoilage warnings for players
    private boolean spoilageWarnings = true;

    // Consumption
    private int staleHungerSeconds = 10;
    private int spoilingHungerSeconds = 30;

    // Messages
    private String prefix = "<gold><b>Pantry</b></gold> <gray>»</gray> ";

    public PantryConfig(Logger logger) {
        this.logger = logger;
        initDefaults();
    }

    private void initDefaults() {
        bandRates.put(TemperatureBand.FREEZING, 0.25);
        bandRates.put(TemperatureBand.COLD, 0.5);
        bandRates.put(TemperatureBand.MILD, 1.0);
        bandRates.put(TemperatureBand.WARM, 1.5);
        bandRates.put(TemperatureBand.HOT, 2.0);

        shelfLifeDays.clear();
        // Raw meat and fish
        putShelfLife("BEEF", 3);
        putShelfLife("PORKCHOP", 3);
        putShelfLife("CHICKEN", 2);
        putShelfLife("MUTTON", 3);
        putShelfLife("RABBIT", 2);
        putShelfLife("COD", 2);
        putShelfLife("SALMON", 2);
        putShelfLife("TROPICAL_FISH", 2);
        // Cooked meat and fish
        putShelfLife("COOKED_BEEF", 6);
        putShelfLife("COOKED_PORKCHOP", 6);
        putShelfLife("COOKED_CHICKEN", 5);
        putShelfLife("COOKED_MUTTON", 6);
        putShelfLife("COOKED_RABBIT", 5);
        putShelfLife("COOKED_COD", 4);
        putShelfLife("COOKED_SALMON", 4);
        // Bakery and prepared food
        putShelfLife("BREAD", 7);
        putShelfLife("COOKIE", 10);
        putShelfLife("PUMPKIN_PIE", 5);
        putShelfLife("BAKED_POTATO", 5);
        putShelfLife("MUSHROOM_STEW", 3);
        putShelfLife("BEETROOT_SOUP", 3);
        putShelfLife("RABBIT_STEW", 3);
        putShelfLife("SUSPICIOUS_STEW", 3);
        // Produce
        putShelfLife("APPLE", 10);
        putShelfLife("CARROT", 12);
        putShelfLife("POTATO", 14);
        putShelfLife("BEETROOT", 10);
        putShelfLife("MELON_SLICE", 3);
        putShelfLife("SWEET_BERRIES", 4);
        putShelfLife("GLOW_BERRIES", 5);

        containerMultipliers.clear();
        containerMultipliers.put("barrel", 0.75);

        iceMaterials.clear();
        addIfPresent(iceMaterials, "ICE");
        addIfPresent(iceMaterials, "PACKED_ICE");
        addIfPresent(iceMaterials, "BLUE_ICE");
        addIfPresent(iceMaterials, "SNOW_BLOCK");
    }

    private void putShelfLife(String name, double days) {
        Material mat = Material.matchMaterial(name);
        if (mat != null && mat.isItem()) {
            shelfLifeDays.put(mat, days);
        }
    }

    private void addIfPresent(Set<Material> set, String name) {
        if (name == null || name.isBlank()) return;
        Material mat = Material.matchMaterial(name.trim());
        if (mat != null && mat.isBlock()) {
            set.add(mat);
        }
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
                if (logger != null) {
                    logger.severe("Could not save default config.yml: " + e.getMessage());
                }
            }
        }

        FileConfiguration config = new YamlConfiguration();
        if (file.exists()) {
            try {
                config.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                // Keep running with defaults instead of disabling the plugin on a malformed file
                if (logger != null) {
                    logger.severe("Malformed config.yml, using defaults: " + e.getMessage());
                }
            }
        }

        // Decay
        this.decayEnabled = config.getBoolean("decay.enabled", true);
        setTicksPerDay(config.getLong("decay.ticks-per-day", 24000L));
        setMinEvaluationIntervalTicks(config.getLong("decay.min-evaluation-interval-ticks", 6000L));
        setExpiryGranularityTicks(config.getLong("decay.expiry-granularity-ticks", 1000L));
        setPlayerCheckIntervalTicks(config.getLong("decay.player-check-interval-ticks", 1200L));
        this.spoilToRottenFlesh = config.getBoolean("decay.spoil-to-rotten-flesh", true);
        for (TemperatureBand band : TemperatureBand.values()) {
            String path = "decay.band-rates." + band.name().toLowerCase(Locale.ROOT);
            setBandRate(band, config.getDouble(path, bandRates.getOrDefault(band, 1.0)));
        }

        // Stages
        setStageThresholds(config.getDouble("stages.fresh-above", 0.5), config.getDouble("stages.stale-above", 0.15));

        // Shelf life
        ConfigurationSection shelfSection = config.getConfigurationSection("shelf-life-days");
        if (shelfSection != null) {
            shelfLifeDays.clear();
            for (String key : shelfSection.getKeys(false)) {
                Material mat = Material.matchMaterial(key.trim());
                if (mat == null || !mat.isItem()) {
                    if (logger != null) {
                        logger.warning("Unknown material in shelf-life-days: " + key);
                    }
                    continue;
                }
                setShelfLifeDays(mat, shelfSection.getDouble(key, 0.0));
            }
        }

        // Cellar
        this.cellarEnabled = config.getBoolean("cellar.enabled", true);
        setCellarMultiplier(config.getDouble("cellar.multiplier", 0.5));
        setMinRoofBlocks(config.getInt("cellar.min-roof-blocks", 3));
        setRoofScanHeight(config.getInt("cellar.roof-scan-height", 16));
        setDeepY(config.getInt("cellar.deep-y", 40));
        setIceRadius(config.getInt("cellar.ice-radius", 2));
        setIceMultiplier(config.getDouble("cellar.ice-multiplier", 0.5));
        if (config.contains("cellar.ice-materials")) {
            iceMaterials.clear();
            for (String matName : config.getStringList("cellar.ice-materials")) {
                addIfPresent(iceMaterials, matName);
            }
        }
        this.cellarCacheTtlSeconds = Math.max(1, Math.min(86400, config.getInt("cellar.cache-ttl-seconds", 60)));
        this.cellarCacheMaxSize = Math.max(100, Math.min(1_000_000, config.getInt("cellar.cache-max-size", 5000)));

        // Preservation
        setSmokedMultiplier(config.getDouble("preservation.smoked-multiplier", 0.25));

        // Containers
        ConfigurationSection containers = config.getConfigurationSection("containers");
        if (containers != null) {
            containerMultipliers.clear();
            for (String key : containers.getKeys(false)) {
                setContainerMultiplier(key, containers.getDouble(key, 1.0));
            }
        }

        // Composting
        this.compostEnabled = config.getBoolean("compost.enabled", true);
        double chance = config.getDouble("compost.rotten-flesh-chance", 0.3);
        this.compostChance = Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 0.3;

        // Warnings
        this.spoilageWarnings = config.getBoolean("warnings.spoilage", true);

        // Consumption
        this.staleHungerSeconds = Math.max(0, Math.min(600, config.getInt("consumption.stale-hunger-seconds", 10)));
        this.spoilingHungerSeconds = Math.max(0, Math.min(600, config.getInt("consumption.spoiling-hunger-seconds", 30)));

        // Messages
        String configuredPrefix = config.getString("messages.prefix", "<gold><b>Pantry</b></gold> <gray>»</gray> ");
        this.prefix = configuredPrefix != null ? configuredPrefix : "<gold><b>Pantry</b></gold> <gray>»</gray> ";
    }

    private static double sanitizeMultiplier(double value, double fallback, double max) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0, Math.min(max, value));
    }

    // Getters and Setters for testing and runtime modification
    public boolean isDecayEnabled() { return decayEnabled; }
    public void setDecayEnabled(boolean enabled) { this.decayEnabled = enabled; }

    public long getTicksPerDay() { return ticksPerDay; }
    public void setTicksPerDay(long ticks) { this.ticksPerDay = Math.max(100L, Math.min(1_000_000L, ticks)); }

    public long getMinEvaluationIntervalTicks() { return minEvaluationIntervalTicks; }
    public void setMinEvaluationIntervalTicks(long ticks) { this.minEvaluationIntervalTicks = Math.max(0L, Math.min(1_000_000L, ticks)); }

    public long getExpiryGranularityTicks() { return expiryGranularityTicks; }
    public void setExpiryGranularityTicks(long ticks) { this.expiryGranularityTicks = Math.max(1L, Math.min(24_000L, ticks)); }

    public long getPlayerCheckIntervalTicks() { return playerCheckIntervalTicks; }
    public void setPlayerCheckIntervalTicks(long ticks) { this.playerCheckIntervalTicks = Math.max(20L, Math.min(72_000L, ticks)); }

    public boolean isSpoilToRottenFlesh() { return spoilToRottenFlesh; }
    public void setSpoilToRottenFlesh(boolean spoil) { this.spoilToRottenFlesh = spoil; }

    public double getBandRate(TemperatureBand band) {
        if (band == null) return 1.0;
        return bandRates.getOrDefault(band, 1.0);
    }
    public void setBandRate(TemperatureBand band, double rate) {
        if (band == null) return;
        bandRates.put(band, sanitizeMultiplier(rate, 1.0, 10.0));
    }

    public double getFreshAbove() { return freshAbove; }
    public double getStaleAbove() { return staleAbove; }

    /**
     * Sets the stage thresholds. They must satisfy 0 &lt; staleAbove &lt; freshAbove &lt; 1;
     * otherwise the defaults (0.5 / 0.15) are restored.
     */
    public void setStageThresholds(double fresh, double stale) {
        boolean valid = Double.isFinite(fresh) && Double.isFinite(stale) && stale > 0.0 && stale < fresh && fresh < 1.0;
        if (!valid) {
            if (logger != null) {
                logger.warning("Invalid freshness stage thresholds; restoring defaults (0.5 / 0.15).");
            }
            fresh = 0.5;
            stale = 0.15;
        }
        this.freshAbove = fresh;
        this.staleAbove = stale;
    }

    /**
     * Shelf life in in-game days, or 0 if the material never spoils.
     */
    public double getShelfLifeDays(Material material) {
        if (material == null) return 0.0;
        return shelfLifeDays.getOrDefault(material, 0.0);
    }
    public void setShelfLifeDays(Material material, double days) {
        if (material == null) return;
        if (!Double.isFinite(days) || days <= 0.0) {
            shelfLifeDays.remove(material);
            return;
        }
        shelfLifeDays.put(material, Math.min(10_000.0, days));
    }
    public Map<Material, Double> getShelfLifeTable() { return Collections.unmodifiableMap(shelfLifeDays); }

    public boolean isCellarEnabled() { return cellarEnabled; }
    public void setCellarEnabled(boolean enabled) { this.cellarEnabled = enabled; }

    public double getCellarMultiplier() { return cellarMultiplier; }
    public void setCellarMultiplier(double multiplier) { this.cellarMultiplier = sanitizeMultiplier(multiplier, 0.5, 1.0); }

    public int getMinRoofBlocks() { return minRoofBlocks; }
    public void setMinRoofBlocks(int blocks) { this.minRoofBlocks = Math.max(1, Math.min(64, blocks)); }

    public int getRoofScanHeight() { return roofScanHeight; }
    public void setRoofScanHeight(int height) { this.roofScanHeight = Math.max(1, Math.min(128, height)); }

    public int getDeepY() { return deepY; }
    public void setDeepY(int y) { this.deepY = Math.max(-64, Math.min(320, y)); }

    public int getIceRadius() { return iceRadius; }
    public void setIceRadius(int radius) { this.iceRadius = Math.max(0, Math.min(8, radius)); }

    public double getIceMultiplier() { return iceMultiplier; }
    public void setIceMultiplier(double multiplier) { this.iceMultiplier = sanitizeMultiplier(multiplier, 0.5, 1.0); }

    public Set<Material> getIceMaterials() { return Collections.unmodifiableSet(iceMaterials); }

    public int getCellarCacheTtlSeconds() { return cellarCacheTtlSeconds; }
    public int getCellarCacheMaxSize() { return cellarCacheMaxSize; }

    public double getSmokedMultiplier() { return smokedMultiplier; }
    public void setSmokedMultiplier(double multiplier) { this.smokedMultiplier = sanitizeMultiplier(multiplier, 0.25, 1.0); }

    /**
     * Decay multiplier for a container type (lowercase InventoryType name), 1.0 if not configured.
     */
    public double getContainerMultiplier(String containerType) {
        if (containerType == null) return 1.0;
        return containerMultipliers.getOrDefault(containerType.trim().toLowerCase(Locale.ROOT), 1.0);
    }
    public void setContainerMultiplier(String containerType, double multiplier) {
        if (containerType == null || containerType.isBlank()) return;
        containerMultipliers.put(containerType.trim().toLowerCase(Locale.ROOT), sanitizeMultiplier(multiplier, 1.0, 2.0));
    }

    public boolean isCompostEnabled() { return compostEnabled; }
    public void setCompostEnabled(boolean enabled) { this.compostEnabled = enabled; }
    public double getCompostChance() { return compostChance; }

    public boolean isSpoilageWarnings() { return spoilageWarnings; }

    public int getStaleHungerSeconds() { return staleHungerSeconds; }
    public int getSpoilingHungerSeconds() { return spoilingHungerSeconds; }

    public String getPrefix() { return prefix; }
}
