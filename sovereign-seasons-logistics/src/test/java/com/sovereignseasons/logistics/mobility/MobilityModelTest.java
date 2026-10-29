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

import com.sovereignseasons.api.Season;
import com.sovereignseasons.logistics.config.LogisticsConfig;
import com.sovereignseasons.logistics.mobility.MovementModel.Effect;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MobilityModelTest {
    private LogisticsConfig config;
    private SurfaceClassifier classifier;
    private MovementModel movement;

    @BeforeEach
    void setup() {
        config = new LogisticsConfig(null);
        classifier = new SurfaceClassifier(config);
        movement = new MovementModel(config);
    }

    @Test
    @DisplayName("Snow layers are shallow or deep; snow blocks and powder snow are deep")
    void shouldClassifySnow() {
        assertThat(classifier.classify(Material.SNOW, 1, Material.GRASS_BLOCK, false, Season.WINTER)).isEqualTo(SurfaceType.SNOW);
        assertThat(classifier.classify(Material.SNOW, 2, Material.GRASS_BLOCK, false, Season.WINTER)).isEqualTo(SurfaceType.SNOW);
        assertThat(classifier.classify(Material.SNOW, 3, Material.GRASS_BLOCK, false, Season.WINTER)).isEqualTo(SurfaceType.DEEP_SNOW);
        assertThat(classifier.classify(Material.SNOW_BLOCK, 0, Material.DIRT, false, Season.WINTER)).isEqualTo(SurfaceType.DEEP_SNOW);
        assertThat(classifier.classify(Material.POWDER_SNOW, 0, Material.DIRT, false, Season.WINTER)).isEqualTo(SurfaceType.DEEP_SNOW);
    }

    @Test
    @DisplayName("Thin snow over a road still counts as road; deep snow must be cleared")
    void roadsShouldIgnoreThinSnow() {
        assertThat(classifier.classify(Material.SNOW, 2, Material.DIRT_PATH, false, Season.WINTER)).isEqualTo(SurfaceType.ROAD);
        assertThat(classifier.classify(Material.SNOW, 3, Material.DIRT_PATH, false, Season.WINTER)).isEqualTo(SurfaceType.DEEP_SNOW);
        assertThat(classifier.classify(Material.GRAVEL, 0, Material.DIRT, true, Season.AUTUMN)).isEqualTo(SurfaceType.ROAD);
        assertThat(classifier.classify(Material.OAK_PLANKS, 0, Material.DIRT, false, Season.SPRING)).isEqualTo(SurfaceType.ROAD);
        assertThat(classifier.classify(Material.WHITE_CONCRETE, 0, Material.DIRT, false, Season.SPRING)).isEqualTo(SurfaceType.ROAD);
    }

    @Test
    @DisplayName("Mud appears on soil only when it rains in spring or autumn; mud blocks always")
    void shouldClassifyMud() {
        assertThat(classifier.classify(Material.GRASS_BLOCK, 0, Material.DIRT, true, Season.AUTUMN)).isEqualTo(SurfaceType.MUD);
        assertThat(classifier.classify(Material.FARMLAND, 0, Material.DIRT, true, Season.SPRING)).isEqualTo(SurfaceType.MUD);
        assertThat(classifier.classify(Material.GRASS_BLOCK, 0, Material.DIRT, false, Season.AUTUMN)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(Material.GRASS_BLOCK, 0, Material.DIRT, true, Season.SUMMER)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(Material.GRASS_BLOCK, 0, Material.DIRT, true, null)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(Material.MUD, 0, Material.DIRT, false, Season.SUMMER)).isEqualTo(SurfaceType.MUD);
        assertThat(classifier.classify(Material.SAND, 0, Material.SAND, true, Season.AUTUMN)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(null, 0, null, true, Season.AUTUMN)).isEqualTo(SurfaceType.NORMAL);
    }

    @Test
    @DisplayName("Disabled features stop classifying their surfaces")
    void disabledFeaturesShouldNotClassify() {
        config.setSnowEnabled(false);
        config.setMudEnabled(false);
        config.setRoadsEnabled(false);
        assertThat(classifier.classify(Material.SNOW, 4, Material.GRASS_BLOCK, false, Season.WINTER)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(Material.MUD, 0, Material.DIRT, true, Season.AUTUMN)).isEqualTo(SurfaceType.NORMAL);
        assertThat(classifier.classify(Material.GRAVEL, 0, Material.DIRT, true, Season.AUTUMN)).isEqualTo(SurfaceType.NORMAL);
    }

    @Test
    @DisplayName("Snow slows players and mounts unless they wear leather; mud slows everyone")
    void snowAndMudEffects() {
        assertThat(movement.evaluate(SurfaceType.SNOW, false, false, false, 0)).isEqualTo(new Effect(1, 0));
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, false, false, false, 0)).isEqualTo(new Effect(2, 0));
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, false, true, false, 0)).isEqualTo(Effect.NONE);
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, true, false, false, 0)).isEqualTo(new Effect(2, 0));
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, true, true, false, 0)).isEqualTo(Effect.NONE);
        assertThat(movement.evaluate(SurfaceType.MUD, false, true, false, 0)).isEqualTo(new Effect(1, 0));
        assertThat(movement.evaluate(SurfaceType.NORMAL, false, false, false, 0)).isEqualTo(Effect.NONE);
        assertThat(movement.evaluate(null, false, false, false, 0)).isEqualTo(Effect.NONE);
    }

    @Test
    @DisplayName("Roads speed up mounts only, unless a blizzard is blowing")
    void roadEffects() {
        assertThat(movement.evaluate(SurfaceType.ROAD, true, false, false, 0)).isEqualTo(new Effect(0, 1));
        assertThat(movement.evaluate(SurfaceType.ROAD, false, false, false, 0)).isEqualTo(Effect.NONE);
        assertThat(movement.evaluate(SurfaceType.ROAD, true, false, true, 0)).isEqualTo(new Effect(1, 0));
        config.setRoadPlayerSpeed(1);
        assertThat(movement.evaluate(SurfaceType.ROAD, false, false, false, 0)).isEqualTo(new Effect(0, 1));
    }

    @Test
    @DisplayName("Blizzards add slowness on any ground, even with leather")
    void blizzardEffects() {
        assertThat(movement.evaluate(SurfaceType.NORMAL, false, false, true, 0)).isEqualTo(new Effect(1, 0));
        assertThat(movement.evaluate(SurfaceType.SNOW, false, false, true, 0)).isEqualTo(new Effect(2, 0));
        assertThat(movement.evaluate(SurfaceType.SNOW, false, true, true, 0)).isEqualTo(new Effect(1, 0));
        config.setBlizzardEnabled(false);
        assertThat(movement.evaluate(SurfaceType.NORMAL, false, false, true, 0)).isEqualTo(Effect.NONE);
    }

    @Test
    @DisplayName("Carried weight slows players on foot but never their mounts")
    void weightEffects() {
        assertThat(movement.evaluate(SurfaceType.NORMAL, false, false, false, 2)).isEqualTo(new Effect(2, 0));
        assertThat(movement.evaluate(SurfaceType.SNOW, false, false, false, 2)).isEqualTo(new Effect(2, 0));
        assertThat(movement.evaluate(SurfaceType.NORMAL, true, false, false, 2)).isEqualTo(Effect.NONE);
        assertThat(movement.evaluate(SurfaceType.ROAD, false, false, false, 1)).isEqualTo(new Effect(1, 0));
        config.setMobilityEnabled(false);
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, false, false, true, 1)).isEqualTo(new Effect(1, 0));
        assertThat(movement.evaluate(SurfaceType.DEEP_SNOW, true, false, true, 1)).isEqualTo(Effect.NONE);
    }
}
