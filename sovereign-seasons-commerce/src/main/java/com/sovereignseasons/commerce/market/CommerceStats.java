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

package com.sovereignseasons.commerce.market;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters shown by {@code /commerce stats}. Thread-safe: on Folia, events run on several region threads.
 */
public final class CommerceStats {
    private final AtomicLong adjustedOffers = new AtomicLong();
    private final AtomicLong refusedTrades = new AtomicLong();
    private final AtomicLong cratesSold = new AtomicLong();
    private final AtomicLong emeraldsPaid = new AtomicLong();
    private final AtomicLong fairsStocked = new AtomicLong();

    public void addAdjustedOffers(int count) { adjustedOffers.addAndGet(count); }
    public void addRefusedTrade() { refusedTrades.incrementAndGet(); }
    public void addBulkSale(int crates, int emeralds) {
        cratesSold.addAndGet(crates);
        emeraldsPaid.addAndGet(emeralds);
    }
    public void addFairStocked() { fairsStocked.incrementAndGet(); }

    public long getAdjustedOffers() { return adjustedOffers.get(); }
    public long getRefusedTrades() { return refusedTrades.get(); }
    public long getCratesSold() { return cratesSold.get(); }
    public long getEmeraldsPaid() { return emeraldsPaid.get(); }
    public long getFairsStocked() { return fairsStocked.get(); }
}
