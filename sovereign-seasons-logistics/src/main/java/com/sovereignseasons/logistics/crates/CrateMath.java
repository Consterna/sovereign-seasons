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

package com.sovereignseasons.logistics.crates;

/**
 * Pure math for food freshness inside crates.
 * <p>
 * Pantry expiries assume a baseline decay rate of 1.0. Inside a crate food decays at a fixed rate {@code r}
 * whatever the surroundings, so after {@code t} ticks the expiry has moved by {@code t * (1 - r)}.
 * Storing {@code V = expiresAt - packedAt * (1 - r)} makes the crate state independent of the packing moment:
 * crates packed from the same stack get the same value and keep stacking, and the expiry when unpacking at
 * {@code now} is simply {@code V + now * (1 - r)}.
 */
public final class CrateMath {
    private CrateMath() {}

    public static long packedValue(long expiresAt, long packedAt, double packedDecayRate) {
        return expiresAt - shift(packedAt, packedDecayRate);
    }

    public static long unpackedExpiresAt(long packedValue, long now, double packedDecayRate) {
        return packedValue + shift(now, packedDecayRate);
    }

    /**
     * Rounds down to Pantry's expiry granularity. Rounding to the nearest value on both packing and unpacking
     * could add up to one granularity step per cycle, so repacking would slowly refresh food; flooring never
     * adds freshness and still gives crates packed from one stack the same value, so they keep stacking.
     */
    public static long floorTo(long value, long granularity) {
        if (granularity <= 1L) {
            return value;
        }
        return Math.floorDiv(value, granularity) * granularity;
    }

    private static long shift(long ticks, double rate) {
        double clamped = Double.isFinite(rate) ? Math.max(0.0, Math.min(1.0, rate)) : 1.0;
        return Math.round(Math.max(0L, ticks) * (1.0 - clamped));
    }
}
