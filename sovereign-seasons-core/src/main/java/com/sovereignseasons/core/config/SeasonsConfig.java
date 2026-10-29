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

package com.sovereignseasons.core.config;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.crops.CropProfile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * Strongly typed configuration for Sovereign Seasons.
 */
public class SeasonsConfig {
    private final Logger logger;

    // Calendar
    private int daysPerSeason = 7;
    private long ticksPerDay = 24000L;
    private Set<String> enabledWorlds = new HashSet<>();

    // Crops
    private boolean winterGrowthPaused = true;
    private double winterWitherChance = 0.08; // 8% default (user specified 5-10%)
    private Material witherMaterial = Material.DEAD_BUSH;
    private boolean playWitherEffects = true;
    private final Set<Material> affectedCrops = EnumSet.noneOf(Material.class);

    // Greenhouse
    private boolean greenhouseEnabled = true;
    private boolean requireGlassCeiling = true;
    private int maxCeilingHeight = 16;
    private boolean requireHeatSource = true;
    private int heatRadius = 4;
    private int cacheTtlSeconds = 30;
    private int cacheMaxSize = 10000;
    private boolean preventFreezeAndSnow = true;
    private final Set<Material> allowedGlassMaterials = EnumSet.noneOf(Material.class);
    private final Set<Material> allowedHeatSources = EnumSet.noneOf(Material.class);

    // Temperature
    private boolean temperatureEnabled = true;
    private double biomeTemperatureScale = 25.0;
    private double biomeTemperatureOffset = -5.0;
    private final Map<Season, Double> seasonTemperatureOffsets = new EnumMap<>(Season.class);
    private double freezingBelow = 0.0;
    private double coldBelow = 10.0;
    private double mildBelow = 20.0;
    private double warmBelow = 30.0;

    // Seasonal crop growth (outside winter)
    private boolean seasonalGrowthEnabled = true;
    private double outOfSeasonFactor = 0.5;
    private double adjacentBandFactor = 0.6;
    private double farBandFactor = 0.3;
    private int undergroundY = 48;
    private final Map<Material, CropProfile> cropProfiles = new EnumMap<>(Material.class);

    // Seasonal weather
    private boolean seasonalWeatherEnabled = true;
    private final Map<Season, Double> rainChance = new EnumMap<>(Season.class);

    // Seasonal fauna
    private boolean seasonalFaunaEnabled = true;
    private double springTwinChance = 0.25;
    private double winterStrayChance = 0.5;
    private double summerHuskChance = 0.5;

    // Player comfort (cold & heat)
    private boolean comfortEnabled = true;
    private int comfortIntervalTicks = 20;
    private boolean coldEnabled = true;
    private boolean coldDamage = false;
    private int coldFreezeTicks = 120;
    private boolean coldSlowness = true;
    private int warmthBlockLight = 10;
    private boolean heatEnabled = true;
    private double heatExhaustionPerSecond = 0.05;
    private int comfortMessageCooldownSeconds = 60;

    // Interface
    private boolean announceSeasonChange = true;
    private boolean clockShowsDate = true;
    private int hudUpdateTicks = 100;

    // Messages
    private String prefix = "<gold><b>SovereignSeasons</b></gold> <gray>»</gray> ";

    public SeasonsConfig(Logger logger) {
        this.logger = logger;
        initDefaults();
    }

