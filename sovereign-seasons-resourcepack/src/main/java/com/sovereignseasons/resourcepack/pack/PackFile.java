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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * One resource pack zip in memory, with the SHA-1 the client checks and a stable id.
 *
 * @param name logical name: {@code base}, {@code spring}, {@code autumn} or {@code winter}
 * @param data zip bytes
 * @param sha1 SHA-1 of the zip (20 bytes, what the client verifies after downloading)
 * @param id   stable pack id, the same on every restart, so a seasonal pack can always be removed by id
 */
public record PackFile(String name, byte[] data, byte[] sha1, UUID id) {

    public PackFile {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(sha1, "sha1 cannot be null");
        Objects.requireNonNull(id, "id cannot be null");
    }

    public static PackFile of(String name, byte[] data) {
        return new PackFile(name, data, sha1(data), idFor(name));
    }

    public static UUID idFor(String name) {
        return UUID.nameUUIDFromBytes(("sovereignseasons:resourcepack:" + name).getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by every Java runtime", e);
        }
    }

    public String sha1Hex() {
        return HexFormat.of().formatHex(sha1);
    }

    /** File name used for hosting: {@code sovereign-seasons-<name>.zip}. */
    public String fileName() {
        return "sovereign-seasons-" + name + ".zip";
    }
}
