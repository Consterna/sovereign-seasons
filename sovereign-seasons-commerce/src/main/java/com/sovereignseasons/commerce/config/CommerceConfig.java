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

package com.sovereignseasons.commerce.config;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.commerce.pricing.ProductValues;
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
 * Strongly typed configuration for Sovereign Seasons Commerce.
 */
public class CommerceConfig {
    private static final double MIN_VALUE_LIMIT = 0.1;
    private static final double MAX_VALUE_LIMIT = 10.0;

    private final Logger logger;

    // Seasonal prices
    private boolean pricesEnabled = true;
    private boolean showPriceNotice = true;
    private double minValue = 0.5;
    private double maxValue = 2.0;
    private double inSeasonValue = 0.8;
    private double offSeasonValue = 1.0;
    private double winterValue = 1.4;
    private final Map<Material, Set<Season>> cropSeasons = new EnumMap<>(Material.class);
    private final Map<Material, Map<Season, Double>> goods = new EnumMap<>(Material.class);
    private ProductValues productValues;

    // Freshness at the counter (Pantry)
    private boolean refuseSpoilingFood = true;

    // Bulk sales (Logistics)
    private boolean bulkEnabled = true;
    private double bulkBonus = 1.15;
    private int bulkDailyLimit = 8;

    // Seasonal fairs (wandering traders)
    private boolean fairsEnabled = true;
    private final Map<Season, List<FairOffer>> fairOffers = new EnumMap<>(Season.class);

    // Messages
    private String prefix = "<gold><b>Commerce</b></gold> <gray>»</gray> ";

    /**
     * An extra wandering trader offer during a season: {@code amount} of {@code item} for {@code price} emeralds.
     */
    public record FairOffer(Material item, int amount, int price, int maxUses) {}

    public CommerceConfig(Logger logger) {
        this.logger = logger;
        initDefaults();
    }

    private void initDefaults() {
        cropSeasons.clear();
        crop("WHEAT", Season.SPRING, Season.SUMMER, Season.AUTUMN);
        crop("BREAD", Season.SPRING, Season.SUMMER, Season.AUTUMN);
        crop("COOKIE", Season.SPRING, Season.SUMMER, Season.AUTUMN);
        crop("CAKE", Season.SPRING, Season.SUMMER, Season.AUTUMN);
        crop("CARROT", Season.SPRING, Season.AUTUMN);
        crop("GOLDEN_CARROT", Season.SPRING, Season.AUTUMN);
        crop("POTATO", Season.SPRING, Season.AUTUMN);
        crop("BAKED_POTATO", Season.SPRING, Season.AUTUMN);
        crop("BEETROOT", Season.SPRING, Season.AUTUMN);
        crop("PUMPKIN", Season.SUMMER, Season.AUTUMN);
        crop("PUMPKIN_PIE", Season.SUMMER, Season.AUTUMN);
        crop("MELON", Season.SUMMER);
        crop("MELON_SLICE", Season.SUMMER);
        crop("GLISTERING_MELON_SLICE", Season.SUMMER);
        crop("APPLE", Season.AUTUMN);
        crop("SWEET_BERRIES", Season.SUMMER, Season.AUTUMN);
        crop("SUGAR_CANE", Season.SPRING, Season.SUMMER);
        crop("COCOA_BEANS", Season.SPRING, Season.SUMMER);

        goods.clear();
        good("*_WOOL", Map.of(Season.SUMMER, 0.85, Season.WINTER, 1.25));
        good("LEATHER", Map.of(Season.WINTER, 1.25));
        good("COAL", Map.of(Season.SUMMER, 0.85, Season.WINTER, 1.3));
        for (String fish : List.of("COD", "SALMON", "COOKED_COD", "COOKED_SALMON", "TROPICAL_FISH", "PUFFERFISH")) {
            good(fish, Map.of(Season.WINTER, 1.3));
        }
        for (String meat : List.of("CHICKEN", "PORKCHOP", "BEEF", "MUTTON", "RABBIT",
                "COOKED_CHICKEN", "COOKED_PORKCHOP", "COOKED_BEEF", "COOKED_MUTTON", "COOKED_RABBIT")) {
            good(meat, Map.of(Season.AUTUMN, 0.85, Season.WINTER, 1.15));
        }

        fairOffers.clear();
        fair(Season.SPRING, "WHEAT_SEEDS", 8, 1, 4);
        fair(Season.SPRING, "BEETROOT_SEEDS", 8, 1, 4);
        fair(Season.SPRING, "PUMPKIN_SEEDS", 4, 1, 4);
        fair(Season.SPRING, "MELON_SEEDS", 4, 1, 4);
        fair(Season.SUMMER, "ICE", 8, 2, 4);
        fair(Season.SUMMER, "PACKED_ICE", 4, 3, 4);
        fair(Season.SUMMER, "BLUE_ICE", 1, 4, 2);
        fair(Season.AUTUMN, "LEATHER_BOOTS", 1, 3, 2);
        fair(Season.AUTUMN, "LEATHER_HORSE_ARMOR", 1, 4, 2);
        fair(Season.AUTUMN, "HAY_BLOCK", 2, 1, 4);
        fair(Season.WINTER, "CAMPFIRE", 1, 2, 4);
        fair(Season.WINTER, "GLASS", 8, 2, 4);
        fair(Season.WINTER, "COAL", 8, 1, 4);

        rebuildValues();
    }

