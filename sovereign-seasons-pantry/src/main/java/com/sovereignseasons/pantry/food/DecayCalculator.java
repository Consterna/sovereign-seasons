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

import com.sovereignseasons.pantry.config.PantryConfig;
import org.bukkit.Material;

import java.util.Objects;

/**
 * Pure (Bukkit-free) decay math.
 * <p>
 * An item's {@link FoodState#expiresAt()} assumes a baseline decay rate of 1.0. When the item has spent
 * {@code elapsed} ticks in a place decaying at {@code rate}, its remaining life shrinks by
 * {@code elapsed * rate} instead of {@code elapsed}, so the expiry moves by {@code elapsed * (1 - rate)}.
 * Expiries are rounded to a configurable granularity so that items produced close in time stack together.
 */
public class DecayCalculator {
    private static final double MAX_RATE = 10.0;

    private final PantryConfig config;

    public DecayCalculator(PantryConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Shelf life in ticks for a material, or 0 if it never spoils.
     */
    public long shelfLifeTicks(Material material) {
        double days = config.getShelfLifeDays(material);
        if (days <= 0.0) {
            return 0L;
        }
        return Math.max(1L, Math.round(days * config.getTicksPerDay()));
    }

    public boolean isPerishable(Material material) {
        return shelfLifeTicks(material) > 0L;
    }

    /**
     * Effective decay rate for a storage context and preservation method (1.0 = baseline).
     */
    public double rate(StorageContext context, Preservation preservation) {
        double rate = context != null ? config.getBandRate(context.band()) : 1.0;
        if (context != null && config.isCellarEnabled()) {
            if (context.cellar()) {
                rate *= config.getCellarMultiplier();
            }
            if (context.iceNearby()) {
                rate *= config.getIceMultiplier();
            }
        }
        if (context != null) {
            rate *= context.containerMultiplier();
        }
        if (preservation == Preservation.SMOKED) {
            rate *= config.getSmokedMultiplier();
        }
        if (!Double.isFinite(rate)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(MAX_RATE, rate));
    }

    /**
     * State for an item that has just been produced at decay-clock tick {@code now}.
     */
    public FoodState createFresh(Material material, Preservation preservation, long now) {
        long expiresAt = round(saturatedAdd(now, shelfLifeTicks(material)));
        return new FoodState(expiresAt, preservation != null ? preservation : Preservation.NONE);
    }

    /**
     * Advances a state after {@code elapsed} ticks spent decaying at {@code rate}.
     * Negative elapsed time (clock rewinds) is ignored.
     */
    public FoodState advance(FoodState state, long elapsed, double rate) {
        Objects.requireNonNull(state, "state cannot be null");
        if (elapsed <= 0L || !Double.isFinite(rate)) {
            return state;
        }
        double shift = (double) elapsed * (1.0 - rate);
        long shiftTicks = shift >= Long.MAX_VALUE ? Long.MAX_VALUE : (shift <= Long.MIN_VALUE ? Long.MIN_VALUE : Math.round(shift));
        return state.withExpiresAt(round(saturatedAdd(state.expiresAt(), shiftTicks)));
    }

    /**
     * Remaining freshness fraction in [0, 1].
     */
    public double freshness(FoodState state, Material material, long now) {
        long shelfLife = shelfLifeTicks(material);
        if (state == null || shelfLife <= 0L) {
            return 1.0;
        }
        double remaining = (double) state.expiresAt() - (double) now;
        return Math.max(0.0, Math.min(1.0, remaining / (double) shelfLife));
    }

    public FreshnessStage stage(FoodState state, Material material, long now) {
        return FreshnessStage.of(freshness(state, material, now), config.getFreshAbove(), config.getStaleAbove());
    }

    public boolean isSpoiled(FoodState state, Material material, long now) {
        return isPerishable(material) && state != null && state.expiresAt() <= now;
    }

    long round(long tick) {
        long granularity = config.getExpiryGranularityTicks();
        if (granularity <= 1L) {
            return tick;
        }
        long down = Math.floorDiv(tick, granularity) * granularity;
        long remainder = tick - down;
        return remainder * 2 >= granularity ? saturatedAdd(down, granularity) : down;
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return result;
    }
}
