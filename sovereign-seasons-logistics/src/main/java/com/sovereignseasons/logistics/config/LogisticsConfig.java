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

package com.sovereignseasons.logistics.config;

import com.sovereignseasons.api.Season;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Strongly typed configuration for Sovereign Seasons Logistics.
 */
public class LogisticsConfig {
    private final Logger logger;

    // Mobility
    private boolean mobilityEnabled = true;
    private int mobilityIntervalTicks = 10;
    private int effectDurationTicks = 30;
    private final Set<EntityType> mounts = EnumSet.noneOf(EntityType.class);

    private boolean snowEnabled = true;
    private int snowSlowness = 1;
    private int deepSnowSlowness = 2;
    private int deepSnowLayers = 3;
    private boolean leatherBootsProtect = true;
    private boolean leatherHorseArmorProtect = true;

    private boolean mudEnabled = true;
    private int mudSlowness = 1;
    private final Set<Season> mudSeasons = EnumSet.noneOf(Season.class);
    private final Set<Material> mudMaterials = EnumSet.noneOf(Material.class);

    private boolean roadsEnabled = true;
    private int roadMountSpeed = 1;
    private int roadPlayerSpeed = 0;
    private int roadMaxSnowLayers = 2;
    private final Set<Material> roadMaterials = EnumSet.noneOf(Material.class);

    private boolean blizzardEnabled = true;
    private int blizzardExtraSlowness = 1;

    // Weight
    private boolean weightEnabled = false;
    private double weightCapacity = 1000.0;
    private double heavyThreshold = 0.6;
    private double overloadedThreshold = 0.85;
    private double containerContentsFactor = 0.5;
    private double crateWeightFactor = 0.6;
    private double defaultItemWeight = 0.5;
    private final List<WeightRule> weightRules = new ArrayList<>();

    // Crates
    private boolean cratesEnabled = true;
    private int itemsPerCrate = 9;
    private double packedDecayRate = 0.5;
    private final Set<Material> packableMaterials = EnumSet.noneOf(Material.class);

    // Frozen lakes
    private boolean iceEnabled = true;
    private int iceIntervalTicks = 40;
    private int iceSamplesPerPlayer = 8;
    private int iceRadius = 48;
    private int iceMaxThawPerChunk = 64;

    // Messages
    private String prefix = "<gold><b>Logistics</b></gold> <gray>»</gray> ";

    /**
     * Weight rule: materials whose name fully matches {@code pattern} weigh {@code weight} each.
     */
    public record WeightRule(Pattern pattern, double weight) {}

    public LogisticsConfig(Logger logger) {
        this.logger = logger;
        initDefaults();
    }