    private void initDefaults() {
        // Default affected crops
        affectedCrops.clear();
        addIfPresent(affectedCrops, "WHEAT");
        addIfPresent(affectedCrops, "CARROTS");
        addIfPresent(affectedCrops, "POTATOES");
        addIfPresent(affectedCrops, "BEETROOTS");
        addIfPresent(affectedCrops, "NETHER_WART");
        addIfPresent(affectedCrops, "COCOA");
        addIfPresent(affectedCrops, "SWEET_BERRY_BUSH");
        addIfPresent(affectedCrops, "MELON_STEM");
        addIfPresent(affectedCrops, "PUMPKIN_STEM");
        addIfPresent(affectedCrops, "ATTACHED_MELON_STEM");
        addIfPresent(affectedCrops, "ATTACHED_PUMPKIN_STEM");
        addIfPresent(affectedCrops, "TORCHFLOWER_CROP");
        addIfPresent(affectedCrops, "PITCHER_CROP");

        // Default glass materials
        allowedGlassMaterials.clear();
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.equals("GLASS") || name.equals("TINTED_GLASS") || name.endsWith("_STAINED_GLASS")
                    || name.equals("GLASS_PANE") || name.endsWith("_STAINED_GLASS_PANE")) {
                allowedGlassMaterials.add(mat);
            }
        }

        // Default heat sources
        allowedHeatSources.clear();
        addIfPresent(allowedHeatSources, "CAMPFIRE");
        addIfPresent(allowedHeatSources, "SOUL_CAMPFIRE");
        addIfPresent(allowedHeatSources, "FIRE");
        addIfPresent(allowedHeatSources, "SOUL_FIRE");
        addIfPresent(allowedHeatSources, "LAVA");
        addIfPresent(allowedHeatSources, "LAVA_CAULDRON");
        addIfPresent(allowedHeatSources, "MAGMA_BLOCK");
        addIfPresent(allowedHeatSources, "TORCH");
        addIfPresent(allowedHeatSources, "WALL_TORCH");
        addIfPresent(allowedHeatSources, "SOUL_TORCH");
        addIfPresent(allowedHeatSources, "SOUL_WALL_TORCH");
        addIfPresent(allowedHeatSources, "LANTERN");
        addIfPresent(allowedHeatSources, "SOUL_LANTERN");
        addIfPresent(allowedHeatSources, "FURNACE");
        addIfPresent(allowedHeatSources, "BLAST_FURNACE");
        addIfPresent(allowedHeatSources, "SMOKER");

        // Default seasonal temperature offsets (Celsius)
        seasonTemperatureOffsets.put(Season.SPRING, 0.0);
        seasonTemperatureOffsets.put(Season.SUMMER, 8.0);
        seasonTemperatureOffsets.put(Season.AUTUMN, -2.0);
        seasonTemperatureOffsets.put(Season.WINTER, -12.0);

        // Default crop climate profiles (gameplay design choices, configurable)
        cropProfiles.clear();
        putProfile("WHEAT", seasons(Season.SPRING, Season.SUMMER, Season.AUTUMN), bands(TemperatureBand.MILD, TemperatureBand.WARM));
        putProfile("CARROTS", seasons(Season.SPRING, Season.AUTUMN), bands(TemperatureBand.COLD, TemperatureBand.MILD));
        putProfile("POTATOES", seasons(Season.SPRING, Season.AUTUMN), bands(TemperatureBand.COLD, TemperatureBand.MILD));
        putProfile("BEETROOTS", seasons(Season.SPRING, Season.AUTUMN), bands(TemperatureBand.COLD, TemperatureBand.MILD));
        putProfile("MELON_STEM", seasons(Season.SUMMER), bands(TemperatureBand.WARM, TemperatureBand.HOT));
        putProfile("ATTACHED_MELON_STEM", seasons(Season.SUMMER), bands(TemperatureBand.WARM, TemperatureBand.HOT));
        putProfile("PUMPKIN_STEM", seasons(Season.SUMMER, Season.AUTUMN), bands(TemperatureBand.MILD, TemperatureBand.WARM));
        putProfile("ATTACHED_PUMPKIN_STEM", seasons(Season.SUMMER, Season.AUTUMN), bands(TemperatureBand.MILD, TemperatureBand.WARM));
        putProfile("COCOA", seasons(Season.SPRING, Season.SUMMER), bands(TemperatureBand.WARM, TemperatureBand.HOT));
        putProfile("SWEET_BERRY_BUSH", seasons(Season.SUMMER, Season.AUTUMN), bands(TemperatureBand.COLD, TemperatureBand.MILD));
        putProfile("SUGAR_CANE", seasons(Season.SPRING, Season.SUMMER), bands(TemperatureBand.WARM, TemperatureBand.HOT));
        putProfile("CACTUS", seasons(Season.SPRING, Season.SUMMER), bands(TemperatureBand.WARM, TemperatureBand.HOT));
        putProfile("TORCHFLOWER_CROP", seasons(Season.SPRING, Season.SUMMER), bands(TemperatureBand.MILD, TemperatureBand.WARM));
        putProfile("PITCHER_CROP", seasons(Season.SPRING, Season.SUMMER), bands(TemperatureBand.MILD, TemperatureBand.WARM));

        // Default rain acceptance per season
        rainChance.put(Season.SPRING, 1.0);
        rainChance.put(Season.SUMMER, 0.5);
        rainChance.put(Season.AUTUMN, 1.0);
        rainChance.put(Season.WINTER, 0.8);
    }

    private static Set<Season> seasons(Season... values) {
        EnumSet<Season> set = EnumSet.noneOf(Season.class);
        set.addAll(Arrays.asList(values));
        return set;
    }

    private static Set<TemperatureBand> bands(TemperatureBand... values) {
        EnumSet<TemperatureBand> set = EnumSet.noneOf(TemperatureBand.class);
        set.addAll(Arrays.asList(values));
        return set;
    }

    private void putProfile(String materialName, Set<Season> fertile, Set<TemperatureBand> ideal) {
        Material mat = Material.matchMaterial(materialName);
        if (mat != null && mat.isBlock()) {
            cropProfiles.put(mat, new CropProfile(fertile, ideal));
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

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Calendar
        this.daysPerSeason = Math.max(1, Math.min(100_000, config.getInt("calendar.days-per-season", 7)));
        this.ticksPerDay = Math.max(100L, Math.min(1_000_000L, config.getLong("calendar.ticks-per-day", 24000L)));
        List<String> worldsList = config.getStringList("calendar.enabled-worlds");
        this.enabledWorlds = new HashSet<>(worldsList);

        // Crops
        this.winterGrowthPaused = config.getBoolean("crops.winter-growth-paused", true);
        double witherChance = config.getDouble("crops.winter-wither-chance", 0.08);
        if (!Double.isFinite(witherChance) || Double.isNaN(witherChance)) {
            witherChance = 0.08;
        }
        this.winterWitherChance = Math.max(0.0, Math.min(1.0, witherChance));

        String witherMatName = config.getString("crops.wither-material", "DEAD_BUSH");
        Material matchMat = witherMatName != null ? Material.matchMaterial(witherMatName.trim()) : null;
        this.witherMaterial = (matchMat != null && matchMat.isBlock()) ? matchMat : Material.DEAD_BUSH;
        this.playWitherEffects = config.getBoolean("crops.play-wither-effects", true);

        if (config.contains("crops.affected-crops")) {
            affectedCrops.clear();
            for (String cropName : config.getStringList("crops.affected-crops")) {
                addIfPresent(affectedCrops, cropName);
            }
        }

        // Greenhouse
        this.greenhouseEnabled = config.getBoolean("greenhouse.enabled", true);
        this.requireGlassCeiling = config.getBoolean("greenhouse.require-glass-ceiling", true);
        this.maxCeilingHeight = Math.max(1, Math.min(384, config.getInt("greenhouse.max-ceiling-height", 16)));
        this.requireHeatSource = config.getBoolean("greenhouse.require-heat-source", true);
        this.heatRadius = Math.max(1, Math.min(32, config.getInt("greenhouse.heat-radius", 4)));
        this.cacheTtlSeconds = Math.max(1, Math.min(86400, config.getInt("greenhouse.cache-ttl-seconds", 30)));
        this.cacheMaxSize = Math.max(100, Math.min(1_000_000, config.getInt("greenhouse.cache-max-size", 10000)));
        this.preventFreezeAndSnow = config.getBoolean("greenhouse.prevent-freeze-and-snow", true);

        if (config.contains("greenhouse.allowed-glass-materials")) {
            allowedGlassMaterials.clear();
            for (String matName : config.getStringList("greenhouse.allowed-glass-materials")) {
                addIfPresent(allowedGlassMaterials, matName);
            }
        }

        if (config.contains("greenhouse.allowed-heat-sources")) {
            allowedHeatSources.clear();
            for (String matName : config.getStringList("greenhouse.allowed-heat-sources")) {
                addIfPresent(allowedHeatSources, matName);
            }
        }

        // Temperature
        this.temperatureEnabled = config.getBoolean("temperature.enabled", true);
        setBiomeTemperatureScale(config.getDouble("temperature.biome-scale", 25.0));
        setBiomeTemperatureOffset(config.getDouble("temperature.biome-offset", -5.0));
        for (Season season : Season.values()) {
            double fallback = seasonTemperatureOffsets.getOrDefault(season, 0.0);
            setSeasonTemperatureOffset(season, config.getDouble("temperature.season-offsets." + season.getIdentifier(), fallback));
        }
        setBandThresholds(
                config.getDouble("temperature.bands.freezing-below", 0.0),
                config.getDouble("temperature.bands.cold-below", 10.0),
                config.getDouble("temperature.bands.mild-below", 20.0),
                config.getDouble("temperature.bands.warm-below", 30.0)
        );

        // Seasonal crop growth
        this.seasonalGrowthEnabled = config.getBoolean("crops.seasonal-growth.enabled", true);
        this.outOfSeasonFactor = unitInterval(config.getDouble("crops.seasonal-growth.out-of-season-factor", 0.5), 0.5);
        this.adjacentBandFactor = unitInterval(config.getDouble("crops.seasonal-growth.adjacent-band-factor", 0.6), 0.6);
        this.farBandFactor = unitInterval(config.getDouble("crops.seasonal-growth.far-band-factor", 0.3), 0.3);
        this.undergroundY = Math.max(-64, Math.min(320, config.getInt("crops.seasonal-growth.underground-y", 48)));
        ConfigurationSection profiles = config.getConfigurationSection("crops.seasonal-growth.profiles");
        if (profiles != null) {
            cropProfiles.clear();
            for (String key : profiles.getKeys(false)) {
                Material mat = Material.matchMaterial(key.trim());
                if (mat == null || !mat.isBlock()) {
                    if (logger != null) {
                        logger.warning("Unknown block in crops.seasonal-growth.profiles: " + key);
                    }
                    continue;
                }
                Set<Season> fertile = EnumSet.noneOf(Season.class);
                for (String seasonName : profiles.getStringList(key + ".seasons")) {
                    Season season = Season.fromString(seasonName);
                    if (season != null) fertile.add(season);
                }
                Set<TemperatureBand> ideal = EnumSet.noneOf(TemperatureBand.class);
                for (String bandName : profiles.getStringList(key + ".bands")) {
                    TemperatureBand band = parseBand(bandName);
                    if (band != null) ideal.add(band);
                }
                cropProfiles.put(mat, new CropProfile(fertile, ideal));
            }
        }

        // Seasonal weather
        this.seasonalWeatherEnabled = config.getBoolean("weather.enabled", true);
        for (Season season : Season.values()) {
            double fallback = rainChance.getOrDefault(season, 1.0);
            rainChance.put(season, unitInterval(config.getDouble("weather.rain-chance." + season.getIdentifier(), fallback), fallback));
        }

        // Seasonal fauna
        this.seasonalFaunaEnabled = config.getBoolean("fauna.enabled", true);
        this.springTwinChance = unitInterval(config.getDouble("fauna.spring-twin-chance", 0.25), 0.25);
        this.winterStrayChance = unitInterval(config.getDouble("fauna.winter-stray-chance", 0.5), 0.5);
        this.summerHuskChance = unitInterval(config.getDouble("fauna.summer-husk-chance", 0.5), 0.5);

        // Comfort
        this.comfortEnabled = config.getBoolean("comfort.enabled", true);
        this.comfortIntervalTicks = Math.max(10, Math.min(200, config.getInt("comfort.interval-ticks", 20)));
        this.coldEnabled = config.getBoolean("comfort.cold.enabled", true);
        this.coldDamage = config.getBoolean("comfort.cold.damage", false);
        this.coldFreezeTicks = Math.max(0, Math.min(300, config.getInt("comfort.cold.freeze-ticks", 120)));
        this.coldSlowness = config.getBoolean("comfort.cold.slowness", true);
        this.warmthBlockLight = Math.max(0, Math.min(15, config.getInt("comfort.cold.warmth-block-light", 10)));
        this.heatEnabled = config.getBoolean("comfort.heat.enabled", true);
        double exhaustion = config.getDouble("comfort.heat.exhaustion-per-second", 0.05);
        this.heatExhaustionPerSecond = Double.isFinite(exhaustion) ? Math.max(0.0, Math.min(4.0, exhaustion)) : 0.05;
        this.comfortMessageCooldownSeconds = Math.max(0, Math.min(3600, config.getInt("comfort.message-cooldown-seconds", 60)));

        // Interface
        this.announceSeasonChange = config.getBoolean("interface.announce-season-change", true);
        this.clockShowsDate = config.getBoolean("interface.clock-shows-date", true);
        this.hudUpdateTicks = Math.max(20, Math.min(1200, config.getInt("interface.hud-update-ticks", 100)));

        // Messages
        String configuredPrefix = config.getString("messages.prefix", "<gold><b>SovereignSeasons</b></gold> <gray>»</gray> ");
        this.prefix = configuredPrefix != null ? configuredPrefix : "<gold><b>SovereignSeasons</b></gold> <gray>»</gray> ";
    }

    // Getters and Setters for testing and runtime modification
    public int getDaysPerSeason() { return daysPerSeason; }
    public void setDaysPerSeason(int days) { this.daysPerSeason = Math.max(1, Math.min(100_000, days)); }

    public long getTicksPerDay() { return ticksPerDay; }
    public void setTicksPerDay(long ticks) { this.ticksPerDay = Math.max(100L, Math.min(1_000_000L, ticks)); }

    public Set<String> getEnabledWorlds() { return Collections.unmodifiableSet(enabledWorlds); }
    public void setEnabledWorlds(Set<String> worlds) { this.enabledWorlds = new HashSet<>(worlds); }

    public boolean isWinterGrowthPaused() { return winterGrowthPaused; }
    public void setWinterGrowthPaused(boolean paused) { this.winterGrowthPaused = paused; }

    public double getWinterWitherChance() { return winterWitherChance; }
    public void setWinterWitherChance(double chance) {
        if (!Double.isFinite(chance) || Double.isNaN(chance)) {
            chance = 0.08;
        }
        this.winterWitherChance = Math.max(0.0, Math.min(1.0, chance));
    }

    public Material getWitherMaterial() { return witherMaterial; }
    public void setWitherMaterial(Material mat) {
        this.witherMaterial = (mat != null && mat.isBlock()) ? mat : Material.DEAD_BUSH;
    }

    public boolean isPlayWitherEffects() { return playWitherEffects; }
    public void setPlayWitherEffects(boolean play) { this.playWitherEffects = play; }

    public Set<Material> getAffectedCrops() { return Collections.unmodifiableSet(affectedCrops); }

    public boolean isGreenhouseEnabled() { return greenhouseEnabled; }
    public void setGreenhouseEnabled(boolean enabled) { this.greenhouseEnabled = enabled; }

    public boolean isRequireGlassCeiling() { return requireGlassCeiling; }
    public void setRequireGlassCeiling(boolean req) { this.requireGlassCeiling = req; }

    public int getMaxCeilingHeight() { return maxCeilingHeight; }
    public void setMaxCeilingHeight(int height) { this.maxCeilingHeight = Math.max(1, Math.min(384, height)); }

    public boolean isRequireHeatSource() { return requireHeatSource; }
    public void setRequireHeatSource(boolean req) { this.requireHeatSource = req; }

    public int getHeatRadius() { return heatRadius; }
    public void setHeatRadius(int radius) { this.heatRadius = Math.max(1, Math.min(32, radius)); }

    public int getCacheTtlSeconds() { return cacheTtlSeconds; }
    public void setCacheTtlSeconds(int ttl) { this.cacheTtlSeconds = Math.max(1, Math.min(86400, ttl)); }

    public int getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(int size) { this.cacheMaxSize = Math.max(100, Math.min(1_000_000, size)); }

    public boolean isPreventFreezeAndSnow() { return preventFreezeAndSnow; }
    public void setPreventFreezeAndSnow(boolean prevent) { this.preventFreezeAndSnow = prevent; }

    public Set<Material> getAllowedGlassMaterials() { return Collections.unmodifiableSet(allowedGlassMaterials); }
    public Set<Material> getAllowedHeatSources() { return Collections.unmodifiableSet(allowedHeatSources); }

    public boolean isTemperatureEnabled() { return temperatureEnabled; }
    public void setTemperatureEnabled(boolean enabled) { this.temperatureEnabled = enabled; }

    public double getBiomeTemperatureScale() { return biomeTemperatureScale; }
    public void setBiomeTemperatureScale(double scale) { this.biomeTemperatureScale = clampTemperature(scale, 25.0); }

    public double getBiomeTemperatureOffset() { return biomeTemperatureOffset; }
    public void setBiomeTemperatureOffset(double offset) { this.biomeTemperatureOffset = clampTemperature(offset, -5.0); }

    public double getSeasonTemperatureOffset(Season season) {
        if (season == null) return 0.0;
        return seasonTemperatureOffsets.getOrDefault(season, 0.0);
    }
    public void setSeasonTemperatureOffset(Season season, double offset) {
        if (season == null) return;
        seasonTemperatureOffsets.put(season, clampTemperature(offset, 0.0));
    }

    public double getFreezingBelow() { return freezingBelow; }
    public double getColdBelow() { return coldBelow; }
    public double getMildBelow() { return mildBelow; }
    public double getWarmBelow() { return warmBelow; }

    /**
     * Sets the temperature band thresholds. Thresholds must be finite, within [-100, 100] and strictly ascending;
     * otherwise the defaults (0, 10, 20, 30) are restored to keep classification consistent.
     */
    public void setBandThresholds(double freezing, double cold, double mild, double warm) {
        boolean inRange = isValidTemperature(freezing) && isValidTemperature(cold)
                && isValidTemperature(mild) && isValidTemperature(warm);
        if (!inRange || !(freezing < cold && cold < mild && mild < warm)) {
            if (logger != null) {
                logger.warning("Invalid temperature band thresholds; restoring defaults (0, 10, 20, 30).");
            }
            freezing = 0.0;
            cold = 10.0;
            mild = 20.0;
            warm = 30.0;
        }
        this.freezingBelow = freezing;
        this.coldBelow = cold;
        this.mildBelow = mild;
        this.warmBelow = warm;
    }

    private static boolean isValidTemperature(double value) {
        return Double.isFinite(value) && value >= -100.0 && value <= 100.0;
    }

    private static double clampTemperature(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(-100.0, Math.min(100.0, value));
    }

    private static double unitInterval(double value, double fallback) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static TemperatureBand parseBand(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return TemperatureBand.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isSeasonalGrowthEnabled() { return seasonalGrowthEnabled; }
    public void setSeasonalGrowthEnabled(boolean enabled) { this.seasonalGrowthEnabled = enabled; }
    public double getOutOfSeasonFactor() { return outOfSeasonFactor; }
    public void setOutOfSeasonFactor(double factor) { this.outOfSeasonFactor = unitInterval(factor, 0.5); }
    public double getAdjacentBandFactor() { return adjacentBandFactor; }
    public void setAdjacentBandFactor(double factor) { this.adjacentBandFactor = unitInterval(factor, 0.6); }
    public double getFarBandFactor() { return farBandFactor; }
    public void setFarBandFactor(double factor) { this.farBandFactor = unitInterval(factor, 0.3); }
    public int getUndergroundY() { return undergroundY; }
    public CropProfile getCropProfile(Material material) { return material == null ? null : cropProfiles.get(material); }
    public Map<Material, CropProfile> getCropProfiles() { return Collections.unmodifiableMap(cropProfiles); }

    public boolean isSeasonalWeatherEnabled() { return seasonalWeatherEnabled; }
    public void setSeasonalWeatherEnabled(boolean enabled) { this.seasonalWeatherEnabled = enabled; }
    public double getRainChance(Season season) { return season == null ? 1.0 : rainChance.getOrDefault(season, 1.0); }
    public void setRainChance(Season season, double chance) { if (season != null) rainChance.put(season, unitInterval(chance, 1.0)); }

    public boolean isSeasonalFaunaEnabled() { return seasonalFaunaEnabled; }
    public void setSeasonalFaunaEnabled(boolean enabled) { this.seasonalFaunaEnabled = enabled; }
    public double getSpringTwinChance() { return springTwinChance; }
    public void setSpringTwinChance(double chance) { this.springTwinChance = unitInterval(chance, 0.25); }
    public double getWinterStrayChance() { return winterStrayChance; }
    public void setWinterStrayChance(double chance) { this.winterStrayChance = unitInterval(chance, 0.5); }
    public double getSummerHuskChance() { return summerHuskChance; }
    public void setSummerHuskChance(double chance) { this.summerHuskChance = unitInterval(chance, 0.5); }

    public boolean isComfortEnabled() { return comfortEnabled; }
    public void setComfortEnabled(boolean enabled) { this.comfortEnabled = enabled; }
    public int getComfortIntervalTicks() { return comfortIntervalTicks; }
    public boolean isColdEnabled() { return coldEnabled; }
    public void setColdEnabled(boolean enabled) { this.coldEnabled = enabled; }
    public boolean isColdDamage() { return coldDamage; }
    public void setColdDamage(boolean damage) { this.coldDamage = damage; }
    public int getColdFreezeTicks() { return coldFreezeTicks; }
    public boolean isColdSlowness() { return coldSlowness; }
    public int getWarmthBlockLight() { return warmthBlockLight; }
    public boolean isHeatEnabled() { return heatEnabled; }
    public void setHeatEnabled(boolean enabled) { this.heatEnabled = enabled; }
    public double getHeatExhaustionPerSecond() { return heatExhaustionPerSecond; }
    public int getComfortMessageCooldownSeconds() { return comfortMessageCooldownSeconds; }

    public boolean isAnnounceSeasonChange() { return announceSeasonChange; }
    public void setAnnounceSeasonChange(boolean announce) { this.announceSeasonChange = announce; }
    public boolean isClockShowsDate() { return clockShowsDate; }
    public int getHudUpdateTicks() { return hudUpdateTicks; }

    public String getPrefix() { return prefix; }
}
