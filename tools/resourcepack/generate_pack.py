#!/usr/bin/env python3
# Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
# Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates the Sovereign Seasons resource pack sources: textures and JSON files (standard library only).

All art is original and procedural: no Minecraft asset is copied. The output is committed in
sovereign-seasons-resourcepack/src/pack, so run this only after changing the art or the crate table.

- Seasonal colormaps (256x256): how the game reads them was checked in the game code
  (ColorMapColorUtil): downfall *= temperature; x = (1 - temperature) * 255; y = (1 - downfall) * 255.
  The base is a three-corner blend that approximates the vanilla look; each season then shifts it.
  Summer has no seasonal pack (vanilla colours).
- Crate textures (16x16) for Logistics crates, one top per packed crop.
- Season icons (8x8 each) for the custom font.

Usage: python tools/resourcepack/generate_pack.py sovereign-seasons-resourcepack/src/pack [--preview <png>]
"""

import colorsys
import os
import struct
import sys
import zlib


# ---------------------------------------------------------------- PNG writer
def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a) tuples."""
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type: none
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as handle:
        handle.write(png)


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def clamp01(x):
    return max(0.0, min(1.0, x))


# ---------------------------------------------------------------- colormaps
# Three-corner blend (cold, hot-dry, hot-wet). The corners are the commonly documented vanilla corner colours,
# used only as a starting point for a new procedural map.
GRASS = {"cold": hex_rgb("80B497"), "dry": hex_rgb("BFB755"), "wet": hex_rgb("47CD33")}
FOLIAGE = {"cold": hex_rgb("60A17B"), "dry": hex_rgb("AEA42A"), "wet": hex_rgb("1ABF00")}
DRY_FOLIAGE = {"cold": hex_rgb("7B6F4E"), "dry": hex_rgb("A3895A"), "wet": hex_rgb("8C7038")}


def base_color(corners, temperature, downfall):
    wet = tuple(downfall * w + (1 - downfall) * d for w, d in zip(corners["wet"], corners["dry"]))
    return tuple((1 - temperature) * c + temperature * h for c, h in zip(corners["cold"], wet))


def shift(rgb, hue_to=None, hue_weight=0.0, sat=1.0, light=0.0):
    r, g, b = (c / 255.0 for c in rgb)
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    if hue_to is not None and hue_weight > 0:
        target = (hue_to % 360) / 360.0
        delta = ((target - h + 0.5) % 1.0) - 0.5
        h = (h + delta * hue_weight) % 1.0
    s = clamp01(s * sat)
    l = clamp01(l + light)
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return tuple(int(round(c * 255)) for c in (r, g, b))


def temperate_weight(temperature):
    """Hot biomes (deserts, savannas, jungles) change little; temperate and cold ones change the most."""
    return 1.0 - clamp01((temperature - 0.8) / 0.2)


SEASONS = {
    # Fresh, slightly brighter greens
    "spring": {
        "grass": lambda c, t, d: shift(c, 105, 0.25, 1.12, 0.03),
        "foliage": lambda c, t, d: shift(c, 100, 0.25, 1.10, 0.04),
        "dry_foliage": lambda c, t, d: shift(c, 60, 0.3, 1.0, 0.02),
    },
    # Golden grass; yellow, orange and red leaves depending on how cold the biome is
    "autumn": {
        "grass": lambda c, t, d: shift(c, 52, 0.55 * temperate_weight(t), 0.95, 0.0),
        "foliage": lambda c, t, d: shift(c, 18 + 30 * (1 - t), 0.85 * temperate_weight(t), 1.15, 0.02),
        "dry_foliage": lambda c, t, d: shift(c, 25, 0.6 * temperate_weight(t), 1.2, 0.0),
    },
    # Dormant: dull, desaturated olive and brown
    "winter": {
        "grass": lambda c, t, d: shift(c, 48, 0.5 * temperate_weight(t), 0.55, -0.04),
        "foliage": lambda c, t, d: shift(c, 40, 0.55 * temperate_weight(t), 0.5, -0.06),
        "dry_foliage": lambda c, t, d: shift(c, 30, 0.4, 0.6, -0.05),
    },
}


