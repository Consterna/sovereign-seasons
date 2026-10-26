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

package com.sovereignseasons.core.calendar;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.SubSeason;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.DayChangeEvent;
import com.sovereignseasons.api.event.SeasonChangeEvent;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages calendar progression, season transitions, and state persistence based on Minecraft in-game days.
 */
public class WorldCalendarManager implements SeasonService {
    private final SeasonsConfig config;
    private final Logger logger;
    private final File storageFile;

    // Persisted offsets per world UUID: dayOffset allows /season set and /season setday without mutating world.getFullTime()
    private final Map<UUID, Integer> dayOffsets = new ConcurrentHashMap<>();

    // Runtime tracking of last evaluated day to fire DayChangeEvent and SeasonChangeEvent
    private final Map<UUID, Integer> lastObservedEffectiveDay = new ConcurrentHashMap<>();
    private final Map<UUID, Season> lastObservedSeason = new ConcurrentHashMap<>();

    public WorldCalendarManager(SeasonsConfig config, File dataFolder, Logger logger) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.logger = logger;
        this.storageFile = dataFolder != null ? new File(dataFolder, "calendar_data.yml") : new File("calendar_data.yml");
        loadData();
    }

    public synchronized void loadData() {
        if (!storageFile.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(storageFile);
            if (yaml.isConfigurationSection("offsets")) {
                for (String key : yaml.getConfigurationSection("offsets").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int offset = yaml.getInt("offsets." + key, 0);
                        dayOffsets.put(uuid, offset);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Could not parse calendar_data.yml: " + e.getMessage());
            }
        }
    }

    public synchronized void saveData() {
        File parentDir = storageFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : dayOffsets.entrySet()) {
            yaml.set("offsets." + entry.getKey().toString(), entry.getValue());
        }
        File tempFile = new File(parentDir != null ? parentDir : new File("."), storageFile.getName() + ".tmp");
        try {
            yaml.save(tempFile);
            try {
                java.nio.file.Files.move(
                        tempFile.toPath(),
                        storageFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(
                        tempFile.toPath(),
                        storageFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException e) {
            try {
                yaml.save(storageFile);
            } catch (IOException ex) {
                if (logger != null) {
                    logger.severe("Could not save calendar_data.yml: " + ex.getMessage());
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Resynchronizes calendar tracking state and reloads persisted offsets.
     * Prevents false season transitions after config reloads.
     */
    public synchronized void reload() {
        loadData();
        if (Bukkit.getServer() != null) {
            for (World world : Bukkit.getWorlds()) {
                if (isEnabledInWorld(world)) {
                    CalendarDate current = getDate(world);
                    if (current != null) {
                        lastObservedEffectiveDay.put(world.getUID(), current.totalDaysElapsed());
                        lastObservedSeason.put(world.getUID(), current.season());
                    }
                } else {
                    unloadWorld(world.getUID());
                }
            }
        }
    }

    /**
     * Cleans up tracking state when a world is unloaded to prevent world leaks.
     */
    public synchronized void unloadWorld(UUID worldUid) {
        if (worldUid == null) return;
        lastObservedEffectiveDay.remove(worldUid);
        lastObservedSeason.remove(worldUid);
    }

    @Override
    public boolean isEnabledInWorld(World world) {
        if (world == null) return false;
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false; // Seasons only apply to the Overworld
        }
        if (config.getEnabledWorlds().isEmpty()) {
            return true;
        }
        return config.getEnabledWorlds().contains(world.getName());
    }

    public CalendarDate calculateDate(int effectiveDay, long timeOfDay) {
        int daysPerSeason = Math.max(1, config.getDaysPerSeason());
        int daysPerYear = daysPerSeason * 4;

        int safeDay = Math.max(0, effectiveDay);
        int year = (safeDay / daysPerYear) + 1;
        int dayOfYear = safeDay % daysPerYear; // 0 to daysPerYear - 1

        int seasonIndex = dayOfYear / daysPerSeason; // 0, 1, 2, 3
        Season season = Season.values()[seasonIndex % Season.values().length];

        int dayOfSeason = (dayOfYear % daysPerSeason) + 1; // 1 to daysPerSeason
        SubSeason subSeason = SubSeason.calculate(dayOfSeason, daysPerSeason);

        return new CalendarDate(
                season,
                subSeason,
                dayOfSeason,
                daysPerSeason,
                safeDay,
                year,
                timeOfDay
        );
    }

    @Override
    public CalendarDate getDate(World world) {
        if (world == null) return null;

        long fullTime = world.getFullTime();
        long ticksPerDay = Math.max(100L, config.getTicksPerDay());
        int rawInGameDay = (int) (fullTime / ticksPerDay);

        int offset = getNormalizedOffset(world.getUID(), rawInGameDay);
        int effectiveDay = Math.max(0, rawInGameDay + offset);

        long timeOfDay = world.getTime();
        long safeTimeOfDay = (timeOfDay % 24000L + 24000L) % 24000L;

        return calculateDate(effectiveDay, safeTimeOfDay);
    }

    private synchronized int getNormalizedOffset(UUID worldUid, int rawInGameDay) {
        int offset = dayOffsets.getOrDefault(worldUid, 0);
        if (rawInGameDay + offset < 0) {
            // When world time was reset backwards, normalize offset so calendar does not freeze
            offset = -rawInGameDay;
            dayOffsets.put(worldUid, offset);
            saveData();
        }
        return offset;
    }

    @Override
    public void setSeason(World world, Season targetSeason) {
        applySeason(world, targetSeason, true);
    }

    private synchronized void applySeason(World world, Season targetSeason, boolean fireEvent) {
        if (world == null || targetSeason == null) return;

        CalendarDate current = getDate(world);
        if (current == null) return;

        int currentYear = current.year();
        Season previousSeason = current.season();

        Season finalSeason = targetSeason;
        if (fireEvent) {
            // Fire SeasonChangeEvent BEFORE applying changes
            SeasonChangeEvent event = new SeasonChangeEvent(world, previousSeason, targetSeason, currentYear);
            callEventSafely(event);
            if (event.isCancelled()) {
                return; // Cancelled by external plugin/listener
            }
            finalSeason = event.getNewSeason() != null ? event.getNewSeason() : targetSeason;
        }

        int daysPerSeason = Math.max(1, config.getDaysPerSeason());
        int daysPerYear = daysPerSeason * 4;

        long fullTime = world.getFullTime();
        long ticksPerDay = Math.max(100L, config.getTicksPerDay());
        int rawInGameDay = (int) (fullTime / ticksPerDay);

        int targetYearBaseDay = (currentYear - 1) * daysPerYear;
        int targetDayInYear = finalSeason.ordinal() * daysPerSeason; // day 1 of target season
        int targetEffectiveDay = targetYearBaseDay + targetDayInYear;

        int newOffset = targetEffectiveDay - rawInGameDay;
        dayOffsets.put(world.getUID(), newOffset);
        saveData();

        // Fire DayChangeEvent if the effective day changed
        CalendarDate newDate = getDate(world);
        if (current.totalDaysElapsed() != targetEffectiveDay) {
            DayChangeEvent dayEvent = new DayChangeEvent(world, current, newDate);
            callEventSafely(dayEvent);
        }

        // Update tracking
        lastObservedEffectiveDay.put(world.getUID(), targetEffectiveDay);
        lastObservedSeason.put(world.getUID(), finalSeason);
    }

    @Override
    public synchronized void setDayOfSeason(World world, int targetDayOfSeason) {
        if (world == null) return;

        CalendarDate current = getDate(world);
        if (current == null) return;

        int daysPerSeason = Math.max(1, config.getDaysPerSeason());
        int clampedDay = Math.max(1, Math.min(daysPerSeason, targetDayOfSeason));

        int daysPerYear = daysPerSeason * 4;
        long fullTime = world.getFullTime();
        long ticksPerDay = Math.max(100L, config.getTicksPerDay());
        int rawInGameDay = (int) (fullTime / ticksPerDay);

        int currentYear = current.year();
        int yearBaseDay = (currentYear - 1) * daysPerYear;
        int seasonBaseDay = current.season().ordinal() * daysPerSeason;
        int targetEffectiveDay = yearBaseDay + seasonBaseDay + (clampedDay - 1);

        if (targetEffectiveDay == current.totalDaysElapsed()) {
            return;
        }

        int newOffset = targetEffectiveDay - rawInGameDay;
        dayOffsets.put(world.getUID(), newOffset);
        saveData();

        CalendarDate newDate = getDate(world);
        DayChangeEvent dayEvent = new DayChangeEvent(world, current, newDate);
        callEventSafely(dayEvent);

        lastObservedEffectiveDay.put(world.getUID(), targetEffectiveDay);
    }

    /**
     * Ticking method to be called periodically (e.g. every second or on time skip)
     * to check if an in-game day or season boundary was crossed.
     * Synchronized to guarantee thread safety between region threads and global timer.
     */
    public synchronized void tickWorld(World world) {
        if (!isEnabledInWorld(world)) return;

        CalendarDate current = getDate(world);
        if (current == null) return;

        UUID uid = world.getUID();
        Integer lastDay = lastObservedEffectiveDay.get(uid);
        Season lastSeason = lastObservedSeason.get(uid);

        int currentEffectiveDay = current.totalDaysElapsed();
        Season currentSeason = current.season();

        if (lastDay == null) {
            // First time tracking this world
            lastObservedEffectiveDay.put(uid, currentEffectiveDay);
            lastObservedSeason.put(uid, currentSeason);
            return;
        }

        if (currentEffectiveDay != lastDay) {
            // Day changed!
            // Construct exact, accurate previous date
            CalendarDate previousDate = calculateDate(lastDay, 0L);

            DayChangeEvent dayEvent = new DayChangeEvent(world, previousDate, current);
            callEventSafely(dayEvent);

            if (currentSeason != lastSeason && lastSeason != null) {
                // Season changed!
                SeasonChangeEvent seasonEvent = new SeasonChangeEvent(world, lastSeason, currentSeason, current.year());
                callEventSafely(seasonEvent);

                if (seasonEvent.isCancelled()) {
                    // Revert effective day to the last day of lastSeason
                    int daysPerSeason = Math.max(1, config.getDaysPerSeason());
                    int daysPerYear = daysPerSeason * 4;
                    int previousSeasonYear = (lastSeason == Season.WINTER && currentSeason == Season.SPRING)
                            ? current.year() - 1
                            : current.year();
                    int yearBaseDay = (previousSeasonYear - 1) * daysPerYear;
                    int rollbackEffectiveDay = yearBaseDay + (lastSeason.ordinal() * daysPerSeason) + (daysPerSeason - 1);

                    long fullTime = world.getFullTime();
                    long ticksPerDay = Math.max(100L, config.getTicksPerDay());
                    int rawInGameDay = (int) (fullTime / ticksPerDay);
                    int newOffset = rollbackEffectiveDay - rawInGameDay;
                    dayOffsets.put(uid, newOffset);
                    saveData();

                    lastObservedEffectiveDay.put(uid, rollbackEffectiveDay);
                    lastObservedSeason.put(uid, lastSeason);
                    return;
                }

                if (seasonEvent.getNewSeason() != currentSeason) {
                    applySeason(world, seasonEvent.getNewSeason(), false);
                    return;
                }

                lastObservedSeason.put(uid, currentSeason);
            }

            lastObservedEffectiveDay.put(uid, currentEffectiveDay);
        }
    }

    private void callEventSafely(org.bukkit.event.Event event) {
        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                Bukkit.getPluginManager().callEvent(event);
            }
        } catch (Exception ignored) {}
    }
}
