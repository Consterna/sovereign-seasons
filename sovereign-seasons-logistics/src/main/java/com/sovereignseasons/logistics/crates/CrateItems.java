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

package com.sovereignseasons.logistics.crates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;

/**
 * Builds and reads crate items: a barrel item carrying its contents in the PersistentDataContainer.
 */
public class CrateItems {
    /** Base item of every crate (a resource pack can give it its own model later). */
    public static final Material CRATE_MATERIAL = Material.BARREL;

    private static final Map<Material, String> NAMES = Map.of(
            Material.CARROT, "Zanahorias",
            Material.POTATO, "Patatas",
            Material.BEETROOT, "Remolachas",
            Material.APPLE, "Manzanas",
            Material.SWEET_BERRIES, "Bayas dulces",
            Material.GLOW_BERRIES, "Bayas luminosas",
            Material.MELON_SLICE, "Rodajas de melón",
            Material.COCOA_BEANS, "Granos de cacao",
            Material.SUGAR_CANE, "Caña de azúcar"
    );

    /**
     * Contents of a crate.
     *
     * @param content     packed material
     * @param count       items per crate
     * @param packedValue Pantry freshness value (see {@link CrateMath}), empty when Pantry was not involved
     */
    public record Contents(Material content, int count, OptionalLong packedValue) {}

    private final NamespacedKey contentKey;
    private final NamespacedKey countKey;
    private final NamespacedKey valueKey;
    private final BooleanSupplier modelData;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public CrateItems(Plugin plugin) {
        this(plugin, () -> false);
    }

    /**
     * @param modelData whether crates get the custom model data the resource pack draws them with ({@link CrateModels})
     */
    public CrateItems(Plugin plugin, BooleanSupplier modelData) {
        Objects.requireNonNull(plugin, "plugin cannot be null");
        this.modelData = Objects.requireNonNull(modelData, "modelData cannot be null");
        this.contentKey = new NamespacedKey(plugin, "crate_content");
        this.countKey = new NamespacedKey(plugin, "crate_count");
        this.valueKey = new NamespacedKey(plugin, "crate_value");
    }

    public static String displayName(Material material) {
        String name = NAMES.get(material);
        if (name != null) {
            return name;
        }
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    public ItemStack create(Material content, int count, OptionalLong packedValue, int amount) {
        ItemStack crate = new ItemStack(CRATE_MATERIAL, Math.max(1, amount));
        ItemMeta meta = crate.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(contentKey, PersistentDataType.STRING, content.name());
        pdc.set(countKey, PersistentDataType.INTEGER, count);
        if (packedValue.isPresent()) {
            pdc.set(valueKey, PersistentDataType.LONG, packedValue.getAsLong());
        }
        meta.displayName(line("<gold>Caja de " + displayName(content) + "</gold>"));
        List<Component> lore = new ArrayList<>();
        lore.add(line("<gray>Contiene:</gray> <white>" + count + " " + displayName(content) + "</white>"));
        if (packedValue.isPresent()) {
            lore.add(line("<gray>Conserva la comida más tiempo</gray>"));
        }
        lore.add(line("<dark_gray>Clic derecho para desempaquetar</dark_gray>"));
        meta.lore(lore);
        if (modelData.getAsBoolean()) {
            meta.setCustomModelData(CrateModels.of(content));
        }
        crate.setItemMeta(meta);
        return crate;
    }

    /**
     * Contents of a crate item, or empty if the item is not a crate.
     */
    public Optional<Contents> read(ItemStack item) {
        if (item == null || item.getType() != CRATE_MATERIAL || !item.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String content = pdc.get(contentKey, PersistentDataType.STRING);
        Integer count = pdc.get(countKey, PersistentDataType.INTEGER);
        if (content == null || count == null || count <= 0) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(content);
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }
        Long value = pdc.get(valueKey, PersistentDataType.LONG);
        return Optional.of(new Contents(material, count, value != null ? OptionalLong.of(value) : OptionalLong.empty()));
    }

    public boolean isCrate(ItemStack item) {
        return read(item).isPresent();
    }

    private Component line(String miniMessageText) {
        return miniMessage.deserialize(miniMessageText).decoration(TextDecoration.ITALIC, false);
    }
}