def colormap(corners, transform):
    rows = []
    for y in range(256):
        row = []
        for x in range(256):
            temperature = 1.0 - x / 255.0
            humidity = 1.0 - y / 255.0          # = downfall * temperature in the game lookup
            downfall = clamp01(humidity / temperature) if temperature > 0 else 0.0
            rgb = base_color(corners, temperature, downfall)
            rgb = transform(rgb, temperature, downfall)
            row.append((*(max(0, min(255, int(round(c)))) for c in rgb), 255))
        rows.append(row)
    return rows


def lookup(rows, temperature, downfall):
    """Same index math as the game (ColorMapColorUtil.get)."""
    temperature = clamp01(temperature)
    downfall = clamp01(downfall) * temperature
    x = int((1.0 - temperature) * 255.0)
    y = int((1.0 - downfall) * 255.0)
    return rows[y][x][:3]


# ---------------------------------------------------------------- crates
WOOD_DARK = hex_rgb("4F3418")
WOOD_MID = hex_rgb("8A6234")
WOOD_LIGHT = hex_rgb("A8794A")
WOOD_SEAM = hex_rgb("6B4A26")

# Packable crops of Logistics and the colours shown on their crate lids
CROPS = {
    "carrot": ["E8862A", "F2A23C", "3E8E2E"],
    "potato": ["C9A15A", "DDB873", "9C7A3C"],
    "beetroot": ["8E1F2A", "B02F3A", "4E7A2A"],
    "apple": ["C8232C", "E2453A", "5B8F2A"],
    "sweet_berries": ["A3172A", "C82A3A", "2F6B2A"],
    "glow_berries": ["F2B33A", "FFD45C", "3F7A2A"],
    "melon_slice": ["E0463E", "F06A55", "4E9A3A"],
    "cocoa_beans": ["7A4A24", "94602F", "5A3418"],
    "sugar_cane": ["8FC456", "A8D872", "5E9636"],
}


def crate_side():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = WOOD_DARK
            elif y in (5, 10):
                c = WOOD_SEAM
            elif x == y or x == 15 - y:
                c = WOOD_SEAM  # cross brace
            else:
                c = WOOD_LIGHT if (x + y * 3) % 7 == 0 else WOOD_MID
            row.append((*c, 255))
        rows.append(row)
    return rows


def crate_bottom():
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c = WOOD_DARK
            elif x % 5 == 0:
                c = WOOD_SEAM
            else:
                c = WOOD_MID
            row.append((*c, 255))
        rows.append(row)
    return rows


def crate_top(colors):
    produce = [hex_rgb(c) for c in colors] if colors else None
    rows = crate_bottom()
    if produce is None:
        return rows
    main, highlight, accent = produce
    for y in range(2, 14):
        for x in range(2, 14):
            # Round produce in a 3x3 grid of 4x4 cells, with a highlight and a leaf/accent pixel
            cx, cy = (x - 2) % 4, (y - 2) % 4
            if (cx, cy) in ((0, 0), (3, 0), (0, 3), (3, 3)):
                continue
            c = main
            if (cx, cy) == (1, 1):
                c = highlight
            if (cx, cy) == (2, 0):
                c = accent
            rows[y][x] = (*c, 255)
    return rows


# ---------------------------------------------------------------- season icons
ICONS = {
    "spring": ["..p..p..", ".pPpPPp.", "..pYYp..", ".pPYYPp.", "..pppp..", "...gg...", "..g.g...", "...g...."],
    "summer": ["y..y..y.", ".y.yy.y.", "..YYYY..", "yyYOOYyy", "yyYOOYyy", "..YYYY..", ".y.yy.y.", "y..y..y."],
    "autumn": [".....OO.", "....OOR.", "...ORRO.", "..ORROO.", ".ORROO..", ".ROOO...", "b.......", ".b......"],
    "winter": ["...w....", ".w.W.w..", "..wWw...", "wWWWWWw.", "..wWw...", ".w.W.w..", "...w....", "........"],
}
ICON_COLORS = {
    "p": "F28DC4", "P": "E05EA8", "Y": "FFD84A", "g": "4E9A3A", "y": "FFC93A", "O": "F28C1E",
    "R": "C8402A", "b": "6B4A26", "w": "BFE8FF", "W": "FFFFFF",
}


