/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// End-to-end gameplay checks: a Mineflayer bot joins a local offline-mode Paper server
// while the console is driven over RCON. Exit code 0 only if every check passes.
//
// Usage: node e2e.js [path/to/server.properties]

const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const { Rcon } = require('./rcon');

const propsPath = process.argv[2] || path.join(__dirname, '..', '..', 'testserver', 'server.properties');
const props = Object.fromEntries(
  fs.readFileSync(propsPath, 'utf8')
    .split(/\r?\n/)
    .filter((l) => l && !l.startsWith('#') && l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)])
);
const HOST = props['server-ip'] || '127.0.0.1';
const PORT = Number(props['server-port'] || 25565);
const BOT = 'e2ebot';

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

async function main() {
  const rcon = await new Rcon(HOST, Number(props['rcon.port']), props['rcon.password']).connect();
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: BOT, auth: 'offline', version: '1.21.11' });
  const chat = [];
  const actionBars = [];
  const titles = [];
  const bossBars = [];
  // Flattens the text of an NBT/JSON chat component (gradients split text into one component per letter)
  const flatText = (component) => {
    const json = typeof component === 'string' ? component : JSON.stringify(component);
    const parts = [];
    const re = /"text":(?:\{"type":"string","value":)?"((?:[^"\\]|\\.)*)"/g;
    let m;
    while ((m = re.exec(json)) !== null) parts.push(m[1]);
    return parts.join('');
  };
  bot.on('messagestr', (msg) => chat.push(msg));
  // Mineflayer does not emit 'actionBar'/'title' reliably on 1.21.11, so read the raw packets
  bot._client.on('action_bar', (packet) => actionBars.push(flatText(packet.text)));
  bot._client.on('set_title_text', (packet) => titles.push(flatText(packet.text)));
  bot._client.on('set_title_subtitle', (packet) => titles.push(flatText(packet.text)));
  bot.on('bossBarCreated', (bar) => bossBars.push(bar.title.toString()));
  bot.on('bossBarUpdated', (bar) => bossBars.push(bar.title.toString()));
  bot.on('kicked', (reason) => console.log('KICKED', JSON.stringify(reason)));
  bot.on('error', (err) => console.log('BOT ERROR', err.message));
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    setTimeout(() => reject(new Error('bot spawn timeout')), 60000);
  });
  await sleep(1500);

  // Run a player command and collect the chat lines it produced.
  async function playerCommand(cmd, waitMs = 1500) {
    const start = chat.length;
    bot.chat(cmd);
    await sleep(waitMs);
    return chat.slice(start).join('\n');
  }
  // Effects are checked server-side: client effect ids differ between protocol versions
  const hasEffect = async (key) => {
    const data = await rcon.command(`data get entity ${BOT} active_effects`);
    return data.includes(`minecraft:${key}`);
  };
  async function waitForBlock(pos) {
    let block = bot.blockAt(pos);
    for (let i = 0; i < 60 && !block; i++) {
      await sleep(500);
      block = bot.blockAt(pos);
    }
    if (!block) throw new Error(`block at ${pos} never loaded`);
    return block;
  }
  // Waits until the client sees a given block type (server-side setblock reaches the bot a few ticks later)
  async function waitForBlockNamed(pos, name) {
    for (let i = 0; i < 60; i++) {
      const block = bot.blockAt(pos);
      if (block && block.name === name) return block;
      await sleep(250);
    }
    throw new Error(`block at ${pos} never became ${name}`);
  }
  async function teleport(pos) {
    await rcon.command(`tp ${BOT} ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5}`);
    await sleep(500);
    await waitForBlock(pos.offset(0, -1, 0));
    // Freshly generated superflat chunks can contain slimes
    await rcon.command('kill @e[type=slime]');
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

  try {
    await rcon.command(`op ${BOT}`);
    await rcon.command(`gamemode survival ${BOT}`);
    await rcon.command('gamerule advance_time false');
    // Superflat worlds spawn slimes; a death would invalidate later checks (gamerule names are 1.21.11+)
    await rcon.command('gamerule spawn_mobs false');
    await rcon.command('kill @e[type=!player]');
    // Set the time of day once: /time set rewinds the world clock, which also rewinds the calendar
    await rcon.command('time set 6000');
    await rcon.command('weather clear 100000');
    await rcon.command(`clear ${BOT}`);

    // ---------------------------------------------------------------- plugins
    const plugins = await rcon.command('plugins');
    check('Core plugin enabled', /SovereignSeasons-Core/.test(plugins), plugins.replace(/\n/g, ' '));
    check('Pantry plugin enabled', /SovereignSeasons-Pantry/.test(plugins));

    // ---------------------------------------------------------------- core
    await rcon.command('season set spring world');
    const info = await playerCommand('/season info');
    check('/season info shows the calendar', /Estación actual/.test(info) && /Primavera/.test(info), info.split('\n')[1] || info);
    check('/season info shows the temperature', /Temperatura:\s*-?\d+(\.\d)?°C/.test(info));

    const consoleInfo = await rcon.command('season info world');
    check('Console /season info works', /Calendario/.test(consoleInfo) || /Calendario/.test(chat.join('\n')), consoleInfo.slice(0, 80));

    // ---------------------------------------------------------------- pantry: inspect
    await rcon.command(`give ${BOT} minecraft:bread 5`);
    check('Bot received bread', await holdItem('bread'));
    const inspect = await playerCommand('/pantry inspect');
    check('/pantry inspect shows shelf life', /Vida útil base: 7\.0 días/.test(inspect), inspect.split('\n').slice(1, 3).join(' | '));

    const stats = await playerCommand('/pantry stats');
    check('/pantry stats works', /Alimentos perecederos configurados: \d+/.test(stats));

    // ---------------------------------------------------------------- pantry: stale food penalty
    await rcon.command(`give ${BOT} minecraft:cooked_beef 1`);
    await holdItem('cooked_beef');
    const set = await playerCommand('/pantry setfresh 30');
    check('/pantry setfresh 30 accepted', /Frescura establecida en 30%/.test(set), set);
    const staleInspect = await playerCommand('/pantry inspect');
    check('Inspect reports Rancio after setfresh', /Rancio/.test(staleInspect));
    await rcon.command(`effect give ${BOT} minecraft:hunger 8 255`);
    await sleep(8500);
    await rcon.command(`effect clear ${BOT}`);
    await sleep(500);
    check('Bot is hungry enough to eat', bot.food < 20, `food=${bot.food}`);
    await holdItem('cooked_beef');
    try {
      await bot.consume();
    } catch (e) {
      check('Bot ate stale beef', false, e.message);
    }
    await sleep(1000);
    check('Eating stale food applies Hunger', await hasEffect('hunger'));
    await rcon.command(`effect clear ${BOT}`);
    await rcon.command(`effect give ${BOT} minecraft:saturation 2 255`);
    await rcon.command(`effect give ${BOT} minecraft:instant_health 1 10`);

    // ---------------------------------------------------------------- pantry: smoker does not stall
    const base = bot.entity.position.floored();
    const smokerPos = base.offset(2, 0, 0);
    await rcon.command(`setblock ${smokerPos.x} ${smokerPos.y} ${smokerPos.z} minecraft:smoker`);
    await rcon.command(`item replace block ${smokerPos.x} ${smokerPos.y} ${smokerPos.z} container.0 with minecraft:beef 3`);
    await rcon.command(`item replace block ${smokerPos.x} ${smokerPos.y} ${smokerPos.z} container.1 with minecraft:coal 2`);
    await sleep(6500); // smoker: 100 ticks per item
    let furnace = await bot.openFurnace(await waitForBlockNamed(smokerPos, 'smoker'));
    const firstBatch = furnace.outputItem()?.count || 0;
    await furnace.takeOutput();
    furnace.close();
    await sleep(11000);
    furnace = await bot.openFurnace(await waitForBlockNamed(smokerPos, 'smoker'));
    const secondBatch = furnace.outputItem()?.count || 0;
    if (secondBatch > 0) await furnace.takeOutput();
    furnace.close();
    check('Smoker produced a first batch', firstBatch >= 1, `first=${firstBatch}`);
    check('Smoker kept cooking after extraction (no stall)', firstBatch + secondBatch === 3, `first=${firstBatch} second=${secondBatch}`);
    await holdItem('cooked_beef');
    const smoked = await playerCommand('/pantry inspect');
    check('Smoked beef is marked Ahumado', /Conservación: Ahumado/.test(smoked), smoked.split('\n').slice(2, 4).join(' | '));

    // ---------------------------------------------------------------- core: HUD, clock and season title
    let hud = await playerCommand('/season hud');
    if (/ocultado/.test(hud)) hud = await playerCommand('/season hud'); // HUD state persists per player
    check('/season hud enables the BossBar', /HUD de estaciones activado/.test(hud) && bossBars.length > 0, `bars=${bossBars.length}`);
    check('HUD BossBar shows the date', bossBars.some((b) => /Día/.test(b)), bossBars[bossBars.length - 1] || '');

    await rcon.command(`give ${BOT} minecraft:clock 1`);
    await holdItem('clock');
    const actionBefore = actionBars.length;
    bot.activateItem();
    await sleep(1200);
    bot.deactivateItem();
    check('Using a clock shows the date in the action bar', actionBars.slice(actionBefore).some((a) => /Día\s*\d+\/\d+/.test(a) && /°C/.test(a)),
      actionBars.slice(actionBefore).join(' | '));

    const titlesBefore = titles.length;
    await rcon.command('season set winter world');
    await sleep(2500);
    check('Season change shows a title', titles.slice(titlesBefore).some((t) => /Invierno/.test(t))
      && titles.slice(titlesBefore).some((t) => /Comienza una nueva estación/.test(t)), titles.slice(titlesBefore).join(' | '));

    // ---------------------------------------------------------------- core: gentle cold
    const coldSpot = base.offset(40, 0, 0);
    await teleport(coldSpot);
    await rcon.command(`fillbiome ${coldSpot.x - 12} -64 ${coldSpot.z - 12} ${coldSpot.x + 12} -50 ${coldSpot.z + 12} minecraft:snowy_plains`);
    const coldActionBefore = actionBars.length;
    await sleep(3500);
    const coldTemp = await playerCommand('/season info');
    check('Snowy plains in winter are Helado', /Helado/.test(coldTemp), (coldTemp.match(/Temperatura:[^\n]*/) || [''])[0]);
    check('Freezing exposure applies Slowness', await hasEffect('slowness'));
    check('Freezing exposure warns in the action bar', actionBars.slice(coldActionBefore).some((a) => /frío helado/.test(a)));
    check('Cold without damage keeps full health', bot.health >= 20, `health=${bot.health}`);
    await rcon.command(`item replace entity ${BOT} armor.head with minecraft:leather_helmet`);
    await sleep(4000);
    check('A leather piece protects from the cold', !(await hasEffect('slowness')));
    await rcon.command(`item replace entity ${BOT} armor.head with minecraft:air`);

    // ---------------------------------------------------------------- core: gentle heat
    await rcon.command('season set summer world');
    const hotSpot = base.offset(80, 0, 0);
    await teleport(hotSpot);
    await rcon.command(`fillbiome ${hotSpot.x - 12} -64 ${hotSpot.z - 12} ${hotSpot.x + 12} -50 ${hotSpot.z + 12} minecraft:desert`);
    const heatActionBefore = actionBars.length;
    await sleep(3500);
    const hotTemp = await playerCommand('/season info');
    check('Desert in summer is Caluroso', /Caluroso/.test(hotTemp), (hotTemp.match(/Temperatura:[^\n]*/) || [''])[0]);
    check('Heat exposure warns in the action bar', actionBars.slice(heatActionBefore).some((a) => /calor/.test(a)));

    // ---------------------------------------------------------------- core: seasonal crop climate
    // Potatoes like cold/mild climates: they should grow clearly slower in a hot desert than in plains.
    await rcon.command('season set spring world');
    await rcon.command('season setday 4 world');
    const farm = base.offset(120, 0, 0);
    await teleport(farm.offset(5, 0, 5));
    await rcon.command(`fillbiome ${farm.x - 2} -64 ${farm.z + 8} ${farm.x + 12} -50 ${farm.z + 14} minecraft:desert`);
    for (const dz of [0, 10]) {
      const z = farm.z + dz;
      await rcon.command(`fill ${farm.x} ${farm.y - 1} ${z} ${farm.x + 9} ${farm.y - 1} ${z} minecraft:farmland[moisture=7]`);
      await rcon.command(`fill ${farm.x} ${farm.y - 1} ${z + 1} ${farm.x + 9} ${farm.y - 1} ${z + 1} minecraft:water`);
      await rcon.command(`fill ${farm.x} ${farm.y} ${z} ${farm.x + 9} ${farm.y} ${z} minecraft:potatoes[age=0]`);
    }
    await rcon.command('gamerule random_tick_speed 1000');
    await sleep(6000);
    await rcon.command('gamerule random_tick_speed 3');
    await sleep(1000);
    const ageSum = (dz) => {
      let sum = 0;
      for (let dx = 0; dx < 10; dx++) {
        const block = bot.blockAt(new Vec3(farm.x + dx, farm.y, farm.z + dz));
        sum += block && block.name === 'potatoes' ? Number(block.getProperties().age) : 0;
      }
      return sum;
    };
    const plainsAge = ageSum(0);
    const desertAge = ageSum(10);
    check('Potatoes grow faster in their ideal climate than in a hot desert', plainsAge > desertAge * 1.5 && plainsAge > 0,
      `plains=${plainsAge} desert=${desertAge}`);

    // ---------------------------------------------------------------- core: spring twins
    const pen = farm.offset(0, 0, 20);
    await teleport(pen);
    await rcon.command('kill @e[type=cow]');
    await sleep(800);
    await rcon.command(`summon cow ${pen.x + 2} ${pen.y} ${pen.z}`);
    await rcon.command(`summon cow ${pen.x + 2} ${pen.y} ${pen.z + 1}`);
    await rcon.command(`give ${BOT} minecraft:wheat 4`);
    await sleep(1500);
    await holdItem('wheat');
    for (const cow of Object.values(bot.entities).filter((e) => e.name === 'cow')) {
      await bot.lookAt(cow.position.offset(0, 1, 0));
      await bot.activateEntity(cow);
      await sleep(400);
    }
    await sleep(8000);
    const cows = await rcon.command('execute if entity @e[type=cow]');
    const cowCount = Number((cows.match(/count: (\d+)/i) || [0, 0])[1]);
    check('Breeding in spring (twin chance 100% in test config) yields twins', cowCount === 4, cows);

    // ---------------------------------------------------------------- pantry: composting rotten flesh
    const composterPos = pen.offset(-2, 0, 0);
    await rcon.command(`setblock ${composterPos.x} ${composterPos.y} ${composterPos.z} minecraft:composter`);
    // The test server config sets compost.rotten-flesh-chance to 1.0, so 7 pieces fill a composter
    await rcon.command(`give ${BOT} minecraft:rotten_flesh 7`);
    await holdItem('rotten_flesh');
    const composter = await waitForBlockNamed(composterPos, 'composter');
    for (let i = 0; i < 7; i++) {
      await bot.activateBlock(composter);
      await sleep(300);
    }
    await sleep(2000);
    const ready = await rcon.command(`execute if block ${composterPos.x} ${composterPos.y} ${composterPos.z} minecraft:composter[level=8]`);
    const leftover = bot.inventory.items().filter((i) => i.name === 'rotten_flesh').reduce((n, i) => n + i.count, 0);
    check('Rotten flesh fills a composter until it is ready', /passed/i.test(ready) && leftover === 0, `${ready.trim()} leftover=${leftover}`);

    // Seasonal rain is covered by unit tests only: vanilla offers no way to trigger a *natural*
    // rain start on demand, so an end-to-end check here would pass without exercising the filter.

    // ---------------------------------------------------------------- pantry: container decay
    await teleport(base);
    // Raw beef tracked in the player's inventory: it will rot during the five days skipped below
    await rcon.command(`give ${BOT} minecraft:beef 2`);
    await holdItem('beef');
    await playerCommand('/pantry setfresh 100');
    const chestPos = base.offset(-2, 0, 0);
    await rcon.command(`setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:chest`);
    await rcon.command(`item replace block ${chestPos.x} ${chestPos.y} ${chestPos.z} container.0 with minecraft:beef 4`);
    let chest = await bot.openContainer(await waitForBlockNamed(chestPos, 'chest'));
    chest.close();
    await rcon.command('season set summer world');
    await rcon.command('time add 120000'); // five in-game days
    await sleep(1000);
    chest = await bot.openContainer(await waitForBlockNamed(chestPos, 'chest'));
    const slot0 = chest.containerItems()[0];
    chest.close();
    check('Raw beef in a chest rots into rotten flesh after five summer days',
      slot0 && slot0.name === 'rotten_flesh' && slot0.count === 4, slot0 ? `${slot0.name} x${slot0.count}` : 'empty');

    // ---------------------------------------------------------------- pantry: inventory spoilage warning
    // The test server config sets decay.player-check-interval-ticks to 100
    const warnBefore = actionBars.length;
    await sleep(7000);
    const rottenInInventory = bot.inventory.items().filter((i) => i.name === 'rotten_flesh').reduce((n, i) => n + i.count, 0);
    check('Food rotting in the inventory warns the player', actionBars.slice(warnBefore).some((a) => /podrido|echando a perder/.test(a)),
      actionBars.slice(warnBefore).join(' | '));
    check('Raw beef in the inventory became rotten flesh', rottenInInventory >= 2, `rotten=${rottenInInventory}`);
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
