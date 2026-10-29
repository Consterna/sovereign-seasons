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

package com.sovereignseasons.commerce.pricing;

import com.sovereignseasons.api.Season;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Seasonal market value of products (1.0 = vanilla price).
 * <p>
 * Crops follow their growing seasons: abundant (cheaper) in season, scarce (dearer) in winter. Other goods
 * (wool, coal, fish, meat...) have a fixed value per season. A value above 1.0 means the product is worth more:
 * villagers ask for fewer units when buying it and more emeralds when selling it.
 */
public final class ProductValues {
    private final Map<Material, Set<Season>> cropSeasons;
    private final Map<Material, Map<Season, Double>> goods;
    private final double inSeason;
    private final double offSeason;
    private final double winter;

    public ProductValues(Map<Material, Set<Season>> cropSeasons, Map<Material, Map<Season, Double>> goods,
                         double inSeason, double offSeason, double winter) {
        this.cropSeasons = copySeasons(Objects.requireNonNull(cropSeasons, "cropSeasons cannot be null"));
        this.goods = copyGoods(Objects.requireNonNull(goods, "goods cannot be null"));
        this.inSeason = inSeason;
        this.offSeason = offSeason;
        this.winter = winter;
    }

    /**
     * Seasonal value of a product, or 1.0 if the product is not tracked.
     */
    public double value(Material product, Season season) {
        if (product == null || season == null) {
            return 1.0;
        }
        Map<Season, Double> fixed = goods.get(product);
        if (fixed != null) {
            return fixed.getOrDefault(season, 1.0);
        }
        Set<Season> seasons = cropSeasons.get(product);
        if (seasons == null) {
            return 1.0;
        }
        if (seasons.contains(season)) {
            return inSeason;
        }
        return season == Season.WINTER ? winter : offSeason;
    }

    public boolean isTracked(Material product) {
        return product != null && (goods.containsKey(product) || cropSeasons.containsKey(product));
    }

    public Set<Material> trackedProducts() {
        Set<Material> all = EnumSet.noneOf(Material.class);
        all.addAll(cropSeasons.keySet());
        all.addAll(goods.keySet());
        return Collections.unmodifiableSet(all);
    }

    private static Map<Material, Set<Season>> copySeasons(Map<Material, Set<Season>> source) {
        Map<Material, Set<Season>> copy = new EnumMap<>(Material.class);
        source.forEach((material, seasons) -> {
            Set<Season> set = EnumSet.noneOf(Season.class);
            set.addAll(seasons);
            copy.put(material, Collections.unmodifiableSet(set));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Material, Map<Season, Double>> copyGoods(Map<Material, Map<Season, Double>> source) {
        Map<Material, Map<Season, Double>> copy = new EnumMap<>(Material.class);
        source.forEach((material, values) -> {
            // new EnumMap<>(map) rejects an empty non-EnumMap, so copy entry by entry
            Map<Season, Double> perSeason = new EnumMap<>(Season.class);
            perSeason.putAll(values);
            copy.put(material, Collections.unmodifiableMap(perSeason));
        });
        return Collections.unmodifiableMap(copy);
    }
}
