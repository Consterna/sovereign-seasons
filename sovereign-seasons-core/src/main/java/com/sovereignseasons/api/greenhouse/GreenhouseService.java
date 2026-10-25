package com.sovereignseasons.api.greenhouse;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Service responsible for greenhouse detection and spatial caching.
 */
public interface GreenhouseService {

    /**
     * Checks if a location meets all requirements to be considered a functional greenhouse.
     */
    boolean isGreenhouse(Location location);

    /**
     * Checks if a block is inside a functional greenhouse.
     */
    default boolean isGreenhouse(Block block) {
        if (block == null) return false;
        return isGreenhouse(block.getLocation());
    }

    /**
     * Performs a full diagnostic evaluation and returns a detailed report.
     */
    GreenhouseReport inspect(Location location);

    /**
     * Invalidates any cached evaluation for the given location.
     */
    void invalidate(Location location);

    /**
     * Invalidates cached evaluations within a spherical/cubic radius around the given location.
     */
    void invalidateRadius(Location location, int radius);

    /**
     * Clears all cached greenhouse evaluations.
     */
    void clearCache();

    /**
     * Returns total number of cache hits since server start or cache reset.
     */
    long getCacheHits();

    /**
     * Returns total number of cache misses since server start or cache reset.
     */
    long getCacheMisses();

    /**
     * Returns the current number of active cached positions in the spatial LRU cache.
     */
    long getCacheSize();
}
