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

package com.sovereignseasons.logistics.ice;

import java.util.Arrays;

/**
 * Pure helpers for the per-chunk registry of seasonal ice, stored as a sorted {@code long[]} of packed
 * chunk-local positions. Only ice recorded here is ever melted, so natural and player-placed ice is safe.
 */
public final class IceRegistry {
    private static final long Y_OFFSET = 1L << 20;

    private IceRegistry() {}

    /**
     * Packs chunk-local coordinates (x and z in 0..15, any y) into a single long.
     */
    public static long pack(int localX, int y, int localZ) {
        return ((y + Y_OFFSET) << 8) | ((long) (localX & 15) << 4) | (localZ & 15);
    }

    public static int localX(long packed) {
        return (int) ((packed >> 4) & 15);
    }

    public static int localZ(long packed) {
        return (int) (packed & 15);
    }

    public static int y(long packed) {
        return (int) ((packed >> 8) - Y_OFFSET);
    }

    public static boolean contains(long[] registry, long packed) {
        return registry != null && Arrays.binarySearch(registry, packed) >= 0;
    }

    public static long[] add(long[] registry, long packed) {
        long[] base = registry != null ? registry : new long[0];
        int index = Arrays.binarySearch(base, packed);
        if (index >= 0) {
            return base;
        }
        int insert = -index - 1;
        long[] result = new long[base.length + 1];
        System.arraycopy(base, 0, result, 0, insert);
        result[insert] = packed;
        System.arraycopy(base, insert, result, insert + 1, base.length - insert);
        return result;
    }

    public static long[] remove(long[] registry, long packed) {
        if (registry == null) {
            return new long[0];
        }
        int index = Arrays.binarySearch(registry, packed);
        if (index < 0) {
            return registry;
        }
        long[] result = new long[registry.length - 1];
        System.arraycopy(registry, 0, result, 0, index);
        System.arraycopy(registry, index + 1, result, index, registry.length - index - 1);
        return result;
    }

    /**
     * Returns the registry without its first {@code count} entries.
     */
    public static long[] dropFirst(long[] registry, int count) {
        if (registry == null || count >= registry.length) {
            return new long[0];
        }
        return Arrays.copyOfRange(registry, Math.max(0, count), registry.length);
    }
}