def icon_strip():
    rows = [[(0, 0, 0, 0)] * 32 for _ in range(8)]
    for i, season in enumerate(("spring", "summer", "autumn", "winter")):
        for y, line in enumerate(ICONS[season]):
            for x, ch in enumerate(line):
                if ch != ".":
                    rows[y][i * 8 + x] = (*hex_rgb(ICON_COLORS[ch]), 255)
    return rows


def pack_icon():
    """64x64 pack.png: the four season icons, 4x scaled, on a dark tile."""
    strip = icon_strip()
    rows = [[(32, 34, 38, 255)] * 64 for _ in range(64)]
    for i in range(4):
        ox, oy = (i % 2) * 32, (i // 2) * 32
        for y in range(8):
            for x in range(8):
                px = strip[y][i * 8 + x]
                if px[3]:
                    for dy in range(3):
                        for dx in range(3):
                            rows[oy + 4 + y * 3 + dy][ox + 4 + x * 3 + dx] = px
    return rows


# ---------------------------------------------------------------- JSON files
# Formats checked with the game's own pack metadata reader (1.21.11, 26.1.2 and 26.3): resource formats up to 64
# use pack_format/supported_formats, newer ones min_format/max_format. 22 = 1.20.3-1.20.4, 97 = 26.3.
PACK_FORMAT_MIN = 22
PACK_FORMAT_MAX = 97
LAST_OLD_FORMAT = 64

# custom_model_data of Logistics crates (must match CrateModels in sovereign-seasons-logistics)
CRATE_MODEL_BASE = 7301000
CRATE_MODELS = ["crate"] + [f"crate_{crop}" for crop in CROPS]   # 7301000 generic, then one per crop
CRATE_MODEL_END = CRATE_MODEL_BASE + len(CRATE_MODELS)            # values from here on fall back to the barrel


def write_json(path, data, ascii_only=False):
    import json
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=ascii_only)
        handle.write("\n")


def pack_mcmeta(description):
    return {"pack": {
        "description": description,
        "pack_format": PACK_FORMAT_MIN,
        "supported_formats": [PACK_FORMAT_MIN, LAST_OLD_FORMAT],
        "min_format": PACK_FORMAT_MIN,
        "max_format": PACK_FORMAT_MAX,
    }}


def write_json_files(root):
    base = os.path.join(root, "base")
    write_json(os.path.join(base, "pack.mcmeta"), pack_mcmeta("Sovereign Seasons: cajas e iconos de estación"))
    descriptions = {
        "spring": "Sovereign Seasons: hierba y hojas de primavera",
        "autumn": "Sovereign Seasons: hierba y hojas de otoño",
        "winter": "Sovereign Seasons: hierba y hojas de invierno",
    }
    for season, text in descriptions.items():
        write_json(os.path.join(root, season, "pack.mcmeta"), pack_mcmeta(text))

    models = os.path.join(base, "assets", "sovereignseasons", "models", "item")
    for name in CRATE_MODELS:
        top = "crate_top" if name == "crate" else f"crate_top_{name[len('crate_'):]}"
        write_json(os.path.join(models, f"{name}.json"), {
            "parent": "minecraft:block/cube_bottom_top",
            "textures": {
                "top": f"sovereignseasons:item/{top}",
                "side": "sovereignseasons:item/crate_side",
                "bottom": "sovereignseasons:item/crate_bottom",
            },
        })

    # 1.21.4+: item model definition, picked by the first custom_model_data float
    entries = [{"threshold": CRATE_MODEL_BASE + i, "model": {"type": "minecraft:model", "model": f"sovereignseasons:item/{name}"}}
               for i, name in enumerate(CRATE_MODELS)]
    entries.append({"threshold": CRATE_MODEL_END, "model": {"type": "minecraft:model", "model": "minecraft:block/barrel"}})
    write_json(os.path.join(base, "assets", "minecraft", "items", "barrel.json"), {"model": {
        "type": "minecraft:range_dispatch",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "entries": entries,
        "fallback": {"type": "minecraft:model", "model": "minecraft:block/barrel"},
    }})

    # Up to 1.21.3: item model overrides (the last matching override wins, so they go in ascending order)
    overrides = [{"predicate": {"custom_model_data": CRATE_MODEL_BASE + i}, "model": f"sovereignseasons:item/{name}"}
                 for i, name in enumerate(CRATE_MODELS)]
    overrides.append({"predicate": {"custom_model_data": CRATE_MODEL_END}, "model": "minecraft:block/barrel"})
    write_json(os.path.join(base, "assets", "minecraft", "models", "item", "barrel.json"),
               {"parent": "minecraft:block/barrel", "overrides": overrides})

    # Season icons: private-use characters U+E000 (spring) to U+E003 (winter)
    write_json(os.path.join(base, "assets", "sovereignseasons", "font", "icons.json"), {"providers": [{
        "type": "bitmap",
        "file": "sovereignseasons:font/seasons.png",
        "ascent": 7,
        "height": 8,
        "chars": [""],
    }]}, ascii_only=True)


