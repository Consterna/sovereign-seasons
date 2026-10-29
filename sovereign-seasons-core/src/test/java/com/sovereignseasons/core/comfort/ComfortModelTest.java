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

package com.sovereignseasons.core.comfort;

import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.core.comfort.ComfortModel.Exposure;
import com.sovereignseasons.core.comfort.ComfortModel.Situation;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ComfortModelTest {
    private SeasonsConfig config;
    private ComfortModel model;

    @BeforeEach
    void setup() {
        config = new SeasonsConfig(null);
        model = new ComfortModel(config);
    }

    private static Situation outdoors(TemperatureBand band) {
        return new Situation(band, 15, 0, false, true, false, false, false);
    }

    @Test
    @DisplayName("Only freezing weather outdoors without protection feels cold")
    void coldRequiresFreezingExposure() {
        assertThat(model.evaluate(outdoors(TemperatureBand.FREEZING))).isEqualTo(Exposure.COLD);
        assertThat(model.evaluate(outdoors(TemperatureBand.COLD))).isEqualTo(Exposure.NONE);
        // Leather (vanilla freeze protection), shelter and nearby light/fire all protect
        assertThat(model.evaluate(new Situation(TemperatureBand.FREEZING, 15, 0, true, true, false, false, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.FREEZING, 5, 0, false, true, false, false, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.FREEZING, 15, 12, false, true, false, false, false))).isEqualTo(Exposure.NONE);
    }

    @Test
    @DisplayName("Only hot, sunny daytime without shade, water or fire resistance feels hot")
    void heatRequiresDirectSun() {
        assertThat(model.evaluate(outdoors(TemperatureBand.HOT))).isEqualTo(Exposure.HEAT);
        assertThat(model.evaluate(outdoors(TemperatureBand.WARM))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.HOT, 15, 0, false, false, false, false, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.HOT, 15, 0, false, true, true, false, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.HOT, 14, 0, false, true, false, false, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.HOT, 15, 0, false, true, false, true, false))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(new Situation(TemperatureBand.HOT, 15, 0, false, true, false, false, true))).isEqualTo(Exposure.NONE);
    }

    @Test
    @DisplayName("Each effect and the whole feature can be disabled")
    void shouldRespectToggles() {
        config.setColdEnabled(false);
        assertThat(model.evaluate(outdoors(TemperatureBand.FREEZING))).isEqualTo(Exposure.NONE);
        config.setHeatEnabled(false);
        assertThat(model.evaluate(outdoors(TemperatureBand.HOT))).isEqualTo(Exposure.NONE);
        config.setColdEnabled(true);
        config.setComfortEnabled(false);
        assertThat(model.evaluate(outdoors(TemperatureBand.FREEZING))).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(null)).isEqualTo(Exposure.NONE);
        assertThat(model.evaluate(outdoors(null))).isEqualTo(Exposure.NONE);
    }

    @Test
    @DisplayName("Freeze ticks stay below the vanilla damage threshold unless cold damage is enabled")
    void freezeTicksShouldRespectDamageSetting() {
        assertThat(model.targetFreezeTicks(140)).isEqualTo(120);
        assertThat(model.targetFreezeTicks(100)).isEqualTo(99);
        config.setColdDamage(true);
        assertThat(model.targetFreezeTicks(140)).isGreaterThan(140);
    }

    @Test
    @DisplayName("Heat exhaustion scales with the evaluation interval")
    void heatExhaustionShouldScale() {
        assertThat(model.heatExhaustion(20)).isCloseTo(0.05f, within(1e-6f));
        assertThat(model.heatExhaustion(40)).isCloseTo(0.10f, within(1e-6f));
    }
}
