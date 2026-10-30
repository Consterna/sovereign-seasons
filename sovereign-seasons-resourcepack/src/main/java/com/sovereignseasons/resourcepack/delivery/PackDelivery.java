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

package com.sovereignseasons.resourcepack.delivery;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.SeasonChangeEvent;
import com.sovereignseasons.resourcepack.config.PackConfig;
import com.sovereignseasons.resourcepack.pack.PackFile;
import com.sovereignseasons.resourcepack.pack.PackLibrary;
import com.sovereignseasons.resourcepack.pack.PackRules;
import com.sovereignseasons.resourcepack.pack.PackUrls;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends the base pack and the pack of the current season on join, and swaps only the seasonal pack when the season
 * changes or the player moves to another world. A player who declines one of our packs is not asked again until
 * they use {@code /seasonpack resend}.
 */
public class PackDelivery implements Listener {
    /** Per-player state. Read from several threads on Folia, hence the volatile fields and concurrent map. */
    static final class PlayerPacks {
        volatile String seasonal;
        volatile boolean declined;
        final Map<UUID, PlayerResourcePackStatusEvent.Status> statuses = new ConcurrentHashMap<>();
    }

    private final PackConfig config;
    private final PackLibrary library;
    private final SeasonService seasons;
    private final FoliaLib foliaLib;
    private final Map<UUID, PlayerPacks> players = new ConcurrentHashMap<>();
    private final UUID baseId = PackFile.idFor("base");
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong loaded = new AtomicLong();
    private final AtomicLong declined = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public PackDelivery(PackConfig config, PackLibrary library, SeasonService seasons, FoliaLib foliaLib) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.library = Objects.requireNonNull(library, "library cannot be null");
        this.seasons = Objects.requireNonNull(seasons, "seasons cannot be null");
        this.foliaLib = Objects.requireNonNull(foliaLib, "foliaLib cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        players.put(player.getUniqueId(), new PlayerPacks());
        foliaLib.getImpl().runAtEntityLater(player, () -> sendAll(player), Math.max(1L, config.getSendDelayTicks()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        updateSeasonal(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSeasonChange(SeasonChangeEvent event) {
        Season season = event.getNewSeason();
        World world = event.getWorld();
        for (Player player : world.getPlayers()) {
            // On Folia the task runs later on the player's region: skip it if the player left that world meanwhile
            // (the world change listener already gave them the right pack)
            foliaLib.getImpl().runAtEntity(player, task -> {
                if (player.getWorld().equals(world)) {
                    updateSeasonal(player, season);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        PlayerPacks state = players.get(event.getPlayer().getUniqueId());
        UUID id = event.getID();
        if (state == null || id == null || !isOurs(id)) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        state.statuses.put(id, status);
        boolean seasonal = state.seasonal != null && PackFile.idFor(state.seasonal).equals(id);
        switch (status) {
            case SUCCESSFULLY_LOADED -> loaded.incrementAndGet();
            case DECLINED -> {
                declined.incrementAndGet();
                state.declined = true;
                if (seasonal) {
                    state.seasonal = null;
                }
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                failed.incrementAndGet();
                if (seasonal) {
                    state.seasonal = null; // the client does not have it: the next update may try again
                }
            }
            default -> {
                // ACCEPTED, DOWNLOADED and DISCARDED are intermediate or informational
            }
        }
    }

    /** /seasonpack resend: sends everything again on the player's own thread (required on Folia). */
    public void resend(Player player) {
        foliaLib.getImpl().runAtEntity(player, task -> sendAll(player));
    }

    /** Sends the base pack and the seasonal pack of the player's world (on join and for /seasonpack resend). */
    void sendAll(Player player) {
        if (!player.isOnline()) {
            return;
        }
        PlayerPacks state = players.computeIfAbsent(player.getUniqueId(), id -> new PlayerPacks());
        state.declined = false;
        if (config.isBasePackEnabled()) {
            library.get("base").ifPresent(pack -> send(player, pack));
        }
        if (state.seasonal != null) {
            // Take the current seasonal pack off first, so a world without seasons never keeps one
            player.removeResourcePack(PackFile.idFor(state.seasonal));
            state.statuses.remove(PackFile.idFor(state.seasonal));
            state.seasonal = null;
        }
        updateSeasonal(player);
    }

    private void updateSeasonal(Player player) {
        World world = player.getWorld();
        updateSeasonal(player, seasons.isEnabledInWorld(world) ? seasons.getSeason(world) : null);
    }

    private void updateSeasonal(Player player, Season season) {
        PlayerPacks state = players.get(player.getUniqueId());
        if (state == null || !player.isOnline()) {
            return;
        }
        String target = config.isSeasonalPacksEnabled()
                ? PackRules.seasonalPack(season, config.getSeasonsWithPack(), season != null)
                : null;
        PackRules.Swap swap = PackRules.swap(state.seasonal, target, state.declined);
        if (swap.isNone()) {
            return;
        }
        if (swap.remove() != null) {
            player.removeResourcePack(PackFile.idFor(swap.remove()));
            state.statuses.remove(PackFile.idFor(swap.remove()));
        }
        state.seasonal = null;
        if (swap.add() != null) {
            library.get(swap.add()).ifPresent(pack -> {
                if (send(player, pack)) {
                    state.seasonal = pack.name();
                }
            });
        }
    }

    private boolean send(Player player, PackFile pack) {
        String url = PackUrls.url(config, pack, connectedHost(player), Bukkit.getIp());
        if (url == null) {
            return false;
        }
        String prompt = LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(config.getPrompt()));
        player.addResourcePack(pack.id(), url, pack.sha1(), prompt, config.isForce());
        sent.incrementAndGet();
        return true;
    }

    /** Host name the player typed to join, without the markers some modded clients append. */
    static String connectedHost(Player player) {
        InetSocketAddress virtualHost = player.getVirtualHost();
        if (virtualHost == null) {
            return null;
        }
        String host = virtualHost.getHostString();
        int marker = host.indexOf('\0');
        if (marker >= 0) {
            host = host.substring(0, marker);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private boolean isOurs(UUID id) {
        for (String name : PackLibrary.NAMES) {
            if (PackFile.idFor(name).equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player has the base pack (crates and icons) loaded. */
    public boolean hasBasePack(Player player) {
        PlayerPacks state = players.get(player.getUniqueId());
        return state != null && state.statuses.get(baseId) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    /** Short status for /seasonpack status. */
    public String describe(Player player) {
        PlayerPacks state = players.get(player.getUniqueId());
        if (state == null) {
            return "sin datos";
        }
        PlayerResourcePackStatusEvent.Status base = state.statuses.get(baseId);
        String seasonal = state.seasonal == null ? "ninguno" : state.seasonal;
        return "base: " + (base == null ? "pendiente" : base.name().toLowerCase(java.util.Locale.ROOT))
                + ", estación: " + seasonal + (state.declined ? " (rechazado: no se insiste)" : "");
    }

    public long getSent() { return sent.get(); }
    public long getLoaded() { return loaded.get(); }
    public long getDeclined() { return declined.get(); }
    public long getFailed() { return failed.get(); }
}
