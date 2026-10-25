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
        if (!config.getAffectedCrops().contains(cropType)) {
            return; // Not an affected crop
        }

        // Check if inside a functional greenhouse
        if (greenhouseService.isGreenhouse(block.getLocation())) {
            return; // Greenhouse protects crops! Allow normal growth.
        }

        // Outside greenhouse in Winter: Pause growth
        event.setCancelled(true);

        // Roll for withering chance (5-10%, default 8%)
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < config.getWinterWitherChance()) {
            Material witherMaterial = config.getWitherMaterial();
            CropWinterWitherEvent witherEvent = new CropWinterWitherEvent(block, witherMaterial);
            callEventSafely(witherEvent);

            if (!witherEvent.isCancelled()) {
                block.setType(witherEvent.getReplacementMaterial(), true);

                if (config.isPlayWitherEffects()) {
                    try {
                        Location center = block.getLocation().add(0.5, 0.4, 0.5);
                        world.playSound(center, Sound.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 0.6f, 0.7f);
                        world.spawnParticle(Particle.SMOKE_NORMAL, center, 4, 0.2, 0.15, 0.2, 0.01);
                    } catch (Exception ignored) {}
                }
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
        if (sourceType != Material.PUMPKIN_STEM && sourceType != Material.MELON_STEM) {
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
        Block block = event.getBlock();
        Material type = block.getType();
        if (config.getAllowedGlassMaterials().contains(type) || config.getAllowedHeatSources().contains(type)) {
            greenhouseService.invalidateRadius(block.getLocation(), config.getHeatRadius() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (config.getAllowedGlassMaterials().contains(type) || config.getAllowedHeatSources().contains(type)) {
            greenhouseService.invalidateRadius(block.getLocation(), config.getHeatRadius() + 1);
        }
    }
}
