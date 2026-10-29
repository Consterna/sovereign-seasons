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


package com.sovereignseasons.logistics.api;

import com.sovereignseasons.logistics.crates.CrateItems;
import com.sovereignseasons.logistics.crates.CrateUnpacker;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link LogisticsApi} backed by the crate items and the shared unpacker.
 */
public class LogisticsApiImpl implements LogisticsApi {
    private final CrateItems crates;
    private final CrateUnpacker unpacker;

    public LogisticsApiImpl(CrateItems crates, CrateUnpacker unpacker) {
        this.crates = Objects.requireNonNull(crates, "crates cannot be null");
        this.unpacker = Objects.requireNonNull(unpacker, "unpacker cannot be null");
    }

    @Override
    public Optional<CrateInfo> readCrate(ItemStack item) {
        return crates.read(item).map(contents -> new CrateInfo(contents.content(), contents.count()));
    }

    @Override
    public Optional<ItemStack> previewContents(ItemStack crate) {
        return crates.read(crate).map(unpacker::unpack);
    }
}
