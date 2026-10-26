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

package com.sovereignseasons.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Represents the four primary seasons in Sovereign Seasons.
 */
public enum Season {
    SPRING(
            "spring",
            "Primavera",
            NamedTextColor.GREEN,
            "<gradient:#55FF55:#AAFFAA>Primavera</gradient>",
            1.0f
    ),
    SUMMER(
            "summer",
            "Verano",
            NamedTextColor.GOLD,
            "<gradient:#FFAA00:#FFFF55>Verano</gradient>",
            1.25f
    ),
    AUTUMN(
            "autumn",
            "Otoño",
            NamedTextColor.GOLD,
            "<gradient:#FF5555:#FFAA00>Otoño</gradient>",
            1.0f
    ),
    WINTER(
            "winter",
            "Invierno",
            NamedTextColor.AQUA,
            "<gradient:#55FFFF:#FFFFFF>Invierno</gradient>",
            0.0f
    );

    private final String identifier;
    private final String displayName;
    private final TextColor color;
    private final String miniMessageTag;
    private final float baseCropGrowthMultiplier;

    Season(String identifier, String displayName, TextColor color, String miniMessageTag, float baseCropGrowthMultiplier) {
        this.identifier = identifier;
        this.displayName = displayName;
        this.color = color;
        this.miniMessageTag = miniMessageTag;
        this.baseCropGrowthMultiplier = baseCropGrowthMultiplier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TextColor getColor() {
        return color;
    }

    public String getMiniMessageTag() {
        return miniMessageTag;
    }

    public Component asComponent() {
        return MiniMessage.miniMessage().deserialize(miniMessageTag);
    }

    public float getBaseCropGrowthMultiplier() {
        return baseCropGrowthMultiplier;
    }

    public boolean isWinter() {
        return this == WINTER;
    }

    public Season next() {
        Season[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public Season previous() {
        Season[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }

    public static Season fromString(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "spring", "primavera" -> SPRING;
            case "summer", "verano" -> SUMMER;
            case "autumn", "fall", "otono", "otoño" -> AUTUMN;
            case "winter", "invierno" -> WINTER;
            default -> {
                for (Season season : values()) {
                    if (season.identifier.equalsIgnoreCase(clean) || season.name().equalsIgnoreCase(clean)) {
                        yield season;
                    }
                }
                yield null;
            }
        };
    }
}
