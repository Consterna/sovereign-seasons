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

package com.sovereignseasons.api.ui;

import com.sovereignseasons.api.Season;
import org.bukkit.entity.Player;

/**
 * Optional icon shown before the season name in the title, the clock and the HUD.
 * <p>
 * Another plugin (the Sovereign Seasons resource pack) registers an implementation in Bukkit's ServicesManager;
 * the core asks it every time it shows a season, so the icon only appears to players whose client can draw it.
 */
public interface SeasonIconProvider {

    /**
     * MiniMessage text to put before the season name for this player (for example a glyph of a resource pack font
     * followed by a space), or an empty string for no icon.
     */
    String icon(Player player, Season season);
}
