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
 * Pure decisions for crate interactions.
 */
public final class CrateRules {
    private CrateRules() {}

    public enum Outcome {
        /** Let vanilla handle the click (e.g. open the chest the player clicked). */
        IGNORE,
        PACK,
        UNPACK
    }

    /**
     * @param holdingCrate      the main-hand item is a crate
     * @param canPack           the main-hand item is a packable crop with enough items for a crate
     * @param clickedAir        right-click in the air (false: right-click on a block)
     * @param sneaking          the player is sneaking
     * @param blockInteractable the clicked block opens or toggles something (chest, door, lever...)
     */
    public static Outcome decide(boolean holdingCrate, boolean canPack, boolean clickedAir, boolean sneaking, boolean blockInteractable) {
        if (holdingCrate) {
            // Clicking a chest, door or lever must still work while holding a crate (vanilla: sneaking skips it)
            if (!clickedAir && blockInteractable && !sneaking) {
                return Outcome.IGNORE;
            }
            return Outcome.UNPACK;
        }
        // Packing only in the air: sneak + right-click on farmland must still plant crops
        if (canPack && clickedAir && sneaking) {
            return Outcome.PACK;
        }
        return Outcome.IGNORE;
    }

    /**
     * Whether unpacked food whose expiry has passed must come out as rotten flesh right away.
     * When Pantry keeps spoiled stacks instead (its spoil-to-rotten-flesh option is off), the stack keeps its
     * expired state and Pantry shows it as spoiled.
     */
    public static boolean unpackAsRottenFlesh(long expiresAt, long now, boolean spoilToRottenFlesh) {
        return expiresAt <= now && spoilToRottenFlesh;
    }
}
