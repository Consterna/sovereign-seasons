package com.sovereignseasons.core.config;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonsConfigTest {

    @Test
    @DisplayName("Loads built-in defaults correctly")
    void shouldLoadDefaults(@TempDir File tempDir) {
        SeasonsConfig config = new SeasonsConfig(null);
        config.load(tempDir, null);

        assertThat(config.getDaysPerSeason()).isEqualTo(7);
        assertThat(config.getTicksPerDay()).isEqualTo(24000L);
        assertThat(config.isWinterGrowthPaused()).isTrue();
        assertThat(config.getWinterWitherChance()).isEqualTo(0.08); // 8% default
        assertThat(config.getWitherMaterial()).isEqualTo(Material.DEAD_BUSH);
        assertThat(config.isGreenhouseEnabled()).isTrue();
        assertThat(config.getHeatRadius()).isEqualTo(4);
        assertThat(config.getMaxCeilingHeight()).isEqualTo(16);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(30);
        assertThat(config.getCacheMaxSize()).isEqualTo(10000);

        // Standard crops and glass
        assertThat(config.getAffectedCrops()).contains(Material.WHEAT, Material.CARROTS, Material.POTATOES);
        assertThat(config.getAllowedGlassMaterials()).contains(Material.GLASS, Material.TINTED_GLASS);
        assertThat(config.getAllowedHeatSources()).contains(Material.CAMPFIRE, Material.TORCH, Material.LAVA);
    }

    @Test
    @DisplayName("Loads custom YAML and enforces validation boundaries")
    void shouldLoadCustomYamlAndEnforceBoundaries(@TempDir File tempDir) {
        String yaml = """
                calendar:
                  days-per-season: 10
                  ticks-per-day: 20000
                  enabled-worlds:
                    - survival_world
                crops:
                  winter-growth-paused: false
                  winter-wither-chance: 0.10
                  wither-material: AIR
                greenhouse:
                  heat-radius: 6
                  max-ceiling-height: 20
                  cache-ttl-seconds: 60
                  cache-max-size: 5000
                """;

        SeasonsConfig config = new SeasonsConfig(null);
        config.load(tempDir, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertThat(config.getDaysPerSeason()).isEqualTo(10);
        assertThat(config.getTicksPerDay()).isEqualTo(20000L);
        assertThat(config.getEnabledWorlds()).containsExactly("survival_world");
        assertThat(config.isWinterGrowthPaused()).isFalse();
        assertThat(config.getWinterWitherChance()).isEqualTo(0.10);
        assertThat(config.getWitherMaterial()).isEqualTo(Material.AIR);
        assertThat(config.getHeatRadius()).isEqualTo(6);
        assertThat(config.getMaxCeilingHeight()).isEqualTo(20);
        assertThat(config.getCacheTtlSeconds()).isEqualTo(60);
        assertThat(config.getCacheMaxSize()).isEqualTo(5000);
    }
}
