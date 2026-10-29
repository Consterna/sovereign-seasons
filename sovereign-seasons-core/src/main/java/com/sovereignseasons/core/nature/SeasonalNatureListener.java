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

package com.sovereignseasons.core.nature;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Seasonal weather and fauna: drier summers, spring twins, and the vanilla cold/hot mob variants
 * (strays and husks) appearing wherever the season makes the climate cold or hot enough.
 */
public class SeasonalNatureListener implements Listener {
    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;
    private final DoubleSupplier random;

    public SeasonalNatureListener(SeasonsConfig config, SeasonService seasonService, TemperatureService temperatureService) {
        this(config, seasonService, temperatureService, () -> ThreadLocalRandom.current().nextDouble());
    }

    public SeasonalNatureListener(SeasonsConfig config, SeasonService seasonService, TemperatureService temperatureService, DoubleSupplier random) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    // ---------------------------------------------------------------- weather

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (!config.isSeasonalWeatherEnabled() || !event.toWeatherState()) {
            return; // only natural rain starts are filtered; clearing is never blocked
        }
        if (event.getCause() != WeatherChangeEvent.Cause.NATURAL) {
            return; // /weather and other plugins keep full control
        }
        World world = event.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }
        if (!SeasonalRules.rainAllowed(config, seasonService.getSeason(world), random.getAsDouble())) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- breeding

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!config.isSeasonalFaunaEnabled()) {
            return;
        }
        LivingEntity child = event.getEntity();
        if (!(child instanceof Animals)) {
            return; // villagers and other non-animals are left alone
        }
        World world = child.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }
        if (!SeasonalRules.twins(config, seasonService.getSeason(world), random.getAsDouble())) {
            return;
        }
        Location location = child.getLocation();
        Entity twin = world.spawnEntity(location, child.getType(), CreatureSpawnEvent.SpawnReason.BREEDING);
        if (twin instanceof Ageable ageable) {
            ageable.setBaby();
        }
    }

    // ---------------------------------------------------------------- natural spawns

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalSpawn(CreatureSpawnEvent event) {
        if (!config.isSeasonalFaunaEnabled() || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        EntityType type = event.getEntityType();
        if (type != EntityType.SKELETON && type != EntityType.ZOMBIE) {
            return;
        }
        Location location = event.getLocation();
        World world = location.getWorld();
        if (world == null || !seasonService.isEnabledInWorld(world)) {
            return;
        }
        Season season = seasonService.getSeason(world);
        TemperatureBand band = temperatureService.getBand(location);
        EntityType replacement = SeasonalRules.seasonalVariant(config, type, season, band, random.getAsDouble());
        if (replacement != null) {
            event.setCancelled(true);
            world.spawnEntity(location, replacement, CreatureSpawnEvent.SpawnReason.NATURAL);
        }
    }
}
