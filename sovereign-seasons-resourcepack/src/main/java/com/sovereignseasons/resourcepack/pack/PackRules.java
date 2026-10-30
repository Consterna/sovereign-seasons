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

package com.sovereignseasons.resourcepack.pack;

import com.sovereignseasons.api.Season;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure rules: which seasonal pack a season uses and how to go from the pack a player has to the one it needs.
 */
public final class PackRules {

    /**
     * Change of seasonal pack for one player.
     *
     * @param remove pack to remove first, or null
     * @param add    pack to add, or null
     */
    public record Swap(String remove, String add) {
        public static final Swap NONE = new Swap(null, null);

        public boolean isNone() {
            return remove == null && add == null;
        }
    }

    private PackRules() {
    }

    /**
     * Seasonal pack of a season, or null when the season shows vanilla colours (summer by default) or seasons are
     * off in that world.
     */
    public static String seasonalPack(Season season, Set<Season> seasonsWithPack, boolean seasonsEnabledInWorld) {
        if (!seasonsEnabledInWorld || season == null || seasonsWithPack == null || !seasonsWithPack.contains(season)) {
            return null;
        }
        return season.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Steps to go from the seasonal pack a player has to the one it should have. A player who declined one of our
     * packs gets no new seasonal packs (the current one, if any, is still removed when it no longer applies).
     */
    public static Swap swap(String current, String target, boolean declined) {
        if (Objects.equals(current, target)) {
            return Swap.NONE;
        }
        String add = declined ? null : target;
        return new Swap(current, add);
    }
}
