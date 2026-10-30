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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sovereignseasons.logistics.crates.CrateModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the pack sources (src/pack) and the zips bundled in the plugin. Field names and formats were checked
 * with the game's own readers (see tools/resourcepack/README.md); this test keeps the files consistent.
 */
class PackContentTest {
    private static final Path SOURCES = Path.of(System.getProperty("sovereignseasons.packSources", "src/pack"));

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static BufferedImage image(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        assertThat(image).as(file.toString()).isNotNull();
        return image;
    }

    /** {@code namespace:path} of a texture or model to its file in the base pack. */
    private static Path asset(String id, String folder, String extension) {
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return SOURCES.resolve("base/assets/" + namespace + "/" + folder + "/" + path + extension);
    }

    @Test
    @DisplayName("Every pack declares the format range checked with the game: 1.20.4 (22) to 26.3 (97)")
    void packMetadata() throws IOException {
        for (String name : PackLibrary.NAMES) {
            JsonObject pack = json(SOURCES.resolve(name + "/pack.mcmeta")).getAsJsonObject("pack");
            assertThat(pack.get("pack_format").getAsInt()).as(name).isEqualTo(22);
            assertThat(pack.getAsJsonArray("supported_formats")).as(name).containsExactly(
                    JsonParser.parseString("22"), JsonParser.parseString("64"));
            assertThat(pack.get("min_format").getAsInt()).as(name).isEqualTo(22);
            assertThat(pack.get("max_format").getAsInt()).as(name).isEqualTo(97);
            assertThat(pack.get("description").getAsString()).as(name).startsWith("Sovereign Seasons");
            assertThat(image(SOURCES.resolve(name + "/pack.png")).getWidth()).isEqualTo(64);
        }
    }

    @Test
    @DisplayName("Every JSON file parses and every texture and model it names exists")
    void referencesResolve() throws IOException {
        List<Path> jsonFiles;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            jsonFiles = files.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".mcmeta")).toList();
        }
        assertThat(jsonFiles).hasSizeGreaterThanOrEqualTo(16);
        for (Path file : jsonFiles) {
            JsonElement parsed = JsonParser.parseString(Files.readString(file));
            assertThat(parsed.isJsonObject()).as(file.toString()).isTrue();
        }
        Path models = SOURCES.resolve("base/assets/sovereignseasons/models/item");
        try (Stream<Path> files = Files.list(models)) {
            for (Path model : files.toList()) {
                JsonObject textures = json(model).getAsJsonObject("textures");
                for (String key : List.of("top", "side", "bottom")) {
                    assertThat(asset(textures.get(key).getAsString(), "textures", ".png")).as(model + " " + key).isRegularFile();
                }
            }
        }
    }

    @Test
    @DisplayName("The barrel model picks a crate model by the custom model data Logistics gives each crate")
    void crateModelsMatchLogistics() throws IOException {
        JsonObject model = json(SOURCES.resolve("base/assets/minecraft/items/barrel.json")).getAsJsonObject("model");
        assertThat(model.get("type").getAsString()).isEqualTo("minecraft:range_dispatch");
        assertThat(model.get("property").getAsString()).isEqualTo("minecraft:custom_model_data");
        assertThat(model.get("index").getAsInt()).isZero();
        assertThat(model.getAsJsonObject("fallback").get("model").getAsString()).isEqualTo("minecraft:block/barrel");

        JsonArray entries = model.getAsJsonArray("entries");
        List<String> expected = new ArrayList<>();
        expected.add("sovereignseasons:item/crate");
        CrateModels.CROPS.forEach(crop -> expected.add("sovereignseasons:item/crate_" + crop.toLowerCase(Locale.ROOT)));
        expected.add("minecraft:block/barrel");
        assertThat(entries).hasSize(expected.size());
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i).getAsJsonObject();
            assertThat(entry.get("threshold").getAsInt()).isEqualTo(CrateModels.BASE + i);
            String target = entry.getAsJsonObject("model").get("model").getAsString();
            assertThat(target).isEqualTo(expected.get(i));
            if (target.startsWith("sovereignseasons:")) {
                assertThat(asset(target, "models", ".json")).isRegularFile();
            }
        }
        assertThat(CrateModels.BASE + entries.size() - 1).isEqualTo(CrateModels.END);

        // Up to 1.21.3 the same table lives in the overrides of the barrel item model, in ascending order
        JsonObject legacy = json(SOURCES.resolve("base/assets/minecraft/models/item/barrel.json"));
        assertThat(legacy.get("parent").getAsString()).isEqualTo("minecraft:block/barrel");
        JsonArray overrides = legacy.getAsJsonArray("overrides");
        assertThat(overrides).hasSize(expected.size());
        for (int i = 0; i < overrides.size(); i++) {
            JsonObject override = overrides.get(i).getAsJsonObject();
            assertThat(override.getAsJsonObject("predicate").get("custom_model_data").getAsInt()).isEqualTo(CrateModels.BASE + i);
            assertThat(override.get("model").getAsString()).isEqualTo(expected.get(i));
        }
    }

    @Test
    @DisplayName("Seasonal colormaps are 256x256 and the icon font has one 8x8 glyph per season")
    void texturesHaveTheRightSize() throws IOException {
        for (String season : List.of("spring", "autumn", "winter")) {
            for (String map : List.of("grass", "foliage", "dry_foliage")) {
                BufferedImage image = image(SOURCES.resolve(season + "/assets/minecraft/textures/colormap/" + map + ".png"));
                assertThat(image.getWidth()).as(season + " " + map).isEqualTo(256);
                assertThat(image.getHeight()).as(season + " " + map).isEqualTo(256);
            }
        }
        assertThat(SOURCES.resolve("summer")).as("summer keeps the vanilla colours").doesNotExist();

        JsonObject provider = json(SOURCES.resolve("base/assets/sovereignseasons/font/icons.json"))
                .getAsJsonArray("providers").get(0).getAsJsonObject();
        assertThat(provider.get("type").getAsString()).isEqualTo("bitmap");
        String chars = provider.getAsJsonArray("chars").get(0).getAsString();
        assertThat(chars).isEqualTo("");
        assertThat(provider.get("ascent").getAsInt()).isLessThanOrEqualTo(provider.get("height").getAsInt());
        BufferedImage strip = image(asset(provider.get("file").getAsString(), "textures", ""));
        assertThat(strip.getWidth()).isEqualTo(8 * chars.length());
        assertThat(strip.getHeight()).isEqualTo(8);
    }

    @Test
    @DisplayName("The zips bundled in the plugin hold exactly the pack sources")
    void bundledZipsMatchSources() throws IOException {
        for (String name : PackLibrary.NAMES) {
            Map<String, byte[]> zipped = new HashMap<>();
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("packs/sovereign-seasons-" + name + ".zip")) {
                assertThat(in).as(name).isNotNull();
                try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(in.readAllBytes()))) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (!entry.isDirectory()) {
                            zipped.put(entry.getName(), zip.readAllBytes());
                        }
                    }
                }
            }
            Path root = SOURCES.resolve(name);
            List<Path> sources;
            try (Stream<Path> files = Files.walk(root)) {
                sources = files.filter(Files::isRegularFile).toList();
            }
            assertThat(zipped).as(name).hasSize(sources.size()).containsKey("pack.mcmeta");
            for (Path source : sources) {
                String entry = root.relativize(source).toString().replace('\\', '/');
                assertThat(zipped.get(entry)).as(name + "/" + entry).isEqualTo(Files.readAllBytes(source));
            }
        }
    }
}
