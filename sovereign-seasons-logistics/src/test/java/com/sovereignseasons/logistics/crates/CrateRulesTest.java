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

import com.sovereignseasons.logistics.crates.CrateRules.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrateRulesTest {

    @Test
    @DisplayName("Holding a crate: unpack in the air or on plain blocks, but let chests and doors open")
    void holdingCrate() {
        assertThat(CrateRules.decide(true, false, true, false, false)).isEqualTo(Outcome.UNPACK);
        assertThat(CrateRules.decide(true, false, false, false, false)).isEqualTo(Outcome.UNPACK);
        assertThat(CrateRules.decide(true, false, false, false, true)).isEqualTo(Outcome.IGNORE);
        // Sneaking skips block interaction in vanilla, so the crate is unpacked instead of being placed
        assertThat(CrateRules.decide(true, false, false, true, true)).isEqualTo(Outcome.UNPACK);
    }

    @Test
    @DisplayName("Packing needs sneaking, enough items and a click in the air")
    void packing() {
        assertThat(CrateRules.decide(false, true, true, true, false)).isEqualTo(Outcome.PACK);
        assertThat(CrateRules.decide(false, true, true, false, false)).isEqualTo(Outcome.IGNORE);
        assertThat(CrateRules.decide(false, true, false, true, false)).isEqualTo(Outcome.IGNORE); // planting on farmland
        assertThat(CrateRules.decide(false, false, true, true, false)).isEqualTo(Outcome.IGNORE);
    }

    @Test
    @DisplayName("Each packable crop has its own crate model number, unknown contents use the generic crate")
    void crateModels() {
        assertThat(CrateModels.of(org.bukkit.Material.CARROT)).isEqualTo(7_301_001);
        assertThat(CrateModels.of(org.bukkit.Material.SUGAR_CANE)).isEqualTo(7_301_009);
        assertThat(CrateModels.of(org.bukkit.Material.DIAMOND)).isEqualTo(CrateModels.BASE);
        assertThat(CrateModels.of(null)).isEqualTo(CrateModels.BASE);
        assertThat(CrateModels.END).isEqualTo(7_301_010);
        // Floats hold every integer below 2^24 exactly, and 1.21.4+ stores custom model data as floats
        assertThat((int) (float) CrateModels.END).isEqualTo(CrateModels.END);
        for (String crop : CrateModels.CROPS) {
            assertThat(org.bukkit.Material.matchMaterial(crop)).as(crop).isNotNull();
        }
    }

    @Test
    @DisplayName("Expired food comes out as rotten flesh only when Pantry converts spoiled food")
    void expiredFood() {
        assertThat(CrateRules.unpackAsRottenFlesh(100L, 100L, true)).isTrue();
        assertThat(CrateRules.unpackAsRottenFlesh(99L, 100L, true)).isTrue();
        assertThat(CrateRules.unpackAsRottenFlesh(101L, 100L, true)).isFalse();
        assertThat(CrateRules.unpackAsRottenFlesh(50L, 100L, false)).isFalse();
    }
}