    private void crop(String name, Season... seasons) {
        Material mat = item(name);
        if (mat != null) {
            Set<Season> set = EnumSet.noneOf(Season.class);
            set.addAll(Arrays.asList(seasons));
            cropSeasons.put(mat, set);
        }
    }

    private void good(String nameOrPattern, Map<Season, Double> values) {
        for (Material mat : expand(nameOrPattern)) {
            Map<Season, Double> perSeason = new EnumMap<>(Season.class);
            perSeason.putAll(values);
            goods.put(mat, perSeason);
        }
    }

    private void fair(Season season, String name, int amount, int price, int maxUses) {
        Material mat = item(name);
        if (mat != null) {
            fairOffers.computeIfAbsent(season, s -> new ArrayList<>()).add(new FairOffer(mat, amount, price, maxUses));
        }
    }

    private static Material item(String name) {
        if (name == null || name.isBlank()) return null;
        Material mat = Material.matchMaterial(name.trim());
        return isTradeable(mat) ? mat : null;
    }

    /** Items a trade can hold: AIR counts as an item in Bukkit but cannot be traded (max stack size 0). */
    private static boolean isTradeable(Material mat) {
        return mat != null && mat.isItem() && !mat.isAir() && !mat.isLegacy() && mat.getMaxStackSize() > 0;
    }

    /**
     * Materials matching a name, or a {@code *} wildcard pattern such as {@code *_WOOL}.
     */
    static List<Material> expand(String nameOrPattern) {
        if (nameOrPattern == null || nameOrPattern.isBlank()) {
            return List.of();
        }
        String pattern = nameOrPattern.trim().toUpperCase(Locale.ROOT);
        if (!pattern.contains("*")) {
            Material mat = item(pattern);
            return mat != null ? List.of(mat) : List.of();
        }
        String regex = Arrays.stream(pattern.split("\\*", -1))
                .map(java.util.regex.Pattern::quote)
                .reduce((a, b) -> a + ".*" + b)
                .orElse("");
        List<Material> matches = new ArrayList<>();
        for (Material mat : Material.values()) {
            if (isTradeable(mat) && mat != Material.EMERALD && mat.name().matches(regex)) {
                matches.add(mat);
            }
        }
        return matches;
    }

