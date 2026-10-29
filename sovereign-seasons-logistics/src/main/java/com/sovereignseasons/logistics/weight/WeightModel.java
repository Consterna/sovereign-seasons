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

package com.sovereignseasons.logistics.weight;

import com.sovereignseasons.logistics.config.LogisticsConfig;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure (Bukkit-free) carried-weight rules.
 * Only what the player carries counts: pack animals, minecarts, chests and the ender chest never do.
 */
public class WeightModel {
    public enum Load {
        LIGHT("Ligera"),
        HEAVY("Pesada"),
        OVERLOADED("Sobrecargada");

        private final String displayName;

        Load(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final LogisticsConfig config;
    private final Map<Material, Double> cache = new EnumMap<>(Material.class);

    public WeightModel(LogisticsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Weight of a single item of this material (first matching rule, else the default weight).
     */
    public synchronized double itemWeight(Material material) {
        if (material == null || material.isAir()) {
            return 0.0;
        }
        return cache.computeIfAbsent(material, mat -> {
            String name = mat.name();
            for (LogisticsConfig.WeightRule rule : config.getWeightRules()) {
                if (rule.pattern().matcher(name).matches()) {
                    return rule.weight();
                }
            }
            return config.getDefaultItemWeight();
        });
    }

    /**
     * Clears memoized weights (after a config reload).
     */
    public synchronized void clearCache() {
        cache.clear();
    }

    public double stackWeight(Material material, int amount) {
        return itemWeight(material) * Math.max(0, amount);
    }

    /**
     * Weight of items carried inside a shulker box or bundle (counted at a reduced factor).
     */
    public double nestedWeight(double contentsWeight) {
        return Math.max(0.0, contentsWeight) * config.getContainerContentsFactor();
    }

    /**
     * Weight of {@code crates} crates each holding {@code perCrate} items of {@code content}: packed goods weigh less.
     */
    public double crateWeight(Material content, int perCrate, int crates) {
        return stackWeight(content, perCrate) * Math.max(0, crates) * config.getCrateWeightFactor();
    }

    public Load load(double totalWeight) {
        double ratio = totalWeight / config.getWeightCapacity();
        if (ratio > config.getOverloadedThreshold()) {
            return Load.OVERLOADED;
        }
        if (ratio > config.getHeavyThreshold()) {
            return Load.HEAVY;
        }
        return Load.LIGHT;
    }

    /**
     * Slowness level caused by the carried weight (0 when disabled or light).
     */
    public int slownessLevel(double totalWeight) {
        if (!config.isWeightEnabled()) {
            return 0;
        }
        return switch (load(totalWeight)) {
            case LIGHT -> 0;
            case HEAVY -> 1;
            case OVERLOADED -> 2;
        };
    }
}
