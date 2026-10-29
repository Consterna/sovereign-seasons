/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// End-to-end checks for Sovereign Seasons Logistics: snow, mud, roads, mounts, blizzards,
// frozen lakes, carried weight and bulk crates (with the Pantry freshness link).
//
// Usage: node e2e-logistics.js [path/to/server.properties]

const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const { Rcon } = require('./rcon');

const propsPath = process.argv[2] || path.join(__dirname, '..', '..', 'testserver', 'server.properties');
const serverDir = path.dirname(propsPath);
const props = Object.fromEntries(
  fs.readFileSync(propsPath, 'utf8').split(/\r?\n/).filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)])
);
const HOST = props['server-ip'] || '127.0.0.1';
const PORT = Number(props['server-port'] || 25565);
const BOT = 'e2ebot';
const LOGISTICS_CONFIG = path.join(serverDir, 'plugins', 'SovereignSeasons-Logistics', 'config.yml');

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

async function main() {
  const rcon = await new Rcon(HOST, Number(props['rcon.port']), props['rcon.password']).connect();
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: BOT, auth: 'offline', version: process.env.MC_VERSION || '1.21.11' });
  const chat = [];
  bot.on('messagestr', (msg) => chat.push(msg));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    setTimeout(() => reject(new Error('bot spawn timeout')), 60000);
  });
  await sleep(1500);

  const cmd = (c) => rcon.command(c);
  async function playerCommand(c, waitMs = 1500) {
    const start = chat.length;
    bot.chat(c);
    await sleep(waitMs);
    return chat.slice(start).join('\n');
  }
  /**
   * Slowness/Speed level (0 = none) of an entity, read server-side through exact NBT paths
   * (the full effect list can be truncated in command output; an amplifier of 0 is omitted from NBT).
   */
  async function effectLevel(selector, effect) {
    const path = `active_effects[{id:"minecraft:${effect}"}]`;
    const id = await cmd(`data get entity ${selector} ${path}.id`);
    if (!id.includes(`minecraft:${effect}`)) return 0;
    const amp = await cmd(`data get entity ${selector} ${path}.amplifier`);
    const m = amp.match(/entity data: (\d+)b/);
    return m ? Number(m[1]) + 1 : 1;
  }
  async function waitForBlock(pos) {
    for (let i = 0; i < 60; i++) {
      const block = bot.blockAt(pos);
      if (block) return block;
      await sleep(500);
    }
    throw new Error(`block at ${pos} never loaded`);
  }
  async function teleport(pos) {
    await cmd(`tp ${BOT} ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5}`);
    await sleep(500);
    await waitForBlock(pos.offset(0, -1, 0));
    await cmd('kill @e[type=slime]');
    await sleep(1000);
  }
  async function holdItem(name) {
    await sleep(500);
    const item = bot.inventory.items().find((i) => i.name === name);
    if (!item) return false;
    await bot.equip(item, 'hand');
    await sleep(300);
    return true;
  }
  const count = (name) => bot.inventory.items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);
  const stacks = (name) => bot.inventory.items().filter((i) => i.name === name).length;
  /** Rewrites one value of the logistics config (test server only) and reloads it. */
  async function setConfig(pattern, replacement) {
    const text = fs.readFileSync(LOGISTICS_CONFIG, 'utf8').replace(/\r\n/g, '\n');
    if (!pattern.test(text)) throw new Error(`pattern ${pattern} not found in logistics config`);
    fs.writeFileSync(LOGISTICS_CONFIG, text.replace(pattern, replacement), 'utf8');
    await cmd('logistics reload');
  }
  async function fillBiome(center, radius, biome) {
    for (let x = center.x - radius; x <= center.x + radius; x += 33) {
      for (let z = center.z - radius; z <= center.z + radius; z += 33) {
        await cmd(`fillbiome ${x} -64 ${z} ${Math.min(x + 32, center.x + radius)} -50 ${Math.min(z + 32, center.z + radius)} ${biome}`);
      }
    }
  }

  try {
    await cmd(`op ${BOT}`);
    await cmd(`gamemode survival ${BOT}`);
    await cmd('gamerule advance_time false');
    await cmd('gamerule spawn_mobs false');
    await cmd('kill @e[type=!player]');
    await cmd('time set 6000');
    await cmd('weather clear');
    await cmd(`clear ${BOT}`);
    await cmd(`effect clear ${BOT}`);

    const plugins = await cmd('plugins');
    check('Logistics plugin enabled', /SovereignSeasons-Logistics/.test(plugins), plugins.replace(/\s+/g, ' '));

    const spawn = bot.entity.position.floored();
    const base = spawn.offset(200, 0, 0);
    await teleport(base);
    const y = base.y;

    // ---------------------------------------------------------------- snow
    await cmd('season set winter world');
    await cmd(`fill ${base.x - 2} ${y} ${base.z - 2} ${base.x + 2} ${y} ${base.z + 2} minecraft:snow[layers=1]`);
    await sleep(2500);
    check('A thin snow layer slows a player (Slowness I)', await effectLevel(BOT, 'slowness') === 1, `level=${await effectLevel(BOT, 'slowness')}`);
    await cmd(`fill ${base.x - 2} ${y} ${base.z - 2} ${base.x + 2} ${y} ${base.z + 2} minecraft:snow[layers=4]`);
    await sleep(2500);
    check('Deep snow slows more (Slowness II)', await effectLevel(BOT, 'slowness') === 2, `level=${await effectLevel(BOT, 'slowness')}`);
    await cmd(`item replace entity ${BOT} armor.feet with minecraft:leather_boots`);
    await sleep(2500);
    check('Leather boots let the player walk on snow', await effectLevel(BOT, 'slowness') === 0);
    await cmd(`item replace entity ${BOT} armor.feet with minecraft:air`);
    await cmd(`fill ${base.x - 2} ${y} ${base.z - 2} ${base.x + 2} ${y + 1} ${base.z + 2} minecraft:air`);
    await teleport(base);

    // ---------------------------------------------------------------- mud
    await cmd('season set autumn world');
    await cmd('weather rain');
    await sleep(2500);
    check('Rain in autumn turns grass into slowing mud', await effectLevel(BOT, 'slowness') === 1, `level=${await effectLevel(BOT, 'slowness')}`);
    await cmd(`fill ${base.x - 2} ${y - 1} ${base.z - 2} ${base.x + 2} ${y - 1} ${base.z + 2} minecraft:dirt_path`);
    await sleep(2500);
    check('A dirt path keeps the player out of the mud', await effectLevel(BOT, 'slowness') === 0);
    await cmd('weather clear');

    // ---------------------------------------------------------------- mounts and roads
    await cmd('season set summer world');
    const ride = base.offset(10, 0, 0);
    await teleport(ride);
    await cmd(`fill ${ride.x - 2} ${y - 1} ${ride.z - 2} ${ride.x + 2} ${y - 1} ${ride.z + 2} minecraft:gravel`);
    await cmd(`summon horse ${ride.x + 0.5} ${y} ${ride.z + 0.5} {Tame:1b,NoAI:1b}`);
    await sleep(1000);
    await cmd(`ride ${BOT} mount @e[type=horse,limit=1,sort=nearest,x=${ride.x},y=${y},z=${ride.z}]`);
    await sleep(2500);
    const horse = `@e[type=horse,limit=1,sort=nearest,x=${ride.x},y=${y},z=${ride.z}]`;
    check('A horse on a gravel road gets Speed', await effectLevel(horse, 'speed') === 1, `speed=${await effectLevel(horse, 'speed')}`);
    await cmd(`fill ${ride.x - 2} ${y} ${ride.z - 2} ${ride.x + 2} ${y} ${ride.z + 2} minecraft:snow[layers=4] replace minecraft:air`);
    await sleep(2500);
    check('A horse in deep snow is slowed', await effectLevel(horse, 'slowness') === 2, `slowness=${await effectLevel(horse, 'slowness')}`);
    await cmd(`item replace entity ${horse} armor.body with minecraft:leather_horse_armor`);
    await sleep(2500);
    check('Leather horse armor lets a horse cross snow', await effectLevel(horse, 'slowness') === 0, `slowness=${await effectLevel(horse, 'slowness')}`);
    await cmd(`ride ${BOT} dismount`);
    await cmd('kill @e[type=horse]');
    await cmd(`fill ${ride.x - 2} ${y} ${ride.z - 2} ${ride.x + 2} ${y + 1} ${ride.z + 2} minecraft:air`);

    // ---------------------------------------------------------------- blizzard (taiga: freezing for us, never frozen by vanilla)
    const cold = base.offset(60, 0, 0);
    await teleport(cold);
    await fillBiome(cold, 20, 'minecraft:taiga');
    await cmd('season set winter world');
    // Leather keeps the core's cold comfort effect away: any Slowness left comes from the blizzard
    await cmd(`item replace entity ${BOT} armor.feet with minecraft:leather_boots`);
    await cmd(`item replace entity ${BOT} armor.head with minecraft:leather_helmet`);
    await cmd('weather rain');
    await sleep(3000);
    const blizzardInfo = await playerCommand('/logistics status');
    check('A winter storm in the open is a blizzard (Slowness despite leather)', await effectLevel(BOT, 'slowness') === 1 && /Ventisca/.test(blizzardInfo),
      blizzardInfo.split('\n').slice(1, 3).join(' | '));
    await cmd(`fill ${cold.x - 1} ${y + 3} ${cold.z - 1} ${cold.x + 1} ${y + 3} ${cold.z + 1} minecraft:stone`);
    await sleep(3000);
    check('Shelter cancels the blizzard', await effectLevel(BOT, 'slowness') === 0);
    await cmd(`fill ${cold.x - 1} ${y + 3} ${cold.z - 1} ${cold.x + 1} ${y + 3} ${cold.z + 1} minecraft:air`);
    await cmd('weather clear');

    // ---------------------------------------------------------------- frozen lakes
    // Small sampling radius so the pond is hit quickly (test server only)
    await setConfig(/(\n\s*radius: )\d+/, '$18');
    await setConfig(/(samples-per-player: )\d+/, '$116');
    const pond = cold.offset(4, -1, 0);
    await cmd(`fill ${pond.x} ${pond.y} ${pond.z - 2} ${pond.x + 3} ${pond.y} ${pond.z + 2} minecraft:water`);
    const manualIce = pond.offset(-2, 0, 3);
    await cmd(`setblock ${manualIce.x} ${manualIce.y} ${manualIce.z} minecraft:ice`);
    await cmd('tick sprint 2000');
    await sleep(8000);
    const pondBlocks = () => {
      let ice = 0;
      let water = 0;
      for (let dx = 0; dx <= 3; dx++) {
        for (let dz = -2; dz <= 2; dz++) {
          const b = bot.blockAt(new Vec3(pond.x + dx, pond.y, pond.z + dz));
          if (b && b.name === 'ice') ice++;
          if (b && b.name === 'water') water++;
        }
      }
      return { ice, water };
    };
    let state = pondBlocks();
    check('A pond freezes over in a freezing winter', state.ice >= 15, JSON.stringify(state));
    await cmd('season set spring world');
    await cmd('tick sprint 2000');
    await sleep(8000);
    state = pondBlocks();
    const manual = bot.blockAt(manualIce);
    check('Seasonal ice melts back into water in spring', state.ice === 0 && state.water === 20, JSON.stringify(state));
    check('Ice placed by hand is never melted', manual && manual.name === 'ice', manual ? manual.name : 'unloaded');
    await setConfig(/(\n\s*radius: )\d+/, '$148');
    await setConfig(/(samples-per-player: )\d+/, '$18');
    await cmd(`item replace entity ${BOT} armor.feet with minecraft:air`);
    await cmd(`item replace entity ${BOT} armor.head with minecraft:air`);

    // ---------------------------------------------------------------- carried weight
    await cmd('season set summer world');
    await teleport(base.offset(-20, 0, 0));
    await setConfig(/(weight:\s*\n(?:\s*#[^\n]*\n)*\s*enabled: )\w+/, '$1true');
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:cobblestone 960`);
    await sleep(2500);
    check('Carrying 15 stacks of cobblestone overloads the player (Slowness II)', await effectLevel(BOT, 'slowness') === 2,
      `level=${await effectLevel(BOT, 'slowness')}`);
    const weightInfo = await playerCommand('/logistics weight');
    check('/logistics weight shows the load', /Carga: 960\/1000/.test(weightInfo), weightInfo.split('\n')[0]);
    await cmd(`clear ${BOT}`);
    await sleep(2500);
    check('Unloading (e.g. onto a mule) removes the weight penalty', await effectLevel(BOT, 'slowness') === 0);
    await setConfig(/(weight:\s*\n(?:\s*#[^\n]*\n)*\s*enabled: )\w+/, '$1false');

    // ---------------------------------------------------------------- crates
    await cmd(`give ${BOT} minecraft:carrot 18`);
    await holdItem('carrot');
    // Packing happens when right-clicking the air (right-clicking a block must still plant crops)
    await bot.look(bot.entity.yaw, 0, true);
    bot.setControlState('sneak', true);
    await sleep(500);
    bot.activateItem();
    await sleep(800);
    bot.activateItem();
    await sleep(800);
    bot.setControlState('sneak', false);
    await sleep(800);
    check('Sneak + right-click packs 9 carrots per crate', count('barrel') === 2 && count('carrot') === 0, `crates=${count('barrel')} carrots=${count('carrot')}`);
    check('Crates packed from the same stack stack together', stacks('barrel') === 1, `stacks=${stacks('barrel')}`);
    await holdItem('barrel');
    bot.activateItem();
    await sleep(1000);
    check('Right-click unpacks a crate', count('barrel') === 1 && count('carrot') === 9, `crates=${count('barrel')} carrots=${count('carrot')}`);
    await holdItem('barrel');
    const ground = await waitForBlock(bot.entity.position.floored().offset(1, -1, 0));
    await bot.lookAt(ground.position.offset(0.5, 1, 0.5));
    try {
      await bot.activateBlock(ground);
    } catch (e) {
      // activateBlock may time out waiting for a block update that never comes: that is the expected outcome
    }
    await sleep(1000);
    const above = bot.blockAt(ground.position.offset(0, 1, 0));
    check('A crate is never placed as a barrel block', above && above.name !== 'barrel', above ? above.name : 'unloaded');

    // ---------------------------------------------------------------- crates keep Pantry freshness
    const hasPantry = /SovereignSeasons-Pantry/.test(plugins);
    if (hasPantry) {
      await cmd(`clear ${BOT}`);
      await cmd(`give ${BOT} minecraft:carrot 9`);
      await holdItem('carrot');
      await playerCommand('/pantry setfresh 30');
      await bot.look(bot.entity.yaw, 0, true);
      bot.setControlState('sneak', true);
      await sleep(500);
      bot.activateItem();
      await sleep(800);
      bot.setControlState('sneak', false);
      const packed = count('barrel') === 1 && count('carrot') === 0;
      await holdItem('barrel');
      bot.activateItem();
      await sleep(1000);
      await holdItem('carrot');
      const inspect = await playerCommand('/pantry inspect');
      const pct = Number((inspect.match(/Frescura: ([\d.]+)%/) || [0, -1])[1]);
      // Only meaningful if the carrots really went through a crate
      check('Packed carrots keep their freshness (~30%)', packed && count('barrel') === 0 && pct >= 28 && pct <= 32,
        `packed=${packed} freshness=${pct}%`);
    }
  } catch (e) {
    check('Unexpected exception during scenario', false, e.stack);
  } finally {
    bot.quit();
    rcon.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
  process.exit(failed.length === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(2);
});