    private void rebuildValues() {
        this.productValues = new ProductValues(cropSeasons, goods, inSeasonValue, offSeasonValue, winterValue);
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

        initDefaults();

        // Seasonal prices
        this.pricesEnabled = config.getBoolean("seasonal-prices.enabled", true);
        this.showPriceNotice = config.getBoolean("seasonal-prices.show-notice", true);
        setValueLimits(config.getDouble("seasonal-prices.min-value", 0.5), config.getDouble("seasonal-prices.max-value", 2.0));
        this.inSeasonValue = value(config.getDouble("seasonal-prices.crops.in-season", 0.8), 0.8);
        this.offSeasonValue = value(config.getDouble("seasonal-prices.crops.off-season", 1.0), 1.0);
        this.winterValue = value(config.getDouble("seasonal-prices.crops.winter", 1.4), 1.4);

        ConfigurationSection crops = config.getConfigurationSection("seasonal-prices.crops.products");
        if (crops != null) {
            cropSeasons.clear();
            for (String key : crops.getKeys(false)) {
                Set<Season> seasons = EnumSet.noneOf(Season.class);
                for (String name : crops.getStringList(key)) {
                    Season season = Season.fromString(name);
                    if (season != null) {
                        seasons.add(season);
                    } else {
                        warn("Unknown season '" + name + "' for crop " + key);
                    }
                }
                List<Material> materials = expand(key);
                if (materials.isEmpty()) {
                    warn("Unknown crop product '" + key + "'");
                }
                for (Material mat : materials) {
                    cropSeasons.put(mat, seasons);
                    // Goods are looked up first, so a default good listed here as a crop must stop being a good
                    goods.remove(mat);
                }
            }
        }

        ConfigurationSection goodsSection = config.getConfigurationSection("seasonal-prices.goods");
        if (goodsSection != null) {
            goods.clear();
            for (String key : goodsSection.getKeys(false)) {
                ConfigurationSection perSeason = goodsSection.getConfigurationSection(key);
                if (perSeason == null) {
                    warn("Ignoring malformed goods entry '" + key + "' (expected season: value pairs)");
                    continue;
                }
                Map<Season, Double> values = new EnumMap<>(Season.class);
                for (String seasonName : perSeason.getKeys(false)) {
                    Season season = Season.fromString(seasonName);
                    double raw = perSeason.getDouble(seasonName, Double.NaN);
                    if (season == null || !Double.isFinite(raw) || raw <= 0.0) {
                        warn("Ignoring invalid value '" + seasonName + "' for goods " + key);
                        continue;
                    }
                    values.put(season, Math.max(MIN_VALUE_LIMIT, Math.min(MAX_VALUE_LIMIT, raw)));
                }
                List<Material> materials = expand(key);
                if (materials.isEmpty()) {
                    warn("Unknown goods product '" + key + "'");
                }
                for (Material mat : materials) {
                    // A material listed as a fixed good is no longer priced as a crop
                    cropSeasons.remove(mat);
                    Map<Season, Double> copy = new EnumMap<>(Season.class);
                    copy.putAll(values);
                    goods.put(mat, copy);
                }
            }
        }
        rebuildValues();

        // Freshness at the counter
        this.refuseSpoilingFood = config.getBoolean("freshness.refuse-spoiling-food", true);

        // Bulk sales
        this.bulkEnabled = config.getBoolean("bulk-sales.enabled", true);
        this.bulkBonus = Math.max(1.0, Math.min(3.0, finite(config.getDouble("bulk-sales.bonus", 1.15), 1.15)));
        this.bulkDailyLimit = clamp(config.getInt("bulk-sales.max-crates-per-villager-per-day", 8), 0, 1000);

        // Seasonal fairs
        this.fairsEnabled = config.getBoolean("fairs.enabled", true);
        ConfigurationSection fairs = config.getConfigurationSection("fairs.offers");
        if (fairs != null) {
            // Each season listed replaces its own offers; seasons left out keep the defaults (use [] to empty one)
            for (String seasonName : fairs.getKeys(false)) {
                Season season = Season.fromString(seasonName);
                if (season == null) {
                    warn("Unknown fair season '" + seasonName + "'");
                    continue;
                }
                List<FairOffer> offers = new ArrayList<>();
                for (Map<?, ?> raw : fairs.getMapList(seasonName)) {
                    FairOffer offer = parseOffer(raw);
                    if (offer != null) {
                        offers.add(offer);
                    } else {
                        warn("Ignoring malformed fair offer in " + seasonName + ": " + raw);
                    }
                }
                fairOffers.put(season, offers);
            }
        }

        // Messages
        String configuredPrefix = config.getString("messages.prefix", "<gold><b>Commerce</b></gold> <gray>»</gray> ");
        this.prefix = configuredPrefix != null ? configuredPrefix : "<gold><b>Commerce</b></gold> <gray>»</gray> ";
    }

