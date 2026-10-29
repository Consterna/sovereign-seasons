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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class PantryConfigTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Built-in defaults match the documented values")
    void shouldLoadDefaults(@TempDir File tempDir) {
        PantryConfig config = new PantryConfig(null);
        config.load(tempDir, null);

        assertThat(config.isDecayEnabled()).isTrue();
        assertThat(config.getTicksPerDay()).isEqualTo(24000L);
        assertThat(config.getBandRate(TemperatureBand.HOT)).isEqualTo(2.0);
        assertThat(config.getBandRate(null)).isEqualTo(1.0);
        assertThat(config.getShelfLifeDays(Material.BREAD)).isEqualTo(7.0);
        assertThat(config.getShelfLifeDays(Material.GOLDEN_APPLE)).isZero();
        assertThat(config.getIceMaterials()).contains(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);
        assertThat(config.getFreshAbove()).isEqualTo(0.5);
        assertThat(config.getStaleAbove()).isEqualTo(0.15);
    }

    @Test
    @DisplayName("The bundled config.yml loads cleanly and matches the defaults")
    void bundledConfigShouldMatchDefaults(@TempDir File tempDir) {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(bundled).isNotNull();
        PantryConfig config = new PantryConfig(null);
        config.load(tempDir, bundled);

        PantryConfig defaults = new PantryConfig(null);
        assertThat(config.getShelfLifeTable()).isEqualTo(defaults.getShelfLifeTable());
        assertThat(config.getSmokedMultiplier()).isEqualTo(0.25);
        assertThat(config.getMinRoofBlocks()).isEqualTo(3);
        assertThat(new File(tempDir, "config.yml")).exists();
    }

    @Test
    @DisplayName("Custom YAML replaces the shelf-life table and enforces bounds")
    void shouldLoadCustomYamlWithBounds(@TempDir File tempDir) {
        PantryConfig config = new PantryConfig(null);
        config.load(tempDir, yaml("""
                decay:
                  ticks-per-day: 5
                  min-evaluation-interval-ticks: -10
                  expiry-granularity-ticks: 0
                  band-rates:
                    hot: 999
                    cold: -3
                shelf-life-days:
                  BREAD: 2
                  NOT_A_REAL_ITEM: 4
                  APPLE: -1
                cellar:
                  multiplier: 7
                  min-roof-blocks: 0
                  ice-radius: 50
                  deep-y: -1000
                preservation:
                  smoked-multiplier: .nan
                consumption:
                  stale-hunger-seconds: -5
                """));

        assertThat(config.getTicksPerDay()).isEqualTo(100L);
        assertThat(config.getMinEvaluationIntervalTicks()).isZero();
        assertThat(config.getExpiryGranularityTicks()).isEqualTo(1L);
        assertThat(config.getBandRate(TemperatureBand.HOT)).isEqualTo(10.0);
        assertThat(config.getBandRate(TemperatureBand.COLD)).isZero();
        assertThat(config.getShelfLifeTable()).containsOnlyKeys(Material.BREAD);
        assertThat(config.getCellarMultiplier()).isEqualTo(1.0);
        assertThat(config.getMinRoofBlocks()).isEqualTo(1);
        assertThat(config.getIceRadius()).isEqualTo(8);
        assertThat(config.getDeepY()).isEqualTo(-64);
        assertThat(config.getSmokedMultiplier()).isEqualTo(0.25);
        assertThat(config.getStaleHungerSeconds()).isZero();
    }

    @Test
    @DisplayName("Container multipliers, compost and warnings load with bounds")
    void shouldLoadContainersAndCompost(@TempDir File tempDir) {
        PantryConfig config = new PantryConfig(null);
        assertThat(config.getContainerMultiplier("barrel")).isEqualTo(0.75);
        assertThat(config.getContainerMultiplier("BARREL")).isEqualTo(0.75);
        assertThat(config.getContainerMultiplier("chest")).isEqualTo(1.0);
        assertThat(config.getContainerMultiplier(null)).isEqualTo(1.0);

        config.load(tempDir, yaml("""
                containers:
                  chest: 1.2
                  shulker_box: 99
                compost:
                  enabled: false
                  rotten-flesh-chance: 3
                warnings:
                  spoilage: false
                """));
        assertThat(config.getContainerMultiplier("barrel")).isEqualTo(1.0); // section replaced
        assertThat(config.getContainerMultiplier("chest")).isEqualTo(1.2);
        assertThat(config.getContainerMultiplier("shulker_box")).isEqualTo(2.0);
        assertThat(config.isCompostEnabled()).isFalse();
        assertThat(config.getCompostChance()).isEqualTo(1.0);
        assertThat(config.isSpoilageWarnings()).isFalse();
    }

    @Test
    @DisplayName("Invalid stage thresholds fall back to defaults")
    void shouldRejectInvalidStageThresholds(@TempDir File tempDir) {
        PantryConfig config = new PantryConfig(null);
        config.load(tempDir, yaml("""
                stages:
                  fresh-above: 0.1
                  stale-above: 0.6
                """));
        assertThat(config.getFreshAbove()).isEqualTo(0.5);
        assertThat(config.getStaleAbove()).isEqualTo(0.15);

        config.setStageThresholds(0.7, 0.3);
        assertThat(config.getFreshAbove()).isEqualTo(0.7);
        assertThat(config.getStaleAbove()).isEqualTo(0.3);
        config.setStageThresholds(1.0, 0.3);
        assertThat(config.getFreshAbove()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Corrupted YAML keeps safe defaults instead of crashing")
    void shouldSurviveCorruptedFile(@TempDir File tempDir) throws Exception {
        Files.writeString(new File(tempDir, "config.yml").toPath(), "decay: [unclosed\n  :: garbage", StandardCharsets.UTF_8);
        PantryConfig config = new PantryConfig(null);
        config.load(tempDir, null);
        assertThat(config.isDecayEnabled()).isTrue();
        assertThat(config.getShelfLifeDays(Material.BREAD)).isEqualTo(7.0);
    }

    @Test
    @DisplayName("Shelf-life setter removes entries for non-positive or non-finite values")
    void shelfLifeSetterShouldValidate() {
        PantryConfig config = new PantryConfig(null);
        config.setShelfLifeDays(Material.BREAD, 0);
        config.setShelfLifeDays(Material.APPLE, Double.POSITIVE_INFINITY);
        config.setShelfLifeDays(Material.CARROT, 1e9);
        config.setShelfLifeDays(null, 3);
        assertThat(config.getShelfLifeDays(Material.BREAD)).isZero();
        assertThat(config.getShelfLifeDays(Material.APPLE)).isZero();
        assertThat(config.getShelfLifeDays(Material.CARROT)).isEqualTo(10_000.0);
    }
}
