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


import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.server.Bootstrap;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Parses the resource pack JSON files with the game client's own readers:
 * item model definitions (assets/{ns}/items), block/item models (assets/{ns}/models) and fonts (assets/{ns}/font).
 */
public class ClientPackCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The same registrations as net.minecraft.client.ClientBootstrap, minus graphics and dialogs
        net.minecraft.client.renderer.item.ItemModels.bootstrap();
        net.minecraft.client.color.item.ItemTintSources.bootstrap();
        // SelectItemModelProperties is skipped: it needs ICU4J (a client-only library) and the pack does not use select
        net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties.bootstrap();
        net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties.bootstrap();
        int errors = 0;
        for (String root : args) {
            try (Stream<Path> files = Files.walk(Path.of(root))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String rel = Path.of(root).relativize(file).toString().replace('\\', '/');
                    String outcome;
                    try {
                        if (rel.contains("/items/")) {
                            JsonElement json = JsonParser.parseString(Files.readString(file));
                            var result = ClientItem.CODEC.parse(JsonOps.INSTANCE, json);
                            outcome = result.result().map(item -> "OK " + item.model().getClass().getSimpleName())
                                    .orElseGet(() -> "ERROR " + result.error().map(e -> e.message()).orElse("?"));
                        } else if (rel.contains("/models/")) {
                            try (Reader reader = Files.newBufferedReader(file)) {
                                CuboidModel model = CuboidModel.fromStream(reader);
                                outcome = "OK parent=" + model.parent();
                            }
                        } else if (rel.contains("/font/")) {
                            JsonElement json = JsonParser.parseString(Files.readString(file));
                            // FontManager.FontDefinitionFile is private: read its CODEC by reflection
                            Class<?> type = Class.forName("net.minecraft.client.gui.font.FontManager$FontDefinitionFile");
                            var field = type.getDeclaredField("CODEC");
                            field.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            var codec = (com.mojang.serialization.Codec<Object>) field.get(null);
                            var result = codec.parse(JsonOps.INSTANCE, json);
                            outcome = result.result().map(def -> "OK " + def)
                                    .orElseGet(() -> "ERROR " + result.error().map(e -> e.message()).orElse("?"));
                        } else {
                            continue;
                        }
                    } catch (Exception e) {
                        outcome = "ERROR " + e;
                    }
                    if (outcome.startsWith("ERROR")) {
                        errors++;
                    }
                    System.out.println(rel + " -> " + outcome);
                }
            }
        }
        System.out.println("errors=" + errors);
    }
}
