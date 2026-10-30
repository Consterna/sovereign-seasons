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

package com.sovereignseasons.resourcepack.pack;

import com.sovereignseasons.api.Season;
import com.sovereignseasons.resourcepack.config.PackConfig;
import com.sovereignseasons.resourcepack.icons.PackSeasonIcons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PackModelsTest {
    private static final Set<Season> DEFAULT = EnumSet.of(Season.SPRING, Season.AUTUMN, Season.WINTER);

    @Test
    @DisplayName("Each season has its pack except summer, which keeps the vanilla colours")
    void seasonalPack() {
        assertThat(PackRules.seasonalPack(Season.SPRING, DEFAULT, true)).isEqualTo("spring");
        assertThat(PackRules.seasonalPack(Season.AUTUMN, DEFAULT, true)).isEqualTo("autumn");
        assertThat(PackRules.seasonalPack(Season.WINTER, DEFAULT, true)).isEqualTo("winter");
        assertThat(PackRules.seasonalPack(Season.SUMMER, DEFAULT, true)).isNull();
        assertThat(PackRules.seasonalPack(Season.WINTER, DEFAULT, false)).as("seasons off in that world").isNull();
        assertThat(PackRules.seasonalPack(Season.WINTER, EnumSet.of(Season.AUTUMN), true)).isNull();
        assertThat(PackRules.seasonalPack(null, DEFAULT, true)).isNull();
    }

    @Test
    @DisplayName("Only the seasonal pack is swapped, and nothing new is pushed to a player who declined")
    void swaps() {
        assertThat(PackRules.swap(null, "spring", false)).isEqualTo(new PackRules.Swap(null, "spring"));
        assertThat(PackRules.swap("spring", "autumn", false)).isEqualTo(new PackRules.Swap("spring", "autumn"));
        assertThat(PackRules.swap("winter", null, false)).isEqualTo(new PackRules.Swap("winter", null));
        assertThat(PackRules.swap("autumn", "autumn", false).isNone()).isTrue();
        assertThat(PackRules.swap(null, null, false).isNone()).isTrue();
        assertThat(PackRules.swap("spring", "autumn", true)).isEqualTo(new PackRules.Swap("spring", null));
        assertThat(PackRules.swap(null, "autumn", true).isNone()).isTrue();
    }

    @Test
    @DisplayName("Pack ids are stable across restarts and the SHA-1 is the one of the bytes")
    void packFiles() {
        PackFile pack = PackFile.of("base", "abc".getBytes(StandardCharsets.US_ASCII));
        assertThat(pack.sha1Hex()).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d"); // SHA-1("abc"), FIPS 180 test vector
        assertThat(pack.sha1()).hasSize(20);
        assertThat(pack.id()).isEqualTo(PackFile.idFor("base")).isNotEqualTo(PackFile.idFor("winter"));
        assertThat(pack.fileName()).isEqualTo("sovereign-seasons-base.zip");
    }

    @Test
    @DisplayName("Download URLs use the address the player joined with, the public URL or the external host")
    void urls(@TempDir File dir) throws Exception {
        PackFile pack = PackFile.of("winter", new byte[]{1});
        PackConfig self = config(dir, "");
        assertThat(PackUrls.url(self, pack, "play.example.com", null))
                .isEqualTo("http://play.example.com:8163/sovereignseasons/sovereign-seasons-winter.zip");
        assertThat(PackUrls.url(self, pack, "2001:db8::1", null)).startsWith("http://[2001:db8::1]:8163/");
        assertThat(PackUrls.url(self, pack, null, "10.0.0.5")).startsWith("http://10.0.0.5:8163/");
        assertThat(PackUrls.url(self, pack, " ", "0.0.0.0")).startsWith("http://127.0.0.1:8163/");

        PackConfig publicUrl = config(dir, "hosting:\n  self:\n    public-url: \"https://packs.example.com:9000/\"\n");
        assertThat(PackUrls.url(publicUrl, pack, "play.example.com", null))
                .isEqualTo("https://packs.example.com:9000/sovereignseasons/sovereign-seasons-winter.zip");

        PackConfig external = config(dir, "hosting:\n  mode: external\n  external:\n    base-url: \"https://cdn.example.com/packs/\"\n");
        assertThat(PackUrls.url(external, pack, "play.example.com", null)).isEqualTo("https://cdn.example.com/packs/sovereign-seasons-winter.zip");
        PackConfig externalEmpty = config(dir, "hosting:\n  mode: external\n");
        assertThat(PackUrls.url(externalEmpty, pack, "play.example.com", null)).isNull();
    }

    @Test
    @DisplayName("Season icons map to the font glyphs and only show to players with the pack")
    void icons() {
        assertThat(PackSeasonIcons.glyph(Season.SPRING)).isEqualTo("<font:sovereignseasons:icons></font> ");
        assertThat(PackSeasonIcons.glyph(Season.WINTER)).contains("");
        assertThat(new PackSeasonIcons(p -> true, () -> true).icon(null, Season.SPRING)).isEmpty();
        assertThat(new PackSeasonIcons(p -> false, () -> true).icon(null, Season.SPRING)).isEmpty();
    }

    private static PackConfig config(File dir, String yaml) throws Exception {
        Files.writeString(new File(dir, "config.yml").toPath(), yaml, StandardCharsets.UTF_8);
        PackConfig config = new PackConfig(null);
        config.load(dir, null);
        return config;
    }
}
