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

package com.sovereignseasons.logistics.mobility;

import com.sovereignseasons.logistics.config.LogisticsConfig;

import java.util.Objects;

/**
 * Pure (Bukkit-free) movement rules. Levels are vanilla potion levels: 0 = none, 1 = Slowness/Speed I, ...
 */
public class MovementModel {

    /**
     * Movement effect to apply.
     *
     * @param slowness Slowness level (0 = none)
     * @param speed    Speed level (0 = none)
     */
    public record Effect(int slowness, int speed) {
        public static final Effect NONE = new Effect(0, 0);

        public boolean isNone() {
            return slowness <= 0 && speed <= 0;
        }
    }

    private final LogisticsConfig config;

    public MovementModel(LogisticsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * @param surface         ground the moving entity is on
     * @param mount           true if the moving entity is a ridden mount, false for a player on foot
     * @param snowProtected   leather boots (player) or leather horse armor (mount), when enabled in config
     * @param blizzard        whether the entity is exposed to a winter blizzard
     * @param weightSlowness  extra Slowness from carried weight (players on foot only)
     */
    public Effect evaluate(SurfaceType surface, boolean mount, boolean snowProtected, boolean blizzard, int weightSlowness) {
        if (!config.isMobilityEnabled()) {
            return mount ? Effect.NONE : new Effect(Math.max(0, weightSlowness), 0);
        }
        SurfaceType ground = surface != null ? surface : SurfaceType.NORMAL;

        int slowness = switch (ground) {
            case SNOW -> snowProtected ? 0 : config.getSnowSlowness();
            case DEEP_SNOW -> snowProtected ? 0 : config.getDeepSnowSlowness();
            case MUD -> config.getMudSlowness();
            default -> 0;
        };
        if (blizzard && config.isBlizzardEnabled()) {
            slowness += config.getBlizzardExtraSlowness();
        }
        if (!mount) {
            slowness = Math.max(slowness, Math.max(0, weightSlowness));
        }

        int speed = 0;
        if (ground == SurfaceType.ROAD && slowness == 0) {
            speed = mount ? config.getRoadMountSpeed() : config.getRoadPlayerSpeed();
        }
        return new Effect(Math.min(5, slowness), Math.min(5, speed));
    }
}
