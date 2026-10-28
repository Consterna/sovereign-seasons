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

import com.sovereignseasons.api.temperature.TemperatureBand;

import java.util.Objects;

/**
 * Environmental conditions of the place where food is stored (a container or a player's inventory).
 *
 * @param band     ambient temperature band
 * @param cellar   whether the place qualifies as a cold underground cellar
 * @param iceNearby whether ice blocks are close enough to act as an ice box
 */
public record StorageContext(TemperatureBand band, boolean cellar, boolean iceNearby) {
    public StorageContext {
        Objects.requireNonNull(band, "band cannot be null");
    }

    public static StorageContext open(TemperatureBand band) {
        return new StorageContext(band, false, false);
    }
}
