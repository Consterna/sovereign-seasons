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

import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.pantry.config.PantryConfig;
import com.sovereignseasons.pantry.item.FoodStateCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FoodDecayServiceTest {
    private static final long DAY = 24_000L;
    private static final StorageContext MILD = StorageContext.open(TemperatureBand.MILD);

    /**
     * In-memory codec: ItemMeta needs a running server, so state is kept per stack instance.
     */
    static final class InMemoryCodec implements FoodStateCodec {
        final Map<ItemStack, FoodState> states = new IdentityHashMap<>();
        final Map<ItemStack, FreshnessStage> stages = new IdentityHashMap<>();

        @Override
        public Optional<FoodState> read(ItemStack item) {
            return Optional.ofNullable(states.get(item));
        }

        @Override
        public void write(ItemStack item, FoodState state, FreshnessStage stage) {
            states.put(item, state);
            stages.put(item, stage);
        }
    }

    private PantryConfig config;
    private InMemoryCodec codec;
    private FoodDecayService service;

    @BeforeEach
    void setup() {
        config = new PantryConfig(null);
        codec = new InMemoryCodec();
        service = new FoodDecayService(config, new DecayCalculator(config), codec);
    }

    @Test
    @DisplayName("Untracked perishable stacks are initialized fresh; non-perishables are untouched")
    void shouldInitializeUntrackedFood() {
        ItemStack bread = new ItemStack(Material.BREAD, 5);
        ItemStack stone = new ItemStack(Material.STONE, 64);
        ItemStack[] contents = {bread, null, stone};

        FoodDecayService.EvaluationResult result = service.evaluate(contents, MILD, 0L, 1_000L);

        assertThat(result.initialized()).isEqualTo(1);
        assertThat(result.evaluated()).isZero();
        assertThat(codec.states.get(bread).expiresAt()).isEqualTo(1_000L + 7 * DAY);
        assertThat(codec.stages.get(bread)).isEqualTo(FreshnessStage.FRESH);
        assertThat(codec.states).doesNotContainKey(stone);
        assertThat(contents[2]).isSameAs(stone);
    }

    @Test
    @DisplayName("Food in a hot place spoils into rotten flesh keeping the stack size")
    void shouldSpoilIntoRottenFlesh() {
        ItemStack beef = new ItemStack(Material.BEEF, 7);
        codec.write(beef, new FoodState(3 * DAY, Preservation.NONE), FreshnessStage.FRESH);
        ItemStack[] contents = {beef};

        // Two days in a hot place (rate 2.0) consume four days of a three-day shelf life
        FoodDecayService.EvaluationResult result = service.evaluate(contents, StorageContext.open(TemperatureBand.HOT), 2 * DAY, 2 * DAY);

        assertThat(result.spoiled()).isEqualTo(1);
        assertThat(contents[0].getType()).isEqualTo(Material.ROTTEN_FLESH);
        assertThat(contents[0].getAmount()).isEqualTo(7);
    }

    @Test
    @DisplayName("With rotten-flesh conversion disabled the stack stays but is marked spoiled")
    void shouldKeepSpoiledStackWhenConversionDisabled() {
        config.setSpoilToRottenFlesh(false);
        ItemStack beef = new ItemStack(Material.BEEF, 2);
        codec.write(beef, new FoodState(DAY, Preservation.NONE), FreshnessStage.FRESH);
        ItemStack[] contents = {beef};

        service.evaluate(contents, MILD, 2 * DAY, 2 * DAY);

        assertThat(contents[0]).isSameAs(beef);
        assertThat(codec.stages.get(beef)).isEqualTo(FreshnessStage.SPOILED);
    }

    @Test
    @DisplayName("A cold cellar slows decay compared to the open air")
    void cellarShouldSlowDecay() {
        ItemStack inCellar = new ItemStack(Material.APPLE, 1);
        ItemStack outside = new ItemStack(Material.APPLE, 1);
        codec.write(inCellar, new FoodState(10 * DAY, Preservation.NONE), FreshnessStage.FRESH);
        codec.write(outside, new FoodState(10 * DAY, Preservation.NONE), FreshnessStage.FRESH);

        service.evaluate(new ItemStack[]{inCellar}, new StorageContext(TemperatureBand.MILD, true, false), 4 * DAY, 4 * DAY);
        service.evaluate(new ItemStack[]{outside}, StorageContext.open(TemperatureBand.WARM), 4 * DAY, 4 * DAY);

        assertThat(codec.states.get(inCellar).expiresAt()).isGreaterThan(codec.states.get(outside).expiresAt());
        assertThat(codec.stages.get(inCellar)).isEqualTo(FreshnessStage.FRESH);
        assertThat(codec.stages.get(outside)).isEqualTo(FreshnessStage.STALE);
    }

    @Test
    @DisplayName("Barrels preserve better than chests and stacks close to rotting are counted")
    void barrelShouldPreserveAndSpoilingShouldBeCounted() {
        ItemStack inBarrel = new ItemStack(Material.BREAD, 1);
        ItemStack inChest = new ItemStack(Material.BREAD, 1);
        codec.write(inBarrel, new FoodState(7 * DAY, Preservation.NONE), FreshnessStage.FRESH);
        codec.write(inChest, new FoodState(7 * DAY, Preservation.NONE), FreshnessStage.FRESH);
        StorageContext barrel = MILD.withContainerMultiplier(config.getContainerMultiplier("BARREL"));

        service.evaluate(new ItemStack[]{inBarrel}, barrel, 4 * DAY, 4 * DAY);
        FoodDecayService.EvaluationResult chest = service.evaluate(new ItemStack[]{inChest}, MILD, 6 * DAY + DAY / 2, 6 * DAY + DAY / 2);

        assertThat(codec.states.get(inBarrel).expiresAt()).isEqualTo(8 * DAY);
        assertThat(chest.spoiling()).isEqualTo(1);
        assertThat(chest.spoiled()).isZero();
    }

    @Test
    @DisplayName("Stacks with the same history end up identical so they keep stacking")
    void identicalHistoriesShouldStayIdentical() {
        ItemStack a = new ItemStack(Material.CARROT, 10);
        ItemStack b = new ItemStack(Material.CARROT, 20);
        ItemStack[] contents = {a, b};

        service.evaluate(contents, MILD, 0L, 5_200L);
        service.evaluate(contents, StorageContext.open(TemperatureBand.COLD), 3 * DAY, 5_200L + 3 * DAY);

        assertThat(codec.states.get(a)).isEqualTo(codec.states.get(b));
    }

    @Test
    @DisplayName("Disabled decay leaves every stack untouched")
    void disabledDecayShouldDoNothing() {
        config.setDecayEnabled(false);
        ItemStack bread = new ItemStack(Material.BREAD, 1);
        FoodDecayService.EvaluationResult result = service.evaluate(new ItemStack[]{bread}, MILD, DAY, DAY);
        assertThat(result.changedAnything()).isFalse();
        assertThat(service.tagIfUntracked(bread, 0L)).isFalse();
        assertThat(codec.states).isEmpty();
        assertThat(service.evaluate(null, MILD, DAY, DAY).changedAnything()).isFalse();
    }

    @Test
    @DisplayName("Evaluation throttling never loses time and handles rewinds")
    void shouldThrottleEvaluations() {
        assertThat(service.isDue(0L, 100L)).isTrue();          // never evaluated
        assertThat(service.isDue(10_000L, 12_000L)).isFalse();  // < 6000 ticks
        assertThat(service.isDue(10_000L, 16_000L)).isTrue();
        assertThat(service.isDue(10_000L, 5_000L)).isTrue();    // clock went backwards
    }

    @Test
    @DisplayName("Smoking resets freshness once and cannot be repeated on the same stack")
    void smokingShouldBeIdempotent() {
        ItemStack steak = new ItemStack(Material.COOKED_BEEF, 3);
        codec.write(steak, new FoodState(DAY, Preservation.NONE), FreshnessStage.STALE);

        assertThat(service.markSmoked(steak, 2_000L)).isTrue();
        FoodState smoked = codec.states.get(steak);
        assertThat(smoked.preservation()).isEqualTo(Preservation.SMOKED);
        assertThat(smoked.expiresAt()).isEqualTo(2_000L + 6 * DAY);

        assertThat(service.markSmoked(steak, 50_000L)).isFalse();
        assertThat(codec.states.get(steak)).isEqualTo(smoked);
        assertThat(service.markSmoked(new ItemStack(Material.DRIED_KELP), 0L)).isFalse();
    }

    @Test
    @DisplayName("Tagging only applies to untracked perishable items")
    void taggingShouldOnlyApplyOnce() {
        ItemStack bread = new ItemStack(Material.BREAD, 1);
        assertThat(service.tagIfUntracked(bread, 0L)).isTrue();
        assertThat(service.tagIfUntracked(bread, 99_000L)).isFalse();
        assertThat(service.tagIfUntracked(new ItemStack(Material.GOLDEN_CARROT), 0L)).isFalse();
        assertThat(service.tagIfUntracked(null, 0L)).isFalse();
    }

    @Test
    @DisplayName("Admin freshness override clamps and preserves the preservation method")
    void shouldOverrideFreshness() {
        ItemStack fish = new ItemStack(Material.COOKED_COD, 1);
        codec.write(fish, new FoodState(0L, Preservation.SMOKED), FreshnessStage.SPOILED);

        assertThat(service.setFreshness(fish, 0.5, 1_000L)).isTrue();
        assertThat(codec.states.get(fish)).isEqualTo(new FoodState(1_000L + 2 * DAY, Preservation.SMOKED));
        assertThat(service.stageOf(fish, 1_000L)).isEqualTo(FreshnessStage.STALE);

        assertThat(service.setFreshness(fish, 5.0, 0L)).isTrue();
        assertThat(service.stageOf(fish, 0L)).isEqualTo(FreshnessStage.FRESH);
        assertThat(service.setFreshness(fish, Double.NaN, 0L)).isFalse();
        assertThat(service.setFreshness(new ItemStack(Material.STONE), 0.5, 0L)).isFalse();
    }

    @Test
    @DisplayName("Untracked or non-perishable items report as fresh when eaten")
    void stageOfShouldDefaultToFresh() {
        assertThat(service.stageOf(new ItemStack(Material.BREAD), 0L)).isEqualTo(FreshnessStage.FRESH);
        assertThat(service.stageOf(new ItemStack(Material.GOLDEN_APPLE), 0L)).isEqualTo(FreshnessStage.FRESH);
        assertThat(service.stageOf(null, 0L)).isEqualTo(FreshnessStage.FRESH);
    }
}
