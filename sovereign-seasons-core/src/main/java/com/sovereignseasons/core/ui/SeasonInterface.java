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

package com.sovereignseasons.core.ui;

import com.sovereignseasons.api.CalendarDate;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.SeasonChangeEvent;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
import com.sovereignseasons.api.ui.SeasonIconProvider;
import com.sovereignseasons.core.config.SeasonsConfig;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-facing interface: season change titles, the clock as a calendar, and the optional season BossBar.
 */
public class SeasonInterface implements Listener {
    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final TemperatureService temperatureService;
    private final FoliaLib foliaLib;
    private final NamespacedKey hudKey;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile SeasonIconProvider iconProvider;
    private volatile boolean iconProviderLoaded;

    public SeasonInterface(Plugin plugin, SeasonsConfig config, SeasonService seasonService, TemperatureService temperatureService, FoliaLib foliaLib) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.temperatureService = Objects.requireNonNull(temperatureService, "temperatureService cannot be null");
        this.foliaLib = foliaLib;
        this.hudKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin cannot be null"), "hud");
    }

    // ---------------------------------------------------------------- season change announcement

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeasonChange(SeasonChangeEvent event) {
        if (!config.isAnnounceSeasonChange()) {
            return;
        }
        Season season = event.getNewSeason();
        Component subtitle = miniMessage.deserialize("<gray>Comienza una nueva estación · Año " + event.getYear() + "</gray>");
        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1));
        for (Player player : event.getWorld().getPlayers()) {
            runFor(player, () -> {
                // Built per player: the season icon depends on whether that client loaded the resource pack
                player.showTitle(Title.title(miniMessage.deserialize(icon(player, season) + season.getMiniMessageTag()), subtitle, times));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.AMBIENT, 0.8f, 1.2f);
            });
        }
    }

    // ---------------------------------------------------------------- clock as a calendar

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClockUse(PlayerInteractEvent event) {
        if (!config.isClockShowsDate() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.CLOCK) {
            return;
        }
        String line = describe(player);
        if (line != null) {
            player.sendActionBar(miniMessage.deserialize(line));
        }
    }

    // ---------------------------------------------------------------- HUD

    /**
     * Toggles the season BossBar for a player and persists the choice. Returns the new state.
     */
    public boolean toggleHud(Player player) {
        boolean enable = !bars.containsKey(player.getUniqueId());
        if (enable) {
            player.getPersistentDataContainer().set(hudKey, PersistentDataType.BYTE, (byte) 1);
            showHud(player);
        } else {
            player.getPersistentDataContainer().remove(hudKey);
            hideHud(player);
        }
        return enable;
    }

    public boolean hasHud(Player player) {
        return bars.containsKey(player.getUniqueId());
    }

    /**
     * Refreshes the BossBar of a player. Must run on the player's own thread.
     */
    public void updateHud(Player player) {
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null || !player.isOnline()) {
            return;
        }
        World world = player.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            bar.name(miniMessage.deserialize("<gray>Sin estaciones en este mundo</gray>"));
            bar.progress(0.0f);
            return;
        }
        CalendarDate date = seasonService.getDate(world);
        if (date == null) {
            return;
        }
        double celsius = temperatureService.getTemperature(player.getLocation());
        TemperatureBand band = temperatureService.classify(celsius);
        bar.name(miniMessage.deserialize(icon(player, date.season()) + SeasonDisplay.hudLine(date, celsius, band)));
        bar.progress(SeasonDisplay.progress(date));
        bar.color(SeasonDisplay.color(date.season()));
    }

    public void forEachHudPlayer(java.util.function.Consumer<UUID> action) {
        bars.keySet().forEach(action);
    }

    private void showHud(Player player) {
        BossBar bar = BossBar.bossBar(Component.empty(), 0.0f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        bars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
        updateHud(player);
    }

    private void hideHud(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    public void hideAll() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            hideHud(player);
        }
        bars.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getPersistentDataContainer().has(hudKey, PersistentDataType.BYTE)) {
            showHud(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bars.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Calendar and temperature line for the player's position, or null if seasons are disabled there.
     */
    public String describe(Player player) {
        World world = player.getWorld();
        if (!seasonService.isEnabledInWorld(world)) {
            return null;
        }
        CalendarDate date = seasonService.getDate(world);
        if (date == null) {
            return null;
        }
        double celsius = temperatureService.getTemperature(player.getLocation());
        return icon(player, date.season()) + SeasonDisplay.hudLine(date, celsius, temperatureService.classify(celsius));
    }

    /**
     * Season icon of an optional {@link SeasonIconProvider} (the Sovereign Seasons resource pack), or "" when none
     * is installed. The provider is cached: ServicesManager lookups take a global lock, and the HUD asks for the
     * icon of every player on every refresh. The cache is dropped whenever that service is (un)registered.
     */
    private String icon(Player player, Season season) {
        if (!iconProviderLoaded) {
            try {
                iconProvider = Bukkit.getServicesManager().load(SeasonIconProvider.class);
            } catch (RuntimeException | LinkageError e) {
                iconProvider = null;
            }
            iconProviderLoaded = true;
        }
        return icon(iconProvider, player, season);
    }

    /**
     * The icon of a provider, or "" when there is none or it fails: the icon is decoration and must never break
     * the interface.
     */
    static String icon(SeasonIconProvider provider, Player player, Season season) {
        if (provider == null) {
            return "";
        }
        try {
            String icon = provider.icon(player, season);
            return icon != null ? icon : "";
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == SeasonIconProvider.class) {
            iconProviderLoaded = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == SeasonIconProvider.class) {
            iconProviderLoaded = false;
        }
    }

    private void runFor(Player player, Runnable action) {
        if (foliaLib != null) {
            foliaLib.getImpl().runAtEntity(player, task -> action.run());
        } else {
            action.run();
        }
    }
}
