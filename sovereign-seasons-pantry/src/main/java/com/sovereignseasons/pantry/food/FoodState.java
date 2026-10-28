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

import java.util.Objects;

/**
 * Persisted decay state of a food item.
 * <p>
 * {@code expiresAt} is the decay-clock tick at which the item spoils assuming a baseline rate of 1.0.
 * Time that is never evaluated (items on the ground, in unopened containers before tracking started)
 * therefore implicitly decays at the baseline rate, and only storage contexts that differ from the
 * baseline need to adjust it.
 *
 * @param expiresAt    decay-clock tick when the item spoils
 * @param preservation preservation method applied to the item
 */
public record FoodState(long expiresAt, Preservation preservation) {
    public FoodState {
        Objects.requireNonNull(preservation, "preservation cannot be null");
    }

    public FoodState withExpiresAt(long newExpiresAt) {
        return new FoodState(newExpiresAt, preservation);
    }
}
