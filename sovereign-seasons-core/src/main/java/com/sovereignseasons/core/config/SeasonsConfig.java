package com.sovereignseasons.core.config;

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

    public String getPrefix() { return prefix; }
}
