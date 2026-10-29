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

package com.sovereignseasons.core.comfort;

import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;

import java.util.Objects;

/**
 * Pure (Bukkit-free) rules deciding whether a player feels cold or heat.
 * Deliberately gentle: only the extreme bands matter, and vanilla answers (leather, fire, shade, water) protect.
 */
public class ComfortModel {
    /** Sky light at head level from which a player counts as being outdoors. */
    public static final int OUTDOOR_SKY_LIGHT = 10;
    /** Full sky light: direct sunlight. */
    public static final int DIRECT_SUN_SKY_LIGHT = 15;

    public enum Exposure { NONE, COLD, HEAT }

    /**
     * Snapshot of the player's situation.
     *
     * @param band           ambient temperature band at the player's location
     * @param skyLight       sky light level at head height (0-15)
     * @param blockLight     block light level at head height (0-15), e.g. torches and fires
     * @param wearsLeather   whether any equipped armor piece is leather (vanilla freeze protection)
     * @param daytime        whether it is daytime in the world
     * @param raining        whether it is raining or snowing in the world
     * @param inWater        whether the player is in water
     * @param fireResistant  whether the player has fire resistance
     */
    public record Situation(
            TemperatureBand band,
            int skyLight,
            int blockLight,
            boolean wearsLeather,
            boolean daytime,
            boolean raining,
            boolean inWater,
            boolean fireResistant
    ) {}

    private final SeasonsConfig config;

    public ComfortModel(SeasonsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    public Exposure evaluate(Situation situation) {
        if (!config.isComfortEnabled() || situation == null || situation.band() == null) {
            return Exposure.NONE;
        }
        if (config.isColdEnabled()
                && situation.band() == TemperatureBand.FREEZING
                && situation.skyLight() >= OUTDOOR_SKY_LIGHT
                && situation.blockLight() < config.getWarmthBlockLight()
                && !situation.wearsLeather()) {
            return Exposure.COLD;
        }
        if (config.isHeatEnabled()
                && situation.band() == TemperatureBand.HOT
                && situation.daytime()
                && !situation.raining()
                && situation.skyLight() >= DIRECT_SUN_SKY_LIGHT
                && !situation.inWater()
                && !situation.fireResistant()) {
            return Exposure.HEAT;
        }
        return Exposure.NONE;
    }

    /**
     * Freeze ticks to apply while cold: just below the vanilla damage threshold unless cold damage is enabled.
     */
    public int targetFreezeTicks(int vanillaMaxFreezeTicks) {
        int max = Math.max(1, vanillaMaxFreezeTicks);
        if (config.isColdDamage()) {
            return max + 20;
        }
        return Math.min(config.getColdFreezeTicks(), max - 1);
    }

    /**
     * Extra exhaustion to add per evaluation while exposed to heat.
     */
    public float heatExhaustion(int intervalTicks) {
        return (float) (config.getHeatExhaustionPerSecond() * (intervalTicks / 20.0));
    }
}
