package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.CropWinterWitherEvent;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.core.config.SeasonsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enforces winter crop pausing and withering mechanics, respecting greenhouses.
 */
public class CropGrowthListener implements Listener {
    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final GreenhouseService greenhouseService;

    public CropGrowthListener(SeasonsConfig config, SeasonService seasonService, GreenhouseService greenhouseService) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.greenhouseService = Objects.requireNonNull(greenhouseService, "greenhouseService cannot be null");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!config.isWinterGrowthPaused()) {
            return;
        }

        Block block = event.getBlock();
        World world = block.getWorld();

        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }

        if (!seasonService.isWinter(world)) {
            return; // Only Winter pauses and withers crops
        }

        Material cropType = block.getType();
        Material newType = event.getNewState() != null ? event.getNewState().getType() : cropType;

        if (!config.getAffectedCrops().contains(cropType) && !config.getAffectedCrops().contains(newType)) {
            return; // Not an affected crop
        }

        // Check if inside a functional greenhouse
        if (greenhouseService.isGreenhouse(block.getLocation())) {
            return; // Greenhouse protects crops! Allow normal growth.
        }

        // Outside greenhouse in Winter: Pause growth
        event.setCancelled(true);

        // Roll for withering chance (5-10%, default 8%) only if an existing crop block is present
        if (config.getAffectedCrops().contains(cropType)) {
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < config.getWinterWitherChance()) {
                Material witherMaterial = config.getWitherMaterial();
                CropWinterWitherEvent witherEvent = new CropWinterWitherEvent(block, witherMaterial);
                callEventSafely(witherEvent);

                if (!witherEvent.isCancelled()) {
                    block.setType(witherEvent.getReplacementMaterial(), false);
                    playWitherEffects(world, block.getLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropFertilize(BlockFertilizeEvent event) {
        if (!config.isWinterGrowthPaused()) {
            return;
        }

        Block block = event.getBlock();
        World world = block.getWorld();

        if (!seasonService.isEnabledInWorld(world) || !seasonService.isWinter(world)) {
            return;
        }

        Material cropType = block.getType();
        boolean isAffected = config.getAffectedCrops().contains(cropType)
                || isSapling(cropType)
                || cropType == Material.PUMPKIN_STEM
                || cropType == Material.MELON_STEM
                || cropType == Material.ATTACHED_PUMPKIN_STEM
                || cropType == Material.ATTACHED_MELON_STEM;

        if (!isAffected) {
            return;
        }

        if (greenhouseService.isGreenhouse(block.getLocation())) {
            return; // Greenhouse protects crops! Allow fertilization.
        }

        // Cancel manual bone meal fertilization outside greenhouse in winter
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFruitSpread(BlockSpreadEvent event) {
        if (!config.isWinterGrowthPaused()) {
            return;
        }

        Block source = event.getSource();
        World world = source.getWorld();

        if (!seasonService.isEnabledInWorld(world) || !seasonService.isWinter(world)) {
            return;
        }

        Material sourceType = source.getType();
        if (sourceType != Material.PUMPKIN_STEM && sourceType != Material.MELON_STEM
                && sourceType != Material.ATTACHED_PUMPKIN_STEM && sourceType != Material.ATTACHED_MELON_STEM) {
            return;
        }

        if (greenhouseService.isGreenhouse(source.getLocation())) {
            return; // Protected by greenhouse
        }

        // Cancel spreading fruit outside greenhouse in winter
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSaplingGrow(StructureGrowEvent event) {
        if (!config.isWinterGrowthPaused()) {
            return;
        }

        Location loc = event.getLocation();
        World world = loc.getWorld();

        if (world == null || !seasonService.isEnabledInWorld(world) || !seasonService.isWinter(world)) {
            return;
        }

        if (greenhouseService.isGreenhouse(loc)) {
            return; // Protected by greenhouse
        }

        // Trees do not grow in open winter
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockModification(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handleBlockModification(event.getBlockPlaced());
    }

    private void handleBlockModification(Block block) {
        if (block == null) return;
        Material type = block.getType();
        boolean isGlass = config.getAllowedGlassMaterials().contains(type);
        boolean isHeat = config.getAllowedHeatSources().contains(type);
        boolean isOccluding = type.isOccluding();

        if (isGlass || isOccluding) {
            // Glass or occluding blocks affect vertical line of sight downwards
            int ceilingHeight = config.getMaxCeilingHeight();
            Location loc = block.getLocation();
            for (int dy = 0; dy <= ceilingHeight; dy++) {
                greenhouseService.invalidate(loc.clone().subtract(0, dy, 0));
            }
        }

        if (isHeat) {
            greenhouseService.invalidateRadius(block.getLocation(), config.getHeatRadius() + 1);
        }
    }

    private boolean isSapling(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.endsWith("_SAPLING") || name.endsWith("_PROPAGULE");
    }

    private void playWitherEffects(World world, Location location) {
        if (!config.isPlayWitherEffects() || world == null || location == null) {
            return;
        }
        try {
            Location center = location.clone().add(0.5, 0.4, 0.5);
            world.playSound(center, Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 0.6f, 0.7f);

            Particle particle = resolveParticle("SMOKE", "SMOKE_NORMAL");
            if (particle != null) {
                world.spawnParticle(particle, center, 4, 0.2, 0.15, 0.2, 0.01);
            }
        } catch (Throwable ignored) {
            // Safe guard against mock environments or missing sound/particle definitions
        }
    }

    private Particle resolveParticle(String... candidateNames) {
        for (String candidate : candidateNames) {
            try {
                return Particle.valueOf(candidate);
            } catch (IllegalArgumentException | NoSuchFieldError ignored) {}
        }
        return null;
    }

    private void callEventSafely(org.bukkit.event.Event event) {
        try {
            if (Bukkit.getServer() != null && Bukkit.getPluginManager() != null) {
                Bukkit.getPluginManager().callEvent(event);
            }
        } catch (Exception ignored) {}
    }
}
