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

package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CropGrowthModelTest {
    private SeasonsConfig config;
    private CropGrowthModel model;
    private CropProfile wheat;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        model = new CropGrowthModel(config);
        wheat = config.getCropProfile(Material.WHEAT);
    }

    @Test
    @DisplayName("In-season crops in their ideal climate always grow")
    void idealConditionsShouldAlwaysGrow() {
        assertThat(model.growthChance(wheat, Season.SUMMER, TemperatureBand.WARM, false, false)).isEqualTo(1.0);
        assertThat(model.growthChance(wheat, Season.SPRING, TemperatureBand.MILD, false, false)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Season and climate factors multiply")
    void factorsShouldMultiply() {
        CropProfile melon = config.getCropProfile(Material.MELON_STEM);
        // Spring is out of season (0.5) and a mild climate is one band from warm (0.6)
        assertThat(model.growthChance(melon, Season.SPRING, TemperatureBand.MILD, false, false)).isCloseTo(0.3, within(1e-9));
        // Cold is two bands away from warm (0.3), in season (1.0)
        assertThat(model.growthChance(melon, Season.SUMMER, TemperatureBand.COLD, false, false)).isCloseTo(0.3, within(1e-9));
        // Freezing is far away and out of season
        assertThat(model.growthChance(melon, Season.AUTUMN, TemperatureBand.FREEZING, false, false)).isCloseTo(0.15, within(1e-9));
    }

    @Test
    @DisplayName("Greenhouses ignore season and climate; underground farms ignore only the season")
    void greenhouseAndUndergroundShouldBypass() {
        CropProfile potato = config.getCropProfile(Material.POTATOES);
        assertThat(model.growthChance(potato, Season.SUMMER, TemperatureBand.HOT, true, false)).isEqualTo(1.0);
        assertThat(model.growthChance(potato, Season.SUMMER, TemperatureBand.MILD, false, true)).isEqualTo(1.0);
        assertThat(model.growthChance(potato, Season.SUMMER, TemperatureBand.HOT, false, true)).isCloseTo(0.3, within(1e-9));
    }

    @Test
    @DisplayName("Crops without a profile, unknown climate or disabled feature grow normally")
    void missingDataShouldNotPenalize() {
        assertThat(model.growthChance(null, Season.WINTER, TemperatureBand.FREEZING, false, false)).isEqualTo(1.0);
        assertThat(model.growthChance(wheat, Season.SUMMER, null, false, false)).isEqualTo(1.0);
        assertThat(model.growthChance(wheat, null, TemperatureBand.WARM, false, false)).isEqualTo(1.0);
        config.setSeasonalGrowthEnabled(false);
        assertThat(model.growthChance(wheat, Season.WINTER, TemperatureBand.FREEZING, false, false)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Nether wart has no profile and is exempt from seasons")
    void netherWartShouldBeExempt() {
        assertThat(config.getCropProfile(Material.NETHER_WART)).isNull();
        assertThat(config.getCropProfiles()).containsKeys(Material.WHEAT, Material.SUGAR_CANE, Material.CACTUS, Material.COCOA);
    }

    @Test
    @DisplayName("Profile band distance picks the closest ideal band")
    void bandDistanceShouldUseClosestIdeal() {
        CropProfile profile = new CropProfile(EnumSet.of(Season.SPRING), EnumSet.of(TemperatureBand.COLD, TemperatureBand.HOT));
        assertThat(profile.bandDistance(TemperatureBand.MILD)).isEqualTo(1);
        assertThat(profile.bandDistance(TemperatureBand.FREEZING)).isEqualTo(1);
        assertThat(profile.bandDistance(TemperatureBand.HOT)).isZero();
        assertThat(new CropProfile(null, null).bandDistance(TemperatureBand.HOT)).isZero();
        assertThat(new CropProfile(Set.of(), Set.of()).isFertileIn(Season.WINTER)).isTrue();
    }

    @Test
    @DisplayName("Profiles load from YAML, ignoring unknown blocks, seasons and bands")
    void shouldLoadProfilesFromYaml(@TempDir File tempDir) {
        String yaml = """
                crops:
                  seasonal-growth:
                    out-of-season-factor: 7
                    adjacent-band-factor: -1
                    profiles:
                      WHEAT: { seasons: [winter, verano, nope], bands: [freezing, lava] }
                      NOT_A_BLOCK: { seasons: [spring], bands: [mild] }
                """;
        SeasonsConfig loaded = new SeasonsConfig(null);
        loaded.load(tempDir, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(loaded.getOutOfSeasonFactor()).isEqualTo(1.0);
        assertThat(loaded.getAdjacentBandFactor()).isZero();
        assertThat(loaded.getCropProfiles()).containsOnlyKeys(Material.WHEAT);
        CropProfile profile = loaded.getCropProfile(Material.WHEAT);
        assertThat(profile.fertileSeasons()).containsExactlyInAnyOrder(Season.WINTER, Season.SUMMER);
        assertThat(profile.idealBands()).containsExactly(TemperatureBand.FREEZING);
    }

    @Test
    @DisplayName("Bundled config.yml matches the built-in defaults for every new section")
    void bundledConfigShouldMatchDefaults(@TempDir File tempDir) {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(bundled).isNotNull();
        SeasonsConfig loaded = new SeasonsConfig(null);
        loaded.load(tempDir, bundled);
        SeasonsConfig defaults = new SeasonsConfig(null);

        assertThat(loaded.getCropProfiles()).isEqualTo(defaults.getCropProfiles());
        for (Season season : Season.values()) {
            assertThat(loaded.getRainChance(season)).isEqualTo(defaults.getRainChance(season));
            assertThat(loaded.getSeasonTemperatureOffset(season)).isEqualTo(defaults.getSeasonTemperatureOffset(season));
        }
        assertThat(loaded.getSpringTwinChance()).isEqualTo(defaults.getSpringTwinChance());
        assertThat(loaded.isColdDamage()).isFalse();
        assertThat(loaded.getColdFreezeTicks()).isEqualTo(120);
        assertThat(loaded.getHeatExhaustionPerSecond()).isEqualTo(0.05);
        assertThat(loaded.isAnnounceSeasonChange()).isTrue();
        assertThat(loaded.getUndergroundY()).isEqualTo(48);
    }
}
