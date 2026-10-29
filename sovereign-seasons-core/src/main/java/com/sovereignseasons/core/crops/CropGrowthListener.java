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

package com.sovereignseasons.core.crops;

import com.sovereignseasons.api.calendar.SeasonService;
import com.sovereignseasons.api.event.CropWinterWitherEvent;
import com.sovereignseasons.api.greenhouse.GreenhouseService;
import com.sovereignseasons.api.Season;
import com.sovereignseasons.api.temperature.TemperatureBand;
import com.sovereignseasons.api.temperature.TemperatureService;
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
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import com.sovereignseasons.core.greenhouse.GreenhouseDetector;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enforces winter crop pausing and withering mechanics, respecting greenhouses.
 */
public class CropGrowthListener implements Listener {
    private final SeasonsConfig config;
    private final SeasonService seasonService;
    private final GreenhouseService greenhouseService;
    private final TemperatureService temperatureService;
    private final CropGrowthModel growthModel;

    public CropGrowthListener(SeasonsConfig config, SeasonService seasonService, GreenhouseService greenhouseService) {
        this(config, seasonService, greenhouseService, null);
    }

    public CropGrowthListener(SeasonsConfig config, SeasonService seasonService, GreenhouseService greenhouseService, TemperatureService temperatureService) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.seasonService = Objects.requireNonNull(seasonService, "seasonService cannot be null");
        this.greenhouseService = Objects.requireNonNull(greenhouseService, "greenhouseService cannot be null");
        this.temperatureService = temperatureService;
        this.growthModel = new CropGrowthModel(config);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        World world = block.getWorld();

        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }

        Material cropType = block.getType();
        Material newType = event.getNewState() != null ? event.getNewState().getType() : cropType;

        if (!config.isWinterGrowthPaused() || !seasonService.isWinter(world)) {
            // Outside winter: seasonal fertility and climate preferences slow growth down
            if (!allowSeasonalGrowth(block, cropType, newType, seasonService.getSeason(world))) {
                event.setCancelled(true);
            }
            return;
        }

        if (!config.getAffectedCrops().contains(cropType) && !config.getAffectedCrops().contains(newType)) {
            // Not frozen by winter (e.g. sugar cane, cactus): still subject to seasonal fertility
            if (!allowSeasonalGrowth(block, cropType, newType, Season.WINTER)) {
                event.setCancelled(true);
            }
            return;
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
                    Material rep = witherEvent.getReplacementMaterial();
                    block.setType((rep != null && rep.isBlock()) ? rep : Material.DEAD_BUSH, false);
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
        Block source = event.getSource();
        World world = source.getWorld();

        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }

        Material sourceType = source.getType();
        if (sourceType != Material.PUMPKIN_STEM && sourceType != Material.MELON_STEM
                && sourceType != Material.ATTACHED_PUMPKIN_STEM && sourceType != Material.ATTACHED_MELON_STEM) {
            return;
        }

        if (!config.isWinterGrowthPaused() || !seasonService.isWinter(world)) {
            if (!allowSeasonalGrowth(source, sourceType, sourceType, seasonService.getSeason(world))) {
                event.setCancelled(true);
            }
            return;
        }

        // Both the stem and the target block where fruit appears must be inside greenhouse
        if (greenhouseService.isGreenhouse(source.getLocation()) && greenhouseService.isGreenhouse(event.getBlock().getLocation())) {
            return; // Protected by greenhouse
        }

        // Cancel spreading fruit outside greenhouse in winter
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWaterFreeze(BlockFormEvent event) {
        if (!config.isPreventFreezeAndSnow()) {
            return;
        }

        Block block = event.getBlock();
        World world = block.getWorld();

        if (!seasonService.isEnabledInWorld(world)) {
            return;
        }

        Material newType = event.getNewState() != null ? event.getNewState().getType() : Material.AIR;
        if (newType == Material.ICE || newType == Material.FROSTED_ICE || newType == Material.SNOW) {
            // Protect water and crops inside a functional greenhouse from freezing or snow accumulation
            if (greenhouseService.isGreenhouse(block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSaplingGrow(StructureGrowEvent event) {
        if (!config.isWinterGrowthPaused()) {
            return;
        }

        Location loc = event.getLocation();
        if (loc == null) {
            return;
        }
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        handleBlockModification(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        handleBlockModification(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        handleBlockModification(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Material mat = event.getClickedBlock().getType();
        if (config.getAllowedHeatSources().contains(mat) || mat.name().contains("CAMPFIRE")) {
            handleBlockModification(event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            handleBlockModification(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            handleBlockModification(b);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block placed = event.getBlockClicked().getRelative(event.getBlockFace());
        handleBlockModification(placed);
        if (event.getBucket() == Material.LAVA_BUCKET) {
            greenhouseService.invalidateRadius(placed.getLocation(), config.getHeatRadius() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block filled = event.getBlockClicked();
        handleBlockModification(filled);
        greenhouseService.invalidateRadius(filled.getLocation(), config.getHeatRadius() + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            handleBlockModification(b);
            handleBlockModification(b.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            handleBlockModification(b);
            handleBlockModification(b.getRelative(event.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCauldronLevelChange(CauldronLevelChangeEvent event) {
        Block block = event.getBlock();
        if (block == null) return;
        Material oldType = block.getType();
        Material newType = event.getNewState() != null ? event.getNewState().getType() : Material.AIR;
        if (config.getAllowedHeatSources().contains(oldType) || config.getAllowedHeatSources().contains(newType)) {
            greenhouseService.invalidateRadius(block.getLocation(), config.getHeatRadius() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        Block block = event.getBlock();
        if (block != null && config.getAllowedHeatSources().contains(block.getType())) {
            greenhouseService.invalidateRadius(block.getLocation(), config.getHeatRadius() + 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        handleBlockModification(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        handleBlockModification(event.getBlock());
        handleBlockModification(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (event.isCancelled()) return;
        World world = event.getWorld();
        if (greenhouseService instanceof GreenhouseDetector gd) {
            gd.invalidateWorld(world.getUID());
        }
    }

    private void handleBlockModification(Block block) {
        if (block == null) return;
        Material type = block.getType();
        boolean isGlass = config.getAllowedGlassMaterials().contains(type);
        boolean isHeat = config.getAllowedHeatSources().contains(type);
        boolean isSolid = type.isSolid();
        boolean isOccluding = type.isOccluding();

        if (isGlass || isOccluding || isSolid) {
            // Glass or solid/occluding blocks affect vertical line of sight downwards
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
        return name.endsWith("_SAPLING") || name.endsWith("_PROPAGULE") || name.equals("AZALEA") || name.equals("FLOWERING_AZALEA");
    }

    /**
     * Rolls the seasonal growth chance for a crop. Greenhouse and climate lookups only happen when
     * the crop has a profile, so unrelated block growth (grass, vines...) stays cheap.
     */
    private boolean allowSeasonalGrowth(Block block, Material cropType, Material newType, Season season) {
        if (!config.isSeasonalGrowthEnabled()) {
            return true;
        }
        CropProfile profile = config.getCropProfile(cropType);
        if (profile == null) {
            profile = config.getCropProfile(newType);
        }
        if (profile == null) {
            return true;
        }
        TemperatureBand band = null;
        if (temperatureService != null) {
            try {
                band = temperatureService.getBand(block.getLocation());
            } catch (RuntimeException ignored) {
                // Unknown climate: only the season factor applies
            }
        }
        boolean underground = isUnderground(block);
        double chance = growthModel.growthChance(profile, season, band, false, underground);
        if (chance >= 1.0) {
            return true;
        }
        if (greenhouseService.isGreenhouse(block.getLocation())) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private boolean isUnderground(Block block) {
        try {
            return block.getY() <= config.getUndergroundY() && block.getLightFromSky() == 0;
        } catch (RuntimeException e) {
            return false;
        }
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
