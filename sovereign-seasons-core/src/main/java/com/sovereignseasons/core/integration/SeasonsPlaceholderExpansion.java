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

package com.sovereignseasons.core.integration;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.core.ui.SeasonDisplay;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Optional PlaceholderAPI expansion. Only loaded when PlaceholderAPI is installed.
 * <p>
 * Placeholders: {@code %sovereignseasons_season%}, {@code _season_id}, {@code _subseason}, {@code _day},
 * {@code _days_per_season}, {@code _year}, {@code _progress}, {@code _temperature}, {@code _band}.
 */
public class SeasonsPlaceholderExpansion extends PlaceholderExpansion {
    private final Plugin plugin;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;

    public SeasonsPlaceholderExpansion(Plugin plugin, SeasonService seasonService, TemperatureService temperatureService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sovereignseasons";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cristian";
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;
        World world = player != null ? player.getWorld() : firstEnabledWorld();
        if (world == null || !seasonService.isEnabledInWorld(world)) {
            return "";
        }
        CalendarDate date = seasonService.getDate(world);
        if (date == null) {
            return "";
        }
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "season" -> date.season().getDisplayName();
            case "season_id" -> date.season().getIdentifier();
            case "subseason" -> date.subSeason().getSpanishName();
            case "day" -> String.valueOf(date.dayOfSeason());
            case "days_per_season" -> String.valueOf(date.totalDaysPerSeason());
            case "year" -> String.valueOf(date.year());
            case "progress" -> String.format(Locale.ROOT, "%.0f", date.getSeasonProgressPercent());
            case "temperature" -> player != null ? SeasonDisplay.formatCelsius(temperatureService.getTemperature(player.getLocation())) : "";
            case "band" -> player != null ? temperatureService.getBand(player.getLocation()).getDisplayName() : "";
            default -> null;
        };
    }

    private World firstEnabledWorld() {
        for (World world : Bukkit.getWorlds()) {
            if (seasonService.isEnabledInWorld(world)) {
                return world;
            }
        }
        return null;
    }
}
