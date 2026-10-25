package com.sovereignseasons.core.greenhouse;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * Lightweight immutable spatial coordinate key for hash-based caching.
 */
public record BlockPos(UUID worldUid, int x, int y, int z) {

    public BlockPos {
        Objects.requireNonNull(worldUid, "worldUid cannot be null");
    }

    public static BlockPos from(Location location) {
        Objects.requireNonNull(location, "location cannot be null");
        World world = Objects.requireNonNull(location.getWorld(), "location world cannot be null");
        return new BlockPos(world.getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean isInWorld(World world) {
        return world != null && world.getUID().equals(worldUid);
    }
}
