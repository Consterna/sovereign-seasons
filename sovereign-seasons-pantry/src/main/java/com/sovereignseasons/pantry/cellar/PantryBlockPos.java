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

package com.sovereignseasons.pantry.cellar;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight immutable spatial key for the cellar cache.
 */
public record PantryBlockPos(UUID worldUid, int x, int y, int z) {

    public PantryBlockPos {
        Objects.requireNonNull(worldUid, "worldUid cannot be null");
    }

    public static PantryBlockPos from(Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        UUID uid = world.getUID();
        if (uid == null) {
            uid = new UUID(0L, 0L);
        }
        return new PantryBlockPos(uid, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public PantryBlockPos offset(int dx, int dy, int dz) {
        return new PantryBlockPos(worldUid, x + dx, y + dy, z + dz);
    }
}
