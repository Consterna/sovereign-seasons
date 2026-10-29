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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceConfigTest {

    private static CommerceConfig loaded(File dir, String yaml) throws Exception {
        Files.writeString(new File(dir, "config.yml").toPath(), yaml, StandardCharsets.UTF_8);
        CommerceConfig config = new CommerceConfig(null);
        config.load(dir, null);
        return config;
    }

    @Test
    @DisplayName("Built-in defaults match the documented behaviour")
    void shouldHaveDocumentedDefaults() {
        CommerceConfig config = new CommerceConfig(null);
        ProductValues values = config.getProductValues();
        assertThat(config.isPricesEnabled()).isTrue();
        assertThat(values.value(Material.CARROT, Season.WINTER)).isEqualTo(1.4);
        assertThat(values.value(Material.CARROT, Season.AUTUMN)).isEqualTo(0.8);
        assertThat(values.value(Material.CARROT, Season.SUMMER)).isEqualTo(1.0);
        assertThat(values.value(Material.WHITE_WOOL, Season.WINTER)).isEqualTo(1.25);
        assertThat(values.value(Material.BLACK_WOOL, Season.SUMMER)).isEqualTo(0.85);
        assertThat(values.value(Material.COAL, Season.WINTER)).isEqualTo(1.3);
        assertThat(values.value(Material.DIAMOND, Season.WINTER)).isEqualTo(1.0);
        assertThat(config.getMinValue()).isEqualTo(0.5);
        assertThat(config.getMaxValue()).isEqualTo(2.0);
        assertThat(config.getBulkBonus()).isEqualTo(1.15);
        assertThat(config.getBulkDailyLimit()).isEqualTo(8);
        assertThat(config.isRefuseSpoilingFood()).isTrue();
        assertThat(config.getFairOffers(Season.SUMMER)).extracting(CommerceConfig.FairOffer::item)
                .containsExactly(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);
        assertThat(config.getFairOffers(Season.WINTER)).hasSize(3);
    }

    @Test
    @DisplayName("The bundled config.yml loads cleanly and matches the defaults")
    void bundledConfigShouldMatchDefaults(@TempDir File tempDir) {
        InputStream bundled = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertThat(bundled).isNotNull();
        CommerceConfig loaded = new CommerceConfig(null);
        loaded.load(tempDir, bundled);
        CommerceConfig defaults = new CommerceConfig(null);

        assertThat(loaded.getProductValues().trackedProducts()).isEqualTo(defaults.getProductValues().trackedProducts());
        for (Material product : defaults.getProductValues().trackedProducts()) {
            for (Season season : Season.values()) {
                assertThat(loaded.getProductValues().value(product, season))
                        .as(product + " in " + season)
                        .isEqualTo(defaults.getProductValues().value(product, season));
            }
        }
        for (Season season : Season.values()) {
            assertThat(loaded.getFairOffers(season)).isEqualTo(defaults.getFairOffers(season));
        }
        assertThat(loaded.getBulkBonus()).isEqualTo(defaults.getBulkBonus());
        assertThat(loaded.getBulkDailyLimit()).isEqualTo(defaults.getBulkDailyLimit());
        assertThat(loaded.getPrefix()).isEqualTo(defaults.getPrefix());
    }

    @Test
    @DisplayName("A malformed file keeps the defaults instead of disabling the plugin")
    void malformedFileKeepsDefaults(@TempDir File tempDir) throws Exception {
        CommerceConfig config = loaded(tempDir, "seasonal-prices: [unclosed\n  : :");
        assertThat(config.isPricesEnabled()).isTrue();
        assertThat(config.getProductValues().value(Material.CARROT, Season.WINTER)).isEqualTo(1.4);
    }

    @Test
    @DisplayName("Out-of-range and invalid values are clamped or replaced by the defaults")
    void clampsInvalidValues(@TempDir File tempDir) throws Exception {
        CommerceConfig config = loaded(tempDir, """
                seasonal-prices:
                  min-value: 1.5
                  max-value: 0.2
                  crops:
                    winter: -3
                    in-season: 99
                    products:
                      CARROT: [winter, nonsense]
                      NOT_A_ITEM: [spring]
                  goods:
                    COAL: { winter: 0, summer: 50 }
                    "*_LOG": { winter: 1.2 }
                    BROKEN: 3
                bulk-sales:
                  bonus: 0.2
                  max-crates-per-villager-per-day: -4
                fairs:
                  offers:
                    summer:
                      - { item: ICE, amount: 500, price: 900, max-uses: 0 }
                      - { item: PACKED_ICE, amount: 500, price: 900 }
                      - { item: EMERALD, amount: 1, price: 1 }
                      - { item: NOT_A_ITEM }
                    nonsense:
                      - { item: ICE }
                """);
        assertThat(config.getMinValue()).isEqualTo(0.5);
        assertThat(config.getMaxValue()).isEqualTo(2.0);
        ProductValues values = config.getProductValues();
        assertThat(values.value(Material.CARROT, Season.WINTER)).as("in season, so clamped in-season value").isEqualTo(10.0);
        assertThat(values.value(Material.CARROT, Season.SUMMER)).isEqualTo(1.0);
        assertThat(values.isTracked(Material.WHEAT)).as("a crop list replaces the defaults").isFalse();
        assertThat(values.value(Material.COAL, Season.WINTER)).isEqualTo(1.0);
        assertThat(values.value(Material.COAL, Season.SUMMER)).isEqualTo(10.0);
        assertThat(values.value(Material.OAK_LOG, Season.WINTER)).isEqualTo(1.2);
        assertThat(values.value(Material.SPRUCE_LOG, Season.WINTER)).isEqualTo(1.2);
        assertThat(values.isTracked(Material.WHITE_WOOL)).as("a goods list replaces the defaults").isFalse();
        assertThat(config.getWinterValue()).isEqualTo(1.4);
        assertThat(config.getBulkBonus()).isEqualTo(1.0);
        assertThat(config.getBulkDailyLimit()).isZero();
        assertThat(config.getFairOffers(Season.SUMMER))
                .containsExactly(new CommerceConfig.FairOffer(Material.PACKED_ICE, 64, 64, 4));
        assertThat(config.getFairOffers(Season.SPRING)).as("seasons left out keep their defaults").hasSize(4);
    }

    @Test
    @DisplayName("Untradeable items, non-numeric values and crop/goods clashes are handled")
    void rejectsUntradeableOffersAndClashes(@TempDir File tempDir) throws Exception {
        CommerceConfig config = loaded(tempDir, """
                seasonal-prices:
                  crops:
                    products:
                      COAL: [winter]
                fairs:
                  offers:
                    winter:
                      - { item: AIR, amount: 1, price: 1 }
                      - { item: GLASS, amount: 8, price: "gratis" }
                      - { item: GLASS, amount: 8, price: 2.5 }
                      - { item: GLASS, amount: 8, price: 4294967296 }
                      - { item: CAMPFIRE, amount: 1, price: 2 }
                """);
        assertThat(config.getFairOffers(Season.WINTER))
                .containsExactly(new CommerceConfig.FairOffer(Material.CAMPFIRE, 1, 2, 4));
        // COAL is a default good; listed as a crop it must follow the crop rules instead of being ignored
        assertThat(config.getProductValues().value(Material.COAL, Season.WINTER)).isEqualTo(0.8);
        assertThat(config.getProductValues().value(Material.COAL, Season.SUMMER)).isEqualTo(1.0);
        assertThat(CommerceConfig.expand("*")).doesNotContain(Material.AIR, Material.EMERALD).isNotEmpty();
    }

    @Test
    @DisplayName("Wildcards expand to every matching item and nothing else")
    void expandsWildcards() {
        assertThat(CommerceConfig.expand("*_WOOL")).contains(Material.WHITE_WOOL, Material.BLACK_WOOL).hasSize(16);
        assertThat(CommerceConfig.expand("coal")).containsExactly(Material.COAL);
        assertThat(CommerceConfig.expand("NOT_A_ITEM")).isEmpty();
        assertThat(CommerceConfig.expand("  ")).isEmpty();
        assertThat(CommerceConfig.expand(null)).isEmpty();
    }
}
