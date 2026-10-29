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

package com.sovereignseasons.pantry.api;

import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.food.DecayCalculator;
import com.sovereignseasons.pantry.food.FoodState;
import com.sovereignseasons.pantry.food.Preservation;
import com.sovereignseasons.pantry.item.FoodStateCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

/**
 * Default {@link PantryApi} backed by Pantry's decay calculator and item codec.
 */
public class PantryApiImpl implements PantryApi {
    private final PantryConfig config;
    private final DecayCalculator calculator;
    private final FoodStateCodec codec;
    private final LongSupplier clock;

    public PantryApiImpl(PantryConfig config, DecayCalculator calculator, FoodStateCodec codec, LongSupplier clock) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public long now() {
        return clock.getAsLong();
    }

    @Override
    public boolean isPerishable(Material material) {
        return calculator.isPerishable(material);
    }

    @Override
    public OptionalLong getExpiresAt(ItemStack item) {
        if (item == null || !calculator.isPerishable(item.getType())) {
            return OptionalLong.empty();
        }
        return codec.read(item).map(state -> OptionalLong.of(state.expiresAt())).orElse(OptionalLong.empty());
    }

    @Override
    public long freshExpiresAt(Material material, long now) {
        return calculator.createFresh(material, Preservation.NONE, now).expiresAt();
    }

    @Override
    public void setExpiresAt(ItemStack item, long expiresAt) {
        if (item == null || !calculator.isPerishable(item.getType())) {
            return;
        }
        Preservation preservation = codec.read(item).map(FoodState::preservation).orElse(Preservation.NONE);
        FoodState state = new FoodState(expiresAt, preservation);
        codec.write(item, state, calculator.stage(state, item.getType(), now()));
    }

    @Override
    public long roundExpiry(long expiresAt) {
        return calculator.roundExpiry(expiresAt);
    }

    @Override
    public boolean spoilsToRottenFlesh() {
        return config.isSpoilToRottenFlesh();
    }
}