# ---------------------------------------------------------------- main
def main():
    root = sys.argv[1]
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None

    base = os.path.join(root, "base", "assets", "sovereignseasons", "textures")
    write_png(os.path.join(base, "item", "crate_side.png"), 16, 16, crate_side())
    write_png(os.path.join(base, "item", "crate_bottom.png"), 16, 16, crate_bottom())
    write_png(os.path.join(base, "item", "crate_top.png"), 16, 16, crate_top(None))
    for crop, colors in CROPS.items():
        write_png(os.path.join(base, "item", f"crate_top_{crop}.png"), 16, 16, crate_top(colors))
    write_png(os.path.join(base, "font", "seasons.png"), 32, 8, icon_strip())
    write_png(os.path.join(root, "base", "pack.png"), 64, 64, pack_icon())

    maps = {}
    for season, transforms in SEASONS.items():
        folder = os.path.join(root, season, "assets", "minecraft", "textures", "colormap")
        for name, corners in (("grass", GRASS), ("foliage", FOLIAGE), ("dry_foliage", DRY_FOLIAGE)):
            rows = colormap(corners, transforms[name])
            maps[(season, name)] = rows
            write_png(os.path.join(folder, f"{name}.png"), 256, 256, rows)
        write_png(os.path.join(root, season, "pack.png"), 64, 64, pack_icon())

    write_json_files(root)
    if preview:
        write_preview(preview, maps)


# Biome climate (temperature, downfall) from the 1.21.11 biome data files
PREVIEW_BIOMES = [
    ("plains", 0.8, 0.4), ("forest", 0.7, 0.8), ("dark_forest", 0.7, 0.8), ("taiga", 0.25, 0.8),
    ("meadow", 0.5, 0.8), ("jungle", 0.95, 0.9), ("savanna", 2.0, 0.0), ("snowy_plains", 0.0, 0.5),
]


def write_preview(path, maps):
    """Swatches per biome: rows = biomes, columns = summer (base) / spring / autumn / winter; grass | foliage."""
    cell = 24
    width, height = cell * 2 * 4 + 3 * 4, cell * len(PREVIEW_BIOMES)
    rows = [[(255, 255, 255, 255)] * width for _ in range(height)]
    base_maps = {name: colormap(c, lambda rgb, t, d: rgb) for name, c in (("grass", GRASS), ("foliage", FOLIAGE))}
    columns = [("summer", base_maps["grass"], base_maps["foliage"])]
    columns += [(s, maps[(s, "grass")], maps[(s, "foliage")]) for s in ("spring", "autumn", "winter")]
    for bi, (biome, t, d) in enumerate(PREVIEW_BIOMES):
        for ci, (_, grass, foliage) in enumerate(columns):
            for k, cmap in enumerate((grass, foliage)):
                color = lookup(cmap, t, d)
                x0 = ci * (cell * 2 + 4) + k * cell
                for y in range(bi * cell, bi * cell + cell - 2):
                    for x in range(x0, x0 + cell - 2):
                        rows[y][x] = (*color, 255)
    write_png(path, width, height, rows)


if __name__ == "__main__":
    main()
