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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The resource packs bundled in the plugin JAR ({@code packs/sovereign-seasons-<name>.zip}, built by Gradle from
 * {@code src/pack/<name>}). They are kept in memory and also copied to {@code plugins/<plugin>/packs/} so that an
 * administrator can upload them to their own host.
 */
public final class PackLibrary {
    public static final List<String> NAMES = List.of("base", "spring", "autumn", "winter");

    private final Map<String, PackFile> packs;

    private PackLibrary(Map<String, PackFile> packs) {
        this.packs = Collections.unmodifiableMap(packs);
    }

    /**
     * @param resources reads a bundled resource by path (the plugin's {@code getResource}); null if missing
     */
    public static PackLibrary load(Function<String, InputStream> resources, Logger logger) throws IOException {
        Map<String, PackFile> loaded = new LinkedHashMap<>();
        for (String name : NAMES) {
            String path = "packs/sovereign-seasons-" + name + ".zip";
            try (InputStream in = resources.apply(path)) {
                if (in == null) {
                    if (logger != null) {
                        logger.warning("Bundled resource pack missing: " + path);
                    }
                    continue;
                }
                loaded.put(name, PackFile.of(name, in.readAllBytes()));
            }
        }
        return new PackLibrary(loaded);
    }

    public Optional<PackFile> get(String name) {
        return Optional.ofNullable(name == null ? null : packs.get(name));
    }

    public Map<String, PackFile> all() {
        return packs;
    }

    /**
     * Copies every pack to {@code folder}, rewriting only the files whose content changed.
     */
    public void export(File folder) throws IOException {
        Files.createDirectories(folder.toPath());
        for (PackFile pack : packs.values()) {
            File target = new File(folder, pack.fileName());
            if (target.isFile() && java.util.Arrays.equals(Files.readAllBytes(target.toPath()), pack.data())) {
                continue;
            }
            Files.write(target.toPath(), pack.data());
        }
    }
}
