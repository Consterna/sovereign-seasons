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
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.api.temperature.TemperatureBand;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonDisplayTest {
    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    @Test
    @DisplayName("HUD line shows season, phase, day, year and temperature")
    void hudLineShouldContainEverything() {
        CalendarDate date = new CalendarDate(Season.WINTER, SubSeason.MID, 4, 7, 25, 2, 6000);
        String text = plain(SeasonDisplay.hudLine(date, -3.25, TemperatureBand.FREEZING));
        assertThat(text).isEqualTo("Invierno (Medio) · Día 4/7 · Año 2 · -3.3°C Helado");
    }

    @Test
    @DisplayName("Temperature is omitted when unknown")
    void hudLineWithoutTemperature() {
        CalendarDate date = new CalendarDate(Season.SPRING, SubSeason.EARLY, 1, 7, 0, 1, 0);
        assertThat(plain(SeasonDisplay.hudLine(date, Double.NaN, TemperatureBand.MILD))).isEqualTo("Primavera (Temprano) · Día 1/7 · Año 1");
        assertThat(plain(SeasonDisplay.hudLine(date, 10.0, null))).doesNotContain("°C");
        // The season icon is decoration: no provider, a null answer or a failing provider all give no icon
        assertThat(SeasonInterface.icon(null, null, Season.SPRING)).isEmpty();
        assertThat(SeasonInterface.icon((player, season) -> null, null, Season.SPRING)).isEmpty();
        assertThat(SeasonInterface.icon((player, season) -> { throw new IllegalStateException("boom"); }, null, Season.SPRING)).isEmpty();
        assertThat(SeasonInterface.icon((player, season) -> "<font:x:y>a</font> ", null, Season.WINTER)).isEqualTo("<font:x:y>a</font> ");
    }

    @Test
    @DisplayName("Progress is clamped and every season has a bar color")
    void progressAndColors() {
        CalendarDate start = new CalendarDate(Season.SPRING, SubSeason.EARLY, 1, 7, 0, 1, 0);
        CalendarDate end = new CalendarDate(Season.SPRING, SubSeason.LATE, 7, 7, 6, 1, 23999);
        assertThat(SeasonDisplay.progress(start)).isZero();
        assertThat(SeasonDisplay.progress(end)).isBetween(0.99f, 1.0f);
        assertThat(SeasonDisplay.color(Season.SUMMER)).isEqualTo(BossBar.Color.YELLOW);
        for (Season season : Season.values()) {
            assertThat(SeasonDisplay.color(season)).isNotNull();
        }
    }
}