    private static FairOffer parseOffer(Map<?, ?> raw) {
        if (!(raw.get("item") instanceof String name)) {
            return null;
        }
        Material mat = item(name);
        if (mat == null || mat == Material.EMERALD) {
            return null;
        }
        int amount = number(raw.get("amount"), 1);
        int price = number(raw.get("price"), 1);
        int maxUses = number(raw.get("max-uses"), 4);
        if (amount <= 0 || price <= 0 || maxUses <= 0) {
            return null; // also rejects values that are not whole numbers, such as "gratis" or 2.5
        }
        // Vanilla never shows a trade beyond one stack on either side
        return new FairOffer(mat, Math.min(amount, mat.getMaxStackSize()), Math.min(price, 64), Math.min(maxUses, 64));
    }

    /**
     * A whole number from YAML: the fallback when the key is missing, -1 (invalid) for anything that is not an
     * integer in int range, so a typo never turns silently into a price.
     */
    private static int number(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if ((value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte)) {
            long n = ((Number) value).longValue();
            return n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE ? (int) n : -1;
        }
        return -1;
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private double value(double raw, double fallback) {
        if (!Double.isFinite(raw) || raw <= 0.0) {
            return fallback;
        }
        return Math.max(MIN_VALUE_LIMIT, Math.min(MAX_VALUE_LIMIT, raw));
    }

    /**
     * Sets the bounds applied to every seasonal value. They must satisfy 0 &lt; min &le; 1 &le; max;
     * otherwise the defaults (0.5 / 2.0) are restored.
     */
    public void setValueLimits(double min, double max) {
        boolean valid = Double.isFinite(min) && Double.isFinite(max) && min >= MIN_VALUE_LIMIT && min <= 1.0
                && max >= 1.0 && max <= MAX_VALUE_LIMIT;
        if (!valid) {
            warn("Invalid seasonal-prices min/max values; restoring defaults (0.5 / 2.0).");
            min = 0.5;
            max = 2.0;
        }
        this.minValue = min;
        this.maxValue = max;
    }

    // Getters and setters for testing and runtime modification
    public boolean isPricesEnabled() { return pricesEnabled; }
    public void setPricesEnabled(boolean enabled) { this.pricesEnabled = enabled; }
    public boolean isShowPriceNotice() { return showPriceNotice; }
    public double getMinValue() { return minValue; }
    public double getMaxValue() { return maxValue; }
    public double getInSeasonValue() { return inSeasonValue; }
    public double getOffSeasonValue() { return offSeasonValue; }
    public double getWinterValue() { return winterValue; }
    public ProductValues getProductValues() { return productValues; }

    public boolean isRefuseSpoilingFood() { return refuseSpoilingFood; }
    public void setRefuseSpoilingFood(boolean refuse) { this.refuseSpoilingFood = refuse; }

    public boolean isBulkEnabled() { return bulkEnabled; }
    public void setBulkEnabled(boolean enabled) { this.bulkEnabled = enabled; }
    public double getBulkBonus() { return bulkBonus; }
    public int getBulkDailyLimit() { return bulkDailyLimit; }

    public boolean isFairsEnabled() { return fairsEnabled; }
    public void setFairsEnabled(boolean enabled) { this.fairsEnabled = enabled; }
    public List<FairOffer> getFairOffers(Season season) {
        return Collections.unmodifiableList(fairOffers.getOrDefault(season, List.of()));
    }

    public String getPrefix() { return prefix; }
}
