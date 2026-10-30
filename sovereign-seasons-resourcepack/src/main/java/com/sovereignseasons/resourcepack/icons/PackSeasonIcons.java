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

package com.sovereignseasons.resourcepack.icons;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.ui.SeasonIconProvider;
import org.bukkit.entity.Player;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Season icons of the base pack's {@code sovereignseasons:icons} font, only for players who loaded that pack:
 * any other client would draw an empty box instead.
 */
public final class PackSeasonIcons implements SeasonIconProvider {
    /** Private-use characters of the font: U+E000 spring, U+E001 summer, U+E002 autumn, U+E003 winter. */
    public static final char FIRST_GLYPH = '';

    private final Predicate<Player> hasPack;
    private final BooleanSupplier enabled;

    public PackSeasonIcons(Predicate<Player> hasPack, BooleanSupplier enabled) {
        this.hasPack = hasPack;
        this.enabled = enabled;
    }

    public static String glyph(Season season) {
        return "<font:sovereignseasons:icons>" + (char) (FIRST_GLYPH + season.ordinal()) + "</font> ";
    }

    @Override
    public String icon(Player player, Season season) {
        if (player == null || season == null || !enabled.getAsBoolean() || !hasPack.test(player)) {
            return "";
        }
        return glyph(season);
    }
}
