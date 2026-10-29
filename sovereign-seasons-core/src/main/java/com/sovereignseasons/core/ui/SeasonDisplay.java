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

package com.sovereignseasons.core.ui;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import net.kyori.adventure.bossbar.BossBar;

import java.util.Locale;

/**
 * Pure text formatting shared by the HUD, the clock and placeholders.
 */
public final class SeasonDisplay {
    private SeasonDisplay() {}

    /**
     * MiniMessage line such as "Primavera (Temprano) · Día 3/7 · Año 1 · 15.2°C Templado".
     */
    public static String hudLine(CalendarDate date, double celsius, TemperatureBand band) {
        StringBuilder line = new StringBuilder();
        line.append(date.season().getMiniMessageTag())
                .append(" <gray>(").append(date.subSeason().getSpanishName()).append(")</gray>")
                .append(" <dark_gray>·</dark_gray> <gray>Día</gray> <white>").append(date.dayOfSeason())
                .append("</white><gray>/</gray><white>").append(date.totalDaysPerSeason()).append("</white>")
                .append(" <dark_gray>·</dark_gray> <gray>Año</gray> <white>").append(date.year()).append("</white>");
        if (band != null && Double.isFinite(celsius)) {
            line.append(" <dark_gray>·</dark_gray> ").append(temperature(celsius, band));
        }
        return line.toString();
    }

    public static String temperature(double celsius, TemperatureBand band) {
        return band.getColorTag() + formatCelsius(celsius) + " " + band.getDisplayName();
    }

    public static String formatCelsius(double celsius) {
        return String.format(Locale.ROOT, "%.1f°C", celsius);
    }

    public static float progress(CalendarDate date) {
        double fraction = date.getSeasonProgressPercent() / 100.0;
        if (!Double.isFinite(fraction)) {
            return 0.0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, fraction));
    }

    public static BossBar.Color color(Season season) {
        return switch (season) {
            case SPRING -> BossBar.Color.GREEN;
            case SUMMER -> BossBar.Color.YELLOW;
            case AUTUMN -> BossBar.Color.RED;
            case WINTER -> BossBar.Color.WHITE;
        };
    }
}
