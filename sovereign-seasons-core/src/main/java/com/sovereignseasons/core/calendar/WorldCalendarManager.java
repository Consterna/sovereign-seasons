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
        this.storageFile = new File(dataFolder, "calendar_data.yml");
        loadData();
    }

    public synchronized void loadData() {
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        if (yaml.isConfigurationSection("offsets")) {
            for (String key : yaml.getConfigurationSection("offsets").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int offset = yaml.getInt("offsets." + key, 0);
                    dayOffsets.put(uuid, offset);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public synchronized void saveData() {
        if (!storageFile.getParentFile().exists()) {
            storageFile.getParentFile().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : dayOffsets.entrySet()) {
            yaml.set("offsets." + entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(storageFile);
        } catch (IOException e) {
            if (logger != null) {
                logger.severe("Could not save calendar_data.yml: " + e.getMessage());
            }
        }
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

    @Override
    public CalendarDate getDate(World world) {
        if (world == null) return null;

        long fullTime = world.getFullTime();
        long ticksPerDay = config.getTicksPerDay();
        int rawInGameDay = (int) (fullTime / ticksPerDay);

        int offset = dayOffsets.getOrDefault(world.getUID(), 0);
        int effectiveDay = Math.max(0, rawInGameDay + offset);

        int daysPerSeason = config.getDaysPerSeason();
        int daysPerYear = daysPerSeason * 4;

        int year = (effectiveDay / daysPerYear) + 1;
        int dayOfYear = effectiveDay % daysPerYear; // 0 to daysPerYear - 1

        int seasonIndex = dayOfYear / daysPerSeason; // 0, 1, 2, 3
        Season season = Season.values()[seasonIndex % Season.values().length];

        int dayOfSeason = (dayOfYear % daysPerSeason) + 1; // 1 to daysPerSeason
        SubSeason subSeason = SubSeason.calculate(dayOfSeason, daysPerSeason);

        long timeOfDay = world.getTime();

        return new CalendarDate(
                season,
                subSeason,
                dayOfSeason,
                daysPerSeason,
                effectiveDay,
                year,
                timeOfDay
        );
    }

    @Override
    public void setSeason(World world, Season targetSeason) {
        if (world == null || targetSeason == null) return;

        CalendarDate current = getDate(world);
        if (current == null) return;

        int daysPerSeason = config.getDaysPerSeason();
        int daysPerYear = daysPerSeason * 4;

        long fullTime = world.getFullTime();
        int rawInGameDay = (int) (fullTime / config.getTicksPerDay());

        int currentYear = current.year();
        int targetYearBaseDay = (currentYear - 1) * daysPerYear;
        int targetDayInYear = targetSeason.ordinal() * daysPerSeason; // day 1 of target season
        int targetEffectiveDay = targetYearBaseDay + targetDayInYear;

        int newOffset = targetEffectiveDay - rawInGameDay;
        dayOffsets.put(world.getUID(), newOffset);
        saveData();

        // Fire SeasonChangeEvent
        Season previousSeason = current.season();
        SeasonChangeEvent event = new SeasonChangeEvent(world, previousSeason, targetSeason, currentYear);
        callEventSafely(event);

        // Update tracking
        lastObservedEffectiveDay.put(world.getUID(), targetEffectiveDay);
        lastObservedSeason.put(world.getUID(), targetSeason);
    }

    @Override
    public void setDayOfSeason(World world, int targetDayOfSeason) {
        if (world == null) return;

        CalendarDate current = getDate(world);
        if (current == null) return;

        int daysPerSeason = config.getDaysPerSeason();
        int clampedDay = Math.max(1, Math.min(daysPerSeason, targetDayOfSeason));

        int daysPerYear = daysPerSeason * 4;
        long fullTime = world.getFullTime();
        int rawInGameDay = (int) (fullTime / config.getTicksPerDay());

        int currentYear = current.year();
        int yearBaseDay = (currentYear - 1) * daysPerYear;
        int seasonBaseDay = current.season().ordinal() * daysPerSeason;
        int targetEffectiveDay = yearBaseDay + seasonBaseDay + (clampedDay - 1);

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
     */
    public void tickWorld(World world) {
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
            lastObservedEffectiveDay.put(uid, currentEffectiveDay);

            // Construct previous date for event
            CalendarDate previousDate = new CalendarDate(
                    lastSeason != null ? lastSeason : currentSeason,
                    SubSeason.calculate(Math.max(1, current.dayOfSeason() - 1), current.totalDaysPerSeason()),
                    Math.max(1, current.dayOfSeason() - 1),
                    current.totalDaysPerSeason(),
                    lastDay,
                    current.year(),
                    0L
            );

            DayChangeEvent dayEvent = new DayChangeEvent(world, previousDate, current);
            callEventSafely(dayEvent);

            if (currentSeason != lastSeason && lastSeason != null) {
                // Season changed!
                lastObservedSeason.put(uid, currentSeason);
                SeasonChangeEvent seasonEvent = new SeasonChangeEvent(world, lastSeason, currentSeason, current.year());
                callEventSafely(seasonEvent);
            }
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
