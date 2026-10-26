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

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCorruptionAndBoundStressTest {

    private void writeConfig(File file, String yaml) throws IOException {
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Non-block wither-material (DIAMOND_SWORD) falls back to DEAD_BUSH preventing block.setType crash")
    void shouldRejectNonBlockWitherMaterial(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        String yaml = """
                crops:
                  wither-material: DIAMOND_SWORD
                """;
        writeConfig(new File(tempDir, "config.yml"), yaml);
        config.load(tempDir, null);

        assertThat(config.getWitherMaterial()).isEqualTo(Material.DEAD_BUSH);
        assertThat(config.getWitherMaterial().isBlock()).isTrue();
    }

    @Test
    @DisplayName("Invalid or unknown wither-material name falls back safely to DEAD_BUSH")
    void shouldHandleUnknownWitherMaterial(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        String yaml = """
                crops:
                  wither-material: NOT_A_REAL_MATERIAL_XYZ
                """;
        writeConfig(new File(tempDir, "config.yml"), yaml);
        config.load(tempDir, null);

        assertThat(config.getWitherMaterial()).isEqualTo(Material.DEAD_BUSH);
    }

    @Test
    @DisplayName("NaN and Infinity winter-wither-chance values normalize safely to 0.08")
    void shouldNormalizeNaNAndInfinityWitherChance(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        // NaN via setter
        config.setWinterWitherChance(Double.NaN);
        assertThat(config.getWinterWitherChance()).isEqualTo(0.08);

        // Infinity via setter
        config.setWinterWitherChance(Double.POSITIVE_INFINITY);
        assertThat(config.getWinterWitherChance()).isEqualTo(0.08);

        // Clamping out of range [0.0, 1.0]
        config.setWinterWitherChance(1.5);
        assertThat(config.getWinterWitherChance()).isEqualTo(1.0);

        config.setWinterWitherChance(-0.5);
        assertThat(config.getWinterWitherChance()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Extreme max-ceiling-height and heat-radius values are clamped to safe maximum bounds")
    void shouldClampExtremeSearchBounds(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        String yaml = """
                greenhouse:
                  max-ceiling-height: 99999
                  heat-radius: 500
                  cache-ttl-seconds: 9999999
                  cache-max-size: 100000000
                """;
        writeConfig(new File(tempDir, "config.yml"), yaml);
        config.load(tempDir, null);

        // Max world height in 1.20+ is 384
        assertThat(config.getMaxCeilingHeight()).isLessThanOrEqualTo(384);
        // Max heat radius capped to prevent cubic explosion
        assertThat(config.getHeatRadius()).isLessThanOrEqualTo(32);
        // TTL capped to 1 day (86400s)
        assertThat(config.getCacheTtlSeconds()).isLessThanOrEqualTo(86400);
        // Max cache size capped to 1,000,000
        assertThat(config.getCacheMaxSize()).isLessThanOrEqualTo(1_000_000);
    }

    @Test
    @DisplayName("Non-block entries in allowed-heat-sources and allowed-glass-materials are ignored")
    void shouldIgnoreNonBlockItemsInMaterialLists(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        String yaml = """
                greenhouse:
                  allowed-glass-materials:
                    - GLASS
                    - DIAMOND_SWORD
                    - STICK
                  allowed-heat-sources:
                    - TORCH
                    - APPLE
                    - COOKED_BEEF
                """;
        writeConfig(new File(tempDir, "config.yml"), yaml);
        config.load(tempDir, null);

        assertThat(config.getAllowedGlassMaterials()).containsExactly(Material.GLASS);
        assertThat(config.getAllowedHeatSources()).containsExactly(Material.TORCH);
    }

    @Test
    @DisplayName("Null messages.prefix in YAML falls back to valid default without string concatenation null")
    void shouldFallbackWhenPrefixIsNull(@TempDir File tempDir) throws IOException {
        SeasonsConfig config = new SeasonsConfig(null);

        String yaml = """
                messages:
                  prefix: ~
                """;
        writeConfig(new File(tempDir, "config.yml"), yaml);
        config.load(tempDir, null);

        assertThat(config.getPrefix()).isNotNull();
        assertThat(config.getPrefix()).isNotEmpty();
    }
}
