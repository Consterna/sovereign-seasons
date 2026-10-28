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

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.temperature.TemperatureService;

import java.util.Objects;

/**
 * Main public entry point for the Sovereign Seasons API.
 * Other modules (Pantry, Logistics, Commerce) depend on this API.
 */
public interface SovereignSeasonsApi {

    SeasonService getSeasonService();

    GreenhouseService getGreenhouseService();

    TemperatureService getTemperatureService();

    // Singleton holder
    final class Holder {
        private static SovereignSeasonsApi instance;

        private Holder() {}

        public static void set(SovereignSeasonsApi api) {
            instance = api;
        }

        public static SovereignSeasonsApi get() {
            return Objects.requireNonNull(instance, "SovereignSeasonsApi has not been initialized yet.");
        }
    }

    static SovereignSeasonsApi get() {
        return Holder.get();
    }
}
