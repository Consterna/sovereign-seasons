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

package com.sovereignseasons.pantry.food;

import java.util.Locale;

/**
 * Preservation method applied to a food item. Salting and fermentation are planned for a later release.
 */
public enum Preservation {
    NONE("none", ""),
    SMOKED("smoked", "Ahumado");

    private final String identifier;
    private final String displayName;

    Preservation(String identifier, String displayName) {
        this.identifier = identifier;
        this.displayName = displayName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Preservation fromIdentifier(String input) {
        if (input == null || input.isBlank()) {
            return NONE;
        }
        String clean = input.trim().toLowerCase(Locale.ROOT);
        for (Preservation preservation : values()) {
            if (preservation.identifier.equals(clean)) {
                return preservation;
            }
        }
        return NONE;
    }
}
