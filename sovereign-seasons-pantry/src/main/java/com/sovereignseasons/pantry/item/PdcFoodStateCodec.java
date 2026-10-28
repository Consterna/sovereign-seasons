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

package com.sovereignseasons.pantry.item;

import com.sovereignseasons.pantry.food.FoodState;
import com.sovereignseasons.pantry.food.FreshnessStage;
import com.sovereignseasons.pantry.food.Preservation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores food decay state in the item's PersistentDataContainer and renders it as lore.
 * Only the freshness stage is shown (not a live percentage) so that stacks evaluated at different
 * moments but with the same expiry remain identical and keep stacking.
 */
public class PdcFoodStateCodec implements FoodStateCodec {
    private static final String FRESHNESS_LINE_PREFIX = "Frescura:";
    private static final String PRESERVATION_LINE_PREFIX = "Conserva:";

    private final NamespacedKey expiresKey;
    private final NamespacedKey preservationKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

    public PdcFoodStateCodec(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.expiresKey = new NamespacedKey(plugin, "expires_at");
        this.preservationKey = new NamespacedKey(plugin, "preservation");
    }

    @Override
    public Optional<FoodState> read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Long expiresAt = pdc.get(expiresKey, PersistentDataType.LONG);
        if (expiresAt == null) {
            return Optional.empty();
        }
        String preservation = pdc.get(preservationKey, PersistentDataType.STRING);
        return Optional.of(new FoodState(expiresAt, Preservation.fromIdentifier(preservation)));
    }

    @Override
    public void write(ItemStack item, FoodState state, FreshnessStage stage) {
        if (item == null || state == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(expiresKey, PersistentDataType.LONG, state.expiresAt());
        if (state.preservation() == Preservation.NONE) {
            pdc.remove(preservationKey);
        } else {
            pdc.set(preservationKey, PersistentDataType.STRING, state.preservation().getIdentifier());
        }

        List<Component> lore = new ArrayList<>();
        List<Component> existing = meta.lore();
        if (existing != null) {
            for (Component line : existing) {
                String text = plain.serialize(line);
                if (!text.startsWith(FRESHNESS_LINE_PREFIX) && !text.startsWith(PRESERVATION_LINE_PREFIX)) {
                    lore.add(line);
                }
            }
        }
        FreshnessStage shown = stage != null ? stage : FreshnessStage.FRESH;
        lore.add(line("<gray>" + FRESHNESS_LINE_PREFIX + "</gray> " + shown.getColorTag() + shown.getDisplayName()));
        if (state.preservation() != Preservation.NONE) {
            lore.add(line("<gray>" + PRESERVATION_LINE_PREFIX + "</gray> <gold>" + state.preservation().getDisplayName()));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private Component line(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText).decoration(TextDecoration.ITALIC, false);
    }
}
