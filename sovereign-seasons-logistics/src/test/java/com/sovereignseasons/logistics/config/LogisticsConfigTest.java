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
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class LogisticsConfigTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Built-in defaults match the documented behaviour")
    void shouldHaveDocumentedDefaults() {
        LogisticsConfig config = new LogisticsConfig(null);
        assertThat(config.isMobilityEnabled()).isTrue();
        assertThat(config.isWeightEnabled()).isFalse();
        assertThat(config.getMounts()).contains(EntityType.HORSE, EntityType.MULE, EntityType.LLAMA);
        assertThat(config.getMudSeasons()).containsExactlyInAnyOrder(Season.SPRING, Season.AUTUMN);
        assertThat(config.getRoadMaterials()).contains(Material.DIRT_PATH, Material.GRAVEL, Material.OAK_PLANKS, Material.WHITE_CONCRETE);
        assertThat(config.getRoadMaterials()).doesNotContain(Material.GRASS_BLOCK, Material.SAND);
        assertThat(config.getPackableMaterials()).contains(Material.CARROT, Material.POTATO).doesNotContain(Material.WHEAT);
        assertThat(config.getWeightRules()).hasSize(4);
        assertThat(config.getItemsPerCrate()).isEqualTo(9);
        assertThat(config.isCrateModelData()).isTrue();
    }

    @Test
    @DisplayName("The bundled config.yml loads cleanly and matches the defaults")
    void bundledConfigShouldMatchDefaults(@TempDir File tempDir) {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(bundled).isNotNull();
        LogisticsConfig loaded = new LogisticsConfig(null);
        loaded.load(tempDir, bundled);
        LogisticsConfig defaults = new LogisticsConfig(null);

        assertThat(loaded.getMounts()).isEqualTo(defaults.getMounts());
        assertThat(loaded.getMudMaterials()).isEqualTo(defaults.getMudMaterials());
        assertThat(loaded.getRoadMaterials()).isEqualTo(defaults.getRoadMaterials());
        assertThat(loaded.getPackableMaterials()).isEqualTo(defaults.getPackableMaterials());
        assertThat(loaded.getWeightRules()).hasSameSizeAs(defaults.getWeightRules());
        for (int i = 0; i < defaults.getWeightRules().size(); i++) {
            assertThat(loaded.getWeightRules().get(i).pattern().pattern()).isEqualTo(defaults.getWeightRules().get(i).pattern().pattern());
            assertThat(loaded.getWeightRules().get(i).weight()).isEqualTo(defaults.getWeightRules().get(i).weight());
        }
        assertThat(loaded.isWeightEnabled()).isFalse();
        assertThat(loaded.getPackedDecayRate()).isEqualTo(0.5);
        assertThat(loaded.getIceRadius()).isEqualTo(48);
    }

    @Test
    @DisplayName("Custom YAML is bounded and malformed entries are ignored")
    void shouldBoundCustomValues(@TempDir File tempDir) {
        LogisticsConfig config = new LogisticsConfig(null);
        config.load(tempDir, yaml("""
                mobility:
                  interval-ticks: 1
                  mounts: [HORSE, NOT_A_MOB]
                  snow:
                    slowness: 99
                    deep-layers: 0
                  mud:
                    seasons: [verano, nope]
                  roads:
                    materials: [GRAVEL, NOT_A_BLOCK, DIAMOND]
                weight:
                  enabled: true
                  capacity: -5
                  heavy-threshold: 0.9
                  overloaded-threshold: 0.2
                  rules:
                    - pattern: "FEATHER"
                      weight: 3
                    - pattern: "[unclosed"
                      weight: 1
                    - pattern: "STICK"
                      weight: lots
                    - weight: 2
                crates:
                  items-per-crate: 1000
                  packed-decay-rate: 7
                  packable: [CARROT, NOT_AN_ITEM]
                ice:
                  radius: 100000
                """));

        assertThat(config.getMobilityIntervalTicks()).isEqualTo(5);
        assertThat(config.getMounts()).containsExactly(EntityType.HORSE);
        assertThat(config.getSnowSlowness()).isEqualTo(5);
        assertThat(config.getDeepSnowLayers()).isEqualTo(1);
        assertThat(config.getMudSeasons()).containsExactly(Season.SUMMER);
        assertThat(config.getRoadMaterials()).containsExactly(Material.GRAVEL);
        assertThat(config.isWeightEnabled()).isTrue();
        assertThat(config.getWeightCapacity()).isEqualTo(1000.0);
        assertThat(config.getHeavyThreshold()).isEqualTo(0.6);
        assertThat(config.getWeightRules()).hasSize(1);
        assertThat(config.getWeightRules().get(0).weight()).isEqualTo(3.0);
        assertThat(config.getItemsPerCrate()).isEqualTo(64);
        assertThat(config.getPackedDecayRate()).isEqualTo(1.0);
        assertThat(config.getPackableMaterials()).containsExactly(Material.CARROT);
        assertThat(config.getIceRadius()).isEqualTo(128);
    }

    @Test
    @DisplayName("Corrupted YAML keeps safe defaults instead of crashing")
    void shouldSurviveCorruptedFile(@TempDir File tempDir) throws Exception {
        Files.writeString(new File(tempDir, "config.yml").toPath(), "mobility: [unclosed\n  :: garbage", StandardCharsets.UTF_8);
        LogisticsConfig config = new LogisticsConfig(null);
        config.load(tempDir, null);
        assertThat(config.isMobilityEnabled()).isTrue();
        assertThat(config.getItemsPerCrate()).isEqualTo(9);
        assertThat(config.getWeightRules()).hasSize(4);
    }
}
