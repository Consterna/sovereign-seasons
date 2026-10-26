package com.sovereignseasons.core.greenhouse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sovereignseasons.api.greenhouse.GreenhouseReport;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Campfire;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance greenhouse detector with spatial LRU caching.
 */
public class GreenhouseDetector implements GreenhouseService {
    private final SeasonsConfig config;
    private volatile Cache<BlockPos, GreenhouseReport> cache;

    public GreenhouseDetector(SeasonsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        rebuildCache();
    }

    public synchronized void rebuildCache() {
        this.cache = CacheBuilder.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(config.getCacheTtlSeconds(), TimeUnit.SECONDS)
                .recordStats()
                .build();
    }

    @Override
    public boolean isGreenhouse(Location location) {
        if (!config.isGreenhouseEnabled()) {
            return false;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }

        BlockPos key = BlockPos.from(location);
        GreenhouseReport cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached.isGreenhouse();
        }

        GreenhouseReport evaluated = inspectInternal(location, false);
        cache.put(key, evaluated);
        return evaluated.isGreenhouse();
    }

    @Override
    public GreenhouseReport inspect(Location location) {
        if (location == null || location.getWorld() == null) {
            return GreenhouseReport.failure(
                    location, false, false, null, null, 0, false, null, null, 0.0, "Ubicación o mundo inválido"
            );
        }

        BlockPos key = BlockPos.from(location);
        GreenhouseReport cached = cache.getIfPresent(key);
        if (cached != null) {
            // Return copy marked as cached
            return new GreenhouseReport(
                    cached.queryLocation(),
                    cached.isGreenhouse(),
                    true,
                    cached.glassCeilingValid(),
                    cached.glassLocation(),
                    cached.glassMaterial(),
                    cached.heatSourceValid(),
                    cached.heatSourceLocation(),
                    cached.heatSourceMaterial(),
                    cached.distanceToCeiling(),
                    cached.distanceToHeatSource(),
                    cached.failureReason()
            );
        }

        GreenhouseReport evaluated = inspectInternal(location, false);
        cache.put(key, evaluated);
        return evaluated;
    }

    private GreenhouseReport inspectInternal(Location location, boolean fromCache) {
        World world = location.getWorld();
        if (world == null) {
            return GreenhouseReport.failure(
                    location, fromCache, false, null, null, 0, false, null, null, 0.0, "El mundo es nulo"
            );
        }

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        int minHeight = getMinHeightSafely(world);
        int maxHeight = getMaxHeightSafely(world);

        // Boundary safety: verify Y is within world height bounds (-64 to 320 in 1.20+)
        if (baseY < minHeight || baseY >= maxHeight) {
            return GreenhouseReport.failure(
                    location, fromCache, false, null, null, 0, false, null, null, 0.0,
                    "Coordenadas fuera de los límites verticales del mundo"
            );
        }

        // Chunk loading safety: do not synchronously force-load chunks during raycast
        if (!isChunkLoadedSafely(world, baseX >> 4, baseZ >> 4)) {
            return GreenhouseReport.failure(
                    location, fromCache, false, null, null, 0, false, null, null, 0.0,
                    "El chunk no está cargado"
            );
        }

        // 1. Vertical Glass Ceiling Verification
        boolean glassValid = false;
        Location glassLoc = null;
        Material glassMat = null;
        int distanceToCeiling = 0;
        String failureReason = null;

        if (config.isRequireGlassCeiling()) {
            int maxCeilingY = Math.min(maxHeight - 1, baseY + config.getMaxCeilingHeight());
            for (int y = baseY + 1; y <= maxCeilingY; y++) {
                Block blockAbove = world.getBlockAt(baseX, y, baseZ);
                if (blockAbove == null) {
                    break;
                }
                Material type = blockAbove.getType();

                if (config.getAllowedGlassMaterials().contains(type)) {
                    glassValid = true;
                    glassLoc = blockAbove.getLocation();
                    glassMat = type;
                    distanceToCeiling = y - baseY;
                    break;
                }

                // If block is solid (slabs, stairs, full cubes, etc.) or occluding, it obstructs the roof
                if (!type.isAir() && (type.isSolid() || type.isOccluding())) {
                    failureReason = "Obstrucción sólida (" + type.name() + ") bajo el techo en Y=" + y;
                    break;
                }
            }

            if (!glassValid && failureReason == null) {
                failureReason = "No se encontró techo de cristal dentro de " + config.getMaxCeilingHeight() + " bloques";
            }
        } else {
            glassValid = true; // Bypassed
        }

        // 2. Heat Source Cubic Radius Verification
        boolean heatValid = false;
        Location heatLoc = null;
        Material heatMat = null;
        double distanceToHeat = 0.0;

        if (config.isRequireHeatSource()) {
            int radius = config.getHeatRadius();
            int minX = baseX - radius;
            int maxX = baseX + radius;
            int minY = Math.max(minHeight, baseY - radius);
            int maxY = Math.min(maxHeight - 1, baseY + radius);
            int minZ = baseZ - radius;
            int maxZ = baseZ + radius;

            scanLoop:
            for (int x = minX; x <= maxX; x++) {
                int cx = x >> 4;
                for (int z = minZ; z <= maxZ; z++) {
                    int cz = z >> 4;
                    // Skip unloaded chunks to prevent synchronous chunk load lag and Folia async issues
                    if (!isChunkLoadedSafely(world, cx, cz)) {
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        Block nearbyBlock = world.getBlockAt(x, y, z);
                        if (nearbyBlock == null) {
                            continue;
                        }
                        Material nearbyType = nearbyBlock.getType();

                        if (config.getAllowedHeatSources().contains(nearbyType)) {
                            // Check if block needs to be lit
                            if (isHeatSourceActive(nearbyBlock)) {
                                heatValid = true;
                                heatLoc = nearbyBlock.getLocation();
                                heatMat = nearbyType;
                                distanceToHeat = location.distance(heatLoc);
                                break scanLoop;
                            }
                        }
                    }
                }
            }

            if (!heatValid && failureReason == null) {
                failureReason = "No se encontró una fuente de calor encendida en un radio de " + radius + " bloques";
            }
        } else {
            heatValid = true; // Bypassed
        }

        boolean overallValid = glassValid && heatValid;
        if (overallValid) {
            return GreenhouseReport.success(
                    location, fromCache, glassLoc, glassMat, distanceToCeiling, heatLoc, heatMat, distanceToHeat
            );
        } else {
            return GreenhouseReport.failure(
                    location, fromCache, glassValid, glassLoc, glassMat, distanceToCeiling,
                    heatValid, heatLoc, heatMat, distanceToHeat, failureReason
            );
        }
    }

    private boolean isChunkLoadedSafely(World world, int chunkX, int chunkZ) {
        try {
            return world.isChunkLoaded(chunkX, chunkZ);
        } catch (Throwable ignored) {
            return true; // Graceful fallback for mock worlds in tests
        }
    }

    private int getMinHeightSafely(World world) {
        try {
            return world.getMinHeight();
        } catch (Throwable ignored) {
            return -64;
        }
    }

    private int getMaxHeightSafely(World world) {
        try {
            return world.getMaxHeight();
        } catch (Throwable ignored) {
            return 320;
        }
    }

    private boolean isHeatSourceActive(Block block) {
        BlockData data;
        try {
            data = block.getBlockData();
        } catch (Throwable ignored) {
            return true;
        }
        if (data == null) {
            return true;
        }
        try {
            if (data instanceof org.bukkit.block.data.Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                return false;
            }
        } catch (Throwable ignored) {}
        if (data instanceof Campfire campfire) {
            return campfire.isLit();
        }
        if (data instanceof Lightable lightable) {
            return lightable.isLit();
        }
        // Always active: Lava, Magma Block, Fire, etc.
        return true;
    }

    /**
     * Purges all cached greenhouse positions belonging to an unloaded world to prevent world memory leaks.
     */
    public void invalidateWorld(java.util.UUID worldUid) {
        if (worldUid == null || cache == null) return;
        cache.asMap().keySet().removeIf(pos -> pos.worldUid().equals(worldUid));
    }

    @Override
    public void invalidate(Location location) {
        if (location == null || location.getWorld() == null) return;
        cache.invalidate(BlockPos.from(location));
    }

    @Override
    public void invalidateRadius(Location location, int radius) {
        if (location == null || location.getWorld() == null) return;
        World world = location.getWorld();
        java.util.UUID uid = world.getUID();
        if (uid == null) return;

        int safeRadius = Math.max(0, Math.min(32, radius));
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();

        for (int x = bx - safeRadius; x <= bx + safeRadius; x++) {
            for (int y = by - safeRadius; y <= by + safeRadius; y++) {
                for (int z = bz - safeRadius; z <= bz + safeRadius; z++) {
                    cache.invalidate(new BlockPos(uid, x, y, z));
                }
            }
        }
    }

    @Override
    public void clearCache() {
        rebuildCache();
    }

    @Override
    public long getCacheHits() {
        return cache.stats().hitCount();
    }

    @Override
    public long getCacheMisses() {
        return cache.stats().missCount();
    }

    @Override
    public long getCacheSize() {
        return cache.size();
    }
}
