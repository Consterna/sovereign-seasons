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


package com.sovereignseasons.commerce.bridge;

import com.sovereignseasons.logistics.api.LogisticsApi;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link CrateReader} backed by the Logistics API. Only referenced after checking that Logistics is installed.
 */
public final class LogisticsCrateReader implements CrateReader {
    private final LogisticsApi api;

    private LogisticsCrateReader(LogisticsApi api) {
        this.api = Objects.requireNonNull(api, "api cannot be null");
    }

    /**
     * Returns a reader if the Logistics API is registered, or null otherwise.
     */
    public static CrateReader create(ServicesManager services) {
        LogisticsApi api = services.load(LogisticsApi.class);
        return api != null ? new LogisticsCrateReader(api) : null;
    }

    @Override
    public Optional<Crate> read(ItemStack item) {
        return api.readCrate(item).map(info -> new Crate(info.content(), info.count()));
    }

    @Override
    public Optional<ItemStack> preview(ItemStack crate) {
        return api.previewContents(crate);
    }
}
