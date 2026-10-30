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

import com.sovereignseasons.resourcepack.config.PackConfig;

/**
 * Where a client downloads a pack from.
 */
public final class PackUrls {
    /** Path prefix of the built-in server. */
    public static final String PATH_PREFIX = "/sovereignseasons/";

    private PackUrls() {
    }

    public static String path(PackFile pack) {
        return PATH_PREFIX + pack.fileName();
    }

    /**
     * @param connectedHost host name or address the player typed to join (from the handshake), or null
     * @param fallbackHost  the server's configured address, or null
     * @return the download URL, or null when no usable URL can be built
     */
    public static String url(PackConfig config, PackFile pack, String connectedHost, String fallbackHost) {
        if (config.getHostingMode() == PackConfig.HostingMode.EXTERNAL) {
            String base = config.getExternalBaseUrl();
            return base.isEmpty() ? null : base + "/" + pack.fileName();
        }
        if (!config.getPublicUrl().isEmpty()) {
            return config.getPublicUrl() + path(pack);
        }
        String host = usable(connectedHost) ? connectedHost : (usable(fallbackHost) ? fallbackHost : "127.0.0.1");
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            host = "[" + host + "]"; // IPv6 literal
        }
        return "http://" + host + ":" + config.getPort() + path(pack);
    }

    private static boolean usable(String host) {
        return host != null && !host.isBlank() && !host.equals("0.0.0.0") && !host.equals("::");
    }
}
