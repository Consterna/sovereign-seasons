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
import com.sovereignseasons.pantry.item.FoodStateCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Applies decay to batches of item stacks (a container's or a player's contents).
 */
public class FoodDecayService {
    private final PantryConfig config;
    private final DecayCalculator calculator;
    private final FoodStateCodec codec;

    public FoodDecayService(PantryConfig config, DecayCalculator calculator, FoodStateCodec codec) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.codec = Objects.requireNonNull(codec, "codec cannot be null");
    }

    /**
     * Outcome of a batch evaluation.
     *
     * @param evaluated   perishable stacks whose state was advanced
     * @param initialized perishable stacks seen for the first time
     * @param spoiled     stacks that rotted and were replaced
     */
    public record EvaluationResult(int evaluated, int initialized, int spoiled) {
        public boolean changedAnything() {
            return evaluated + initialized + spoiled > 0;
        }
    }

    /**
     * Whether a storage place should be re-evaluated, given its last evaluation tick.
     * Skipping keeps the previous tick, so no decay time is ever lost.
     */
    public boolean isDue(long lastEvaluation, long now) {
        if (lastEvaluation <= 0L || now < lastEvaluation) {
            return true;
        }
        return now - lastEvaluation >= config.getMinEvaluationIntervalTicks();
    }

    /**
     * Advances every perishable stack in {@code contents} (modified in place).
     *
     * @param contents inventory contents; spoiled stacks are replaced in the array
     * @param context  storage conditions during the elapsed period
     * @param elapsed  decay-clock ticks since the place was last evaluated (0 if never)
     * @param now      current decay-clock tick
     */
    public EvaluationResult evaluate(ItemStack[] contents, StorageContext context, long elapsed, long now) {
        if (contents == null || !config.isDecayEnabled()) {
            return new EvaluationResult(0, 0, 0);
        }
        int evaluated = 0;
        int initialized = 0;
        int spoiled = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !calculator.isPerishable(item.getType()) || item.getAmount() <= 0) {
                continue;
            }
            Material type = item.getType();
            Optional<FoodState> stored = codec.read(item);
            FoodState state;
            if (stored.isEmpty()) {
                state = calculator.createFresh(type, Preservation.NONE, now);
                initialized++;
            } else {
                FoodState previous = stored.get();
                state = calculator.advance(previous, elapsed, calculator.rate(context, previous.preservation()));
                evaluated++;
            }

            if (calculator.isSpoiled(state, type, now) && config.isSpoilToRottenFlesh()) {
                contents[i] = new ItemStack(Material.ROTTEN_FLESH, item.getAmount());
                spoiled++;
                continue;
            }
            codec.write(item, state, calculator.stage(state, type, now));
        }
        return new EvaluationResult(evaluated, initialized, spoiled);
    }

    /**
     * Starts tracking an untracked perishable item. Returns true if the item was modified.
     */
    public boolean tagIfUntracked(ItemStack item, long now) {
        if (item == null || !config.isDecayEnabled() || !calculator.isPerishable(item.getType())) {
            return false;
        }
        if (codec.read(item).isPresent()) {
            return false;
        }
        FoodState state = calculator.createFresh(item.getType(), Preservation.NONE, now);
        codec.write(item, state, calculator.stage(state, item.getType(), now));
        return true;
    }

    /**
     * Marks a freshly smoked item as preserved and fully fresh. Returns true if the item was modified.
     * Items that were already smoked keep their current expiry so the same stack cannot be refreshed twice.
     */
    public boolean markSmoked(ItemStack item, long now) {
        if (item == null || !config.isDecayEnabled() || !calculator.isPerishable(item.getType())) {
            return false;
        }
        Optional<FoodState> stored = codec.read(item);
        if (stored.isPresent() && stored.get().preservation() == Preservation.SMOKED) {
            return false;
        }
        FoodState state = calculator.createFresh(item.getType(), Preservation.SMOKED, now);
        codec.write(item, state, calculator.stage(state, item.getType(), now));
        return true;
    }

    /**
     * Freshness stage of a single item at {@code now}, without modifying it.
     * Untracked perishable items are considered fresh.
     */
    public FreshnessStage stageOf(ItemStack item, long now) {
        if (item == null || !calculator.isPerishable(item.getType())) {
            return FreshnessStage.FRESH;
        }
        return codec.read(item)
                .map(state -> calculator.stage(state, item.getType(), now))
                .orElse(FreshnessStage.FRESH);
    }

    /**
     * Forces the freshness of an item to the given fraction (admin tooling).
     */
    public boolean setFreshness(ItemStack item, double fraction, long now) {
        if (item == null || !calculator.isPerishable(item.getType()) || !Double.isFinite(fraction)) {
            return false;
        }
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        Preservation preservation = codec.read(item).map(FoodState::preservation).orElse(Preservation.NONE);
        long remaining = Math.round(calculator.shelfLifeTicks(item.getType()) * clamped);
        FoodState state = new FoodState(now + remaining, preservation);
        codec.write(item, state, calculator.stage(state, item.getType(), now));
        return true;
    }

    public DecayCalculator getCalculator() {
        return calculator;
    }

    public FoodStateCodec getCodec() {
        return codec;
    }
}
