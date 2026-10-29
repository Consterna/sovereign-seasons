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

package com.sovereignseasons.commerce.bulk;

/**
 * Wholesale of Logistics crates to villagers: crates sell at the villager's own price for the crop, with the
 * seasonal value and a bulk bonus, and every villager only buys a limited number of crates per day.
 */
public final class BulkSaleModel {

    /**
     * Outcome of a sale.
     *
     * @param emeralds   emeralds paid
     * @param cratesUsed crates taken from the player (never more than needed to pay those emeralds)
     */
    public record Quote(int emeralds, int cratesUsed) {
        public static final Quote NONE = new Quote(0, 0);

        public boolean isSale() {
            return emeralds > 0 && cratesUsed > 0;
        }
    }

    private BulkSaleModel() {
    }

    /**
     * @param itemsPerCrate       items in each crate
     * @param cratesOffered       crates the player holds
     * @param cratesLeftToday     crates this villager still buys today
     * @param unitsPerEmerald     units the villager asks for one emerald in its vanilla trade
     * @param seasonalValue       seasonal value of the crop (1.0 = vanilla)
     * @param bulkBonus           extra value of selling in crates (1.0 = none)
     */
    public static Quote quote(int itemsPerCrate, int cratesOffered, int cratesLeftToday, double unitsPerEmerald,
                              double seasonalValue, double bulkBonus) {
        int crates = Math.min(cratesOffered, cratesLeftToday);
        if (itemsPerCrate <= 0 || crates <= 0 || !(unitsPerEmerald > 0.0) || !(seasonalValue > 0.0) || !(bulkBonus > 0.0)
                || !Double.isFinite(unitsPerEmerald * seasonalValue * bulkBonus)) {
            return Quote.NONE;
        }
        double effectiveUnits = unitsPerEmerald / (seasonalValue * bulkBonus);
        long units = (long) itemsPerCrate * crates;
        int emeralds = (int) Math.min(Integer.MAX_VALUE, (long) Math.floor(units / effectiveUnits + 1e-9));
        if (emeralds <= 0) {
            return Quote.NONE;
        }
        int used = (int) Math.ceil(emeralds * effectiveUnits / itemsPerCrate - 1e-9);
        return new Quote(emeralds, Math.max(1, Math.min(crates, used)));
    }

    /**
     * Crates a villager still buys today, given what it bought on the stored day.
     */
    public static int cratesLeft(int dailyLimit, long storedDay, int soldOnStoredDay, long today) {
        if (dailyLimit <= 0) {
            return 0;
        }
        int sold = storedDay == today ? Math.max(0, soldOnStoredDay) : 0;
        return Math.max(0, dailyLimit - sold);
    }
}