    private void initDefaults() {
        mounts.clear();
        for (String name : List.of("HORSE", "DONKEY", "MULE", "LLAMA", "TRADER_LLAMA", "CAMEL", "SKELETON_HORSE", "ZOMBIE_HORSE")) {
            addEntityIfPresent(mounts, name);
        }

        mudSeasons.clear();
        mudSeasons.add(Season.SPRING);
        mudSeasons.add(Season.AUTUMN);
        mudMaterials.clear();
        for (String name : List.of("GRASS_BLOCK", "DIRT", "COARSE_DIRT", "FARMLAND", "PODZOL", "ROOTED_DIRT", "MYCELIUM", "MUD")) {
            addBlockIfPresent(mudMaterials, name);
        }

        roadMaterials.clear();
        for (String name : List.of("DIRT_PATH", "GRAVEL", "COBBLESTONE", "COBBLESTONE_SLAB", "COBBLESTONE_STAIRS",
                "MOSSY_COBBLESTONE", "STONE_BRICKS", "STONE_BRICK_SLAB", "STONE_BRICK_STAIRS", "MOSSY_STONE_BRICKS",
                "BRICKS", "BRICK_SLAB", "BRICK_STAIRS", "SMOOTH_STONE", "SMOOTH_STONE_SLAB", "STONE_SLAB",
                "POLISHED_ANDESITE", "POLISHED_DIORITE", "POLISHED_GRANITE", "MUD_BRICKS", "MUD_BRICK_SLAB",
                "PACKED_MUD", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES", "POLISHED_BLACKSTONE_BRICKS")) {
            addBlockIfPresent(roadMaterials, name);
        }
        for (Material mat : Material.values()) {
            String n = mat.name();
            if (mat.isBlock() && !n.startsWith("LEGACY_") && (n.endsWith("_PLANKS") || n.endsWith("_CONCRETE"))) {
                roadMaterials.add(mat);
            }
        }

        weightRules.clear();
        addRule("NETHERITE_BLOCK|IRON_BLOCK|GOLD_BLOCK|COPPER_BLOCK|RAW_IRON_BLOCK|RAW_GOLD_BLOCK|RAW_COPPER_BLOCK|ANVIL|CHIPPED_ANVIL|DAMAGED_ANVIL", 9.0);
        addRule(".*_ORE|STONE|COBBLESTONE|DEEPSLATE|COBBLED_DEEPSLATE|.*STONE_BRICKS|ANDESITE|DIORITE|GRANITE|TUFF|BLACKSTONE|OBSIDIAN|SANDSTONE|.*_TERRACOTTA|BRICKS", 1.0);
        addRule(".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS|SWORD|PICKAXE|AXE|SHOVEL|HOE)", 2.0);
        addRule("CARROT|POTATO|BEETROOT|APPLE|WHEAT|.*_SEEDS|SWEET_BERRIES|GLOW_BERRIES|MELON_SLICE|COCOA_BEANS|SUGAR_CANE|BREAD|COOKED_.*|BAKED_POTATO|COOKIE", 0.25);

        packableMaterials.clear();
        for (String name : List.of("CARROT", "POTATO", "BEETROOT", "APPLE", "SWEET_BERRIES", "GLOW_BERRIES", "MELON_SLICE", "COCOA_BEANS", "SUGAR_CANE")) {
            addItemIfPresent(packableMaterials, name);
        }
    }

    private void addRule(String regex, double weight) {
        try {
            weightRules.add(new WeightRule(Pattern.compile(regex), weight));
        } catch (PatternSyntaxException e) {
            if (logger != null) {
                logger.warning("Invalid weight rule pattern '" + regex + "': " + e.getDescription());
            }
        }
    }

    private static void addBlockIfPresent(Set<Material> set, String name) {
        if (name == null || name.isBlank()) return;
        Material mat = Material.matchMaterial(name.trim());
        if (mat != null && mat.isBlock()) set.add(mat);
    }

    private static void addItemIfPresent(Set<Material> set, String name) {
        if (name == null || name.isBlank()) return;
        Material mat = Material.matchMaterial(name.trim());
        if (mat != null && mat.isItem()) set.add(mat);
    }

    private static void addEntityIfPresent(Set<EntityType> set, String name) {
        if (name == null || name.isBlank()) return;
        try {
            set.add(EntityType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // Entity type not present in this server version
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

        // Mobility
        this.mobilityEnabled = config.getBoolean("mobility.enabled", true);
        this.mobilityIntervalTicks = clamp(config.getInt("mobility.interval-ticks", 10), 5, 100);
        this.effectDurationTicks = clamp(config.getInt("mobility.effect-duration-ticks", 30), 10, 200);
        if (config.contains("mobility.mounts")) {
            mounts.clear();
            for (String name : config.getStringList("mobility.mounts")) addEntityIfPresent(mounts, name);
        }

        this.snowEnabled = config.getBoolean("mobility.snow.enabled", true);
        this.snowSlowness = clamp(config.getInt("mobility.snow.slowness", 1), 0, 5);
        this.deepSnowSlowness = clamp(config.getInt("mobility.snow.deep-slowness", 2), 0, 5);
        this.deepSnowLayers = clamp(config.getInt("mobility.snow.deep-layers", 3), 1, 8);
        this.leatherBootsProtect = config.getBoolean("mobility.snow.leather-boots-protect", true);
        this.leatherHorseArmorProtect = config.getBoolean("mobility.snow.leather-horse-armor-protect", true);

        this.mudEnabled = config.getBoolean("mobility.mud.enabled", true);
        this.mudSlowness = clamp(config.getInt("mobility.mud.slowness", 1), 0, 5);
        if (config.contains("mobility.mud.seasons")) {
            mudSeasons.clear();
            for (String name : config.getStringList("mobility.mud.seasons")) {
                Season season = Season.fromString(name);
                if (season != null) mudSeasons.add(season);
            }
        }
        if (config.contains("mobility.mud.materials")) {
            mudMaterials.clear();
            for (String name : config.getStringList("mobility.mud.materials")) addBlockIfPresent(mudMaterials, name);
        }

        this.roadsEnabled = config.getBoolean("mobility.roads.enabled", true);
        this.roadMountSpeed = clamp(config.getInt("mobility.roads.mount-speed", 1), 0, 5);
        this.roadPlayerSpeed = clamp(config.getInt("mobility.roads.player-speed", 0), 0, 5);
        this.roadMaxSnowLayers = clamp(config.getInt("mobility.roads.max-snow-layers", 2), 0, 8);
        if (config.contains("mobility.roads.materials")) {
            roadMaterials.clear();
            for (String name : config.getStringList("mobility.roads.materials")) addBlockIfPresent(roadMaterials, name);
        }

        this.blizzardEnabled = config.getBoolean("mobility.blizzard.enabled", true);
        this.blizzardExtraSlowness = clamp(config.getInt("mobility.blizzard.extra-slowness", 1), 0, 5);

        // Weight
        this.weightEnabled = config.getBoolean("weight.enabled", false);
        this.weightCapacity = positive(config.getDouble("weight.capacity", 1000.0), 1000.0, 1_000_000.0);
        setWeightThresholds(config.getDouble("weight.heavy-threshold", 0.6), config.getDouble("weight.overloaded-threshold", 0.85));
        this.containerContentsFactor = fraction(config.getDouble("weight.container-contents-factor", 0.5), 0.5);
        this.crateWeightFactor = fraction(config.getDouble("weight.crate-factor", 0.6), 0.6);
        this.defaultItemWeight = positive(config.getDouble("weight.default-weight", 0.5), 0.5, 1000.0);
        // A list of {pattern, weight} maps: regex patterns contain dots, which YAML section keys cannot hold
        if (config.contains("weight.rules")) {
            weightRules.clear();
            for (Map<?, ?> rule : config.getMapList("weight.rules")) {
                Object pattern = rule.get("pattern");
                Object weight = rule.get("weight");
                if (!(pattern instanceof String regex) || regex.isBlank() || !(weight instanceof Number number)) {
                    if (logger != null) {
                        logger.warning("Ignoring malformed weight rule: " + rule);
                    }
                    continue;
                }
                double value = number.doubleValue();
                if (Double.isFinite(value) && value >= 0.0) {
                    addRule(regex.trim().toUpperCase(Locale.ROOT), Math.min(1000.0, value));
                }
            }
        }

        // Crates
        this.cratesEnabled = config.getBoolean("crates.enabled", true);
        this.itemsPerCrate = clamp(config.getInt("crates.items-per-crate", 9), 2, 64);
        this.packedDecayRate = fraction(config.getDouble("crates.packed-decay-rate", 0.5), 0.5);
        if (config.contains("crates.packable")) {
            packableMaterials.clear();
            for (String name : config.getStringList("crates.packable")) addItemIfPresent(packableMaterials, name);
        }

        // Frozen lakes
        this.iceEnabled = config.getBoolean("ice.enabled", true);
        this.iceIntervalTicks = clamp(config.getInt("ice.interval-ticks", 40), 10, 1200);
        this.iceSamplesPerPlayer = clamp(config.getInt("ice.samples-per-player", 8), 1, 64);
        this.iceRadius = clamp(config.getInt("ice.radius", 48), 4, 128);
        this.iceMaxThawPerChunk = clamp(config.getInt("ice.max-thaw-per-chunk", 64), 1, 4096);

        // Messages
        String configuredPrefix = config.getString("messages.prefix", "<gold><b>Logistics</b></gold> <gray>»</gray> ");
        this.prefix = configuredPrefix != null ? configuredPrefix : "<gold><b>Logistics</b></gold> <gray>»</gray> ";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double fraction(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double positive(double value, double fallback, double max) {
        if (!Double.isFinite(value) || value <= 0.0) return fallback;
        return Math.min(max, value);
    }

    /**
     * Sets the weight thresholds (fractions of the capacity). They must satisfy 0 &lt; heavy &lt; overloaded;
     * otherwise the defaults (0.6 / 0.85) are restored.
     */
    public void setWeightThresholds(double heavy, double overloaded) {
        boolean valid = Double.isFinite(heavy) && Double.isFinite(overloaded) && heavy > 0.0 && heavy < overloaded && overloaded <= 10.0;
        if (!valid) {
            if (logger != null) {
                logger.warning("Invalid weight thresholds; restoring defaults (0.6 / 0.85).");
            }
            heavy = 0.6;
            overloaded = 0.85;
        }
        this.heavyThreshold = heavy;
        this.overloadedThreshold = overloaded;
    }

    // Getters and Setters for testing and runtime modification
    public boolean isMobilityEnabled() { return mobilityEnabled; }
    public void setMobilityEnabled(boolean enabled) { this.mobilityEnabled = enabled; }
    public int getMobilityIntervalTicks() { return mobilityIntervalTicks; }
    public int getEffectDurationTicks() { return effectDurationTicks; }
    public Set<EntityType> getMounts() { return Collections.unmodifiableSet(mounts); }

    public boolean isSnowEnabled() { return snowEnabled; }
    public void setSnowEnabled(boolean enabled) { this.snowEnabled = enabled; }
    public int getSnowSlowness() { return snowSlowness; }
    public int getDeepSnowSlowness() { return deepSnowSlowness; }
    public int getDeepSnowLayers() { return deepSnowLayers; }
    public boolean isLeatherBootsProtect() { return leatherBootsProtect; }
    public void setLeatherBootsProtect(boolean protect) { this.leatherBootsProtect = protect; }
    public boolean isLeatherHorseArmorProtect() { return leatherHorseArmorProtect; }

    public boolean isMudEnabled() { return mudEnabled; }
    public void setMudEnabled(boolean enabled) { this.mudEnabled = enabled; }
    public int getMudSlowness() { return mudSlowness; }
    public Set<Season> getMudSeasons() { return Collections.unmodifiableSet(mudSeasons); }
    public Set<Material> getMudMaterials() { return Collections.unmodifiableSet(mudMaterials); }

    public boolean isRoadsEnabled() { return roadsEnabled; }
    public void setRoadsEnabled(boolean enabled) { this.roadsEnabled = enabled; }
    public int getRoadMountSpeed() { return roadMountSpeed; }
    public int getRoadPlayerSpeed() { return roadPlayerSpeed; }
    public void setRoadPlayerSpeed(int speed) { this.roadPlayerSpeed = clamp(speed, 0, 5); }
    public int getRoadMaxSnowLayers() { return roadMaxSnowLayers; }
    public Set<Material> getRoadMaterials() { return Collections.unmodifiableSet(roadMaterials); }

    public boolean isBlizzardEnabled() { return blizzardEnabled; }
    public void setBlizzardEnabled(boolean enabled) { this.blizzardEnabled = enabled; }
    public int getBlizzardExtraSlowness() { return blizzardExtraSlowness; }

    public boolean isWeightEnabled() { return weightEnabled; }
    public void setWeightEnabled(boolean enabled) { this.weightEnabled = enabled; }
    public double getWeightCapacity() { return weightCapacity; }
    public double getHeavyThreshold() { return heavyThreshold; }
    public double getOverloadedThreshold() { return overloadedThreshold; }
    public double getContainerContentsFactor() { return containerContentsFactor; }
    public double getCrateWeightFactor() { return crateWeightFactor; }
    public double getDefaultItemWeight() { return defaultItemWeight; }
    public List<WeightRule> getWeightRules() { return Collections.unmodifiableList(weightRules); }

    public boolean isCratesEnabled() { return cratesEnabled; }
    public void setCratesEnabled(boolean enabled) { this.cratesEnabled = enabled; }
    public int getItemsPerCrate() { return itemsPerCrate; }
    public double getPackedDecayRate() { return packedDecayRate; }
    public Set<Material> getPackableMaterials() { return Collections.unmodifiableSet(packableMaterials); }

    public boolean isIceEnabled() { return iceEnabled; }
    public void setIceEnabled(boolean enabled) { this.iceEnabled = enabled; }
    public int getIceIntervalTicks() { return iceIntervalTicks; }
    public int getIceSamplesPerPlayer() { return iceSamplesPerPlayer; }
    public int getIceRadius() { return iceRadius; }
    public int getIceMaxThawPerChunk() { return iceMaxThawPerChunk; }

    public String getPrefix() { return prefix; }
}
