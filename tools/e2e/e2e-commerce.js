/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// End-to-end checks for Sovereign Seasons Commerce: seasonal villager prices (client and server side), the
// vanilla reset of those prices, spoiling food refused at the counter (Pantry), wandering trader fairs and
// prices, and wholesale of Logistics crates with its daily limit.
//
// Usage: node e2e-commerce.js [path/to/server.properties]

const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
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
const COMMERCE_CONFIG = path.join(serverDir, 'plugins', 'SovereignSeasons-Commerce', 'config.yml');
const CONSOLE_LOG = path.join(serverDir, 'console.log');

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}
const withTimeout = (promise, ms) => Promise.race([promise, sleep(ms).then(() => { throw new Error(`timeout after ${ms} ms`); })]);

async function main() {
  const rcon = await new Rcon(HOST, Number(props['rcon.port']), props['rcon.password']).connect();
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: BOT, auth: 'offline', version: process.env.MC_VERSION || '1.21.11' });
  const chat = [];
  const actionBars = [];
  const flatText = (component) => {
    const json = typeof component === 'string' ? component : JSON.stringify(component);
    const parts = [];
    const re = /"text":(?:\{"type":"string","value":)?"((?:[^"\\]|\\.)*)"/g;
    let m;
    while ((m = re.exec(json)) !== null) parts.push(m[1]);
    return parts.join('');
  };
  bot.on('messagestr', (msg) => chat.push(msg));
  // Mineflayer does not emit 'actionBar' reliably on 1.21.11, so read the raw packet
  bot._client.on('action_bar', (packet) => actionBars.push(flatText(packet.text)));
  bot.on('kicked', (reason) => console.log('KICKED', JSON.stringify(reason)));
  bot.on('error', (err) => console.log('BOT ERROR', err.message));
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
  async function waitForBlock(pos) {
    for (let i = 0; i < 60; i++) {
      const block = bot.blockAt(pos);
      if (block) return block;
      await sleep(500);
    }
    throw new Error(`block at ${pos} never loaded`);
  }
  async function teleport(pos) {
    await cmd(`tp ${BOT} ${pos.x + 0.5} ${pos.y} ${pos.z + 0.5} -90 0`);
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
  /** Rewrites one value of the commerce config (test server only) and reloads it. */
  async function setConfig(pattern, replacement) {
    const text = fs.readFileSync(COMMERCE_CONFIG, 'utf8').replace(/\r\n/g, '\n');
    if (!pattern.test(text)) throw new Error(`pattern ${pattern} not found in commerce config`);
    fs.writeFileSync(COMMERCE_CONFIG, text.replace(pattern, replacement), 'utf8');
    await cmd('commerce reload');
  }
  /** Reads one NBT value of the first entity with a tag (server side). */
  async function nbt(tag, nbtPath) {
    const out = await cmd(`data get entity @e[tag=${tag},limit=1] ${nbtPath}`);
    const m = out.match(/has the following entity data: (.*)$/s);
    return m ? m[1].trim() : null;
  }
  /** Special price of an offer: vanilla omits the field from NBT when it is 0. */
  async function specialPrice(tag, index) {
    const out = await cmd(`data get entity @e[tag=${tag},limit=1] Offers.Recipes[${index}].specialPrice`);
    const m = out.match(/has the following entity data: (-?\d+)/);
    if (m) return m[1];
    return /Found no elements/i.test(out) ? '0' : `?(${out.trim()})`;
  }
  /** Whether an entity has an offer selling this item (exact query: full offer lists get truncated over RCON). */
  async function sellsItem(tag, item) {
    const out = await cmd(`execute if data entity @e[tag=${tag},limit=1] Offers.Recipes[{sell:{id:"minecraft:${item}"}}]`);
    return /passed/i.test(out);
  }
  const entityWithTag = (type) => Object.values(bot.entities)
    .filter((e) => e.name === type)
    .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
  async function waitForEntity(type) {
    for (let i = 0; i < 40; i++) {
      const entity = entityWithTag(type);
      if (entity) return entity;
      await sleep(250);
    }
    throw new Error(`no ${type} visible to the bot`);
  }
  async function openFarmer() {
    const villager = await withTimeout(bot.openVillager(await waitForEntity('villager')), 10000);
    await sleep(500);
    return villager;
  }
  async function closeWindow(window) {
    if (window) window.close();
    else if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
    await sleep(800);
  }
  const offer = (window, output) => window.trades.find((t) => t.outputItem && t.outputItem.name === output);
  const buyOffer = (window, input) => window.trades.find((t) => t.inputItem1 && t.inputItem1.name === input && t.outputItem.name === 'emerald');
  async function sneakUse(useFn) {
    bot.setControlState('sneak', true);
    await sleep(500);
    await useFn();
    await sleep(1000);
    bot.setControlState('sneak', false);
    await sleep(500);
  }
  /**
   * Sneaking right-click on an entity. Mineflayer's activateEntity always reports sneaking=false in the
   * use_entity packet (and the server takes the sneak state from that packet), so send it like a real client.
   */
  async function sneakClickEntity(entity) {
    await bot.lookAt(entity.position.offset(0, 1, 0), true);
    bot.setControlState('sneak', true);
    await sleep(500);
    bot._client.write('use_entity', { target: entity.id, mouse: 0, sneaking: true, hand: 0, location: { x: 0, y: 0, z: 0 } });
    await sleep(1200);
    bot.setControlState('sneak', false);
    await sleep(500);
  }
  async function packCrates(n) {
    await holdItem('carrot');
    await bot.look(bot.entity.yaw, 0, true); // packing happens when right-clicking the air
    await sneakUse(async () => {
      for (let i = 0; i < n; i++) {
        bot.activateItem();
        await sleep(700);
      }
    });
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

    const plugins = await cmd('plugins');
    check('Commerce plugin enabled', /SovereignSeasons-Commerce/.test(plugins), plugins.replace(/\s+/g, ' '));
    const log = fs.readFileSync(CONSOLE_LOG, 'utf8');
    check('Commerce links Pantry and Logistics at startup',
      /Spoiling Food Refused: ACTIVE \(Pantry linked\)/.test(log) && /Bulk Crate Sales: ACTIVE \(Logistics linked\)/.test(log));

    const spawn = bot.entity.position.floored();
    const base = spawn.offset(400, 0, 0);
    await teleport(base);
    const fx = base.x + 2;
    const fz = base.z;
    // A farmer that buys carrots and sells golden carrots and bread (NoAI keeps it in place; Xp keeps its job)
    await cmd(`summon villager ${fx + 0.5} ${base.y} ${fz + 0.5} {NoAI:1b,Invulnerable:1b,Silent:1b,Tags:["e2e_farmer"],Xp:10,`
      + 'VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"},Offers:{Recipes:['
      + '{buy:{id:"minecraft:carrot",count:22},sell:{id:"minecraft:emerald",count:1},maxUses:99,xp:2,priceMultiplier:0.05f},'
      + '{buy:{id:"minecraft:emerald",count:3},sell:{id:"minecraft:golden_carrot",count:3},maxUses:99,xp:5,priceMultiplier:0.05f},'
      + '{buy:{id:"minecraft:emerald",count:1},sell:{id:"minecraft:bread",count:6},maxUses:99,xp:1,priceMultiplier:0.05f}]}}');
    await sleep(1500);
    await bot.lookAt(bot.entity.position.offset(2, 1, 0), true);

    // ---------------------------------------------------------------- seasonal prices
    await cmd('season set winter world');
    let window = await openFarmer();
    const winterCarrots = buyOffer(window, 'carrot');
    const winterGolden = offer(window, 'golden_carrot');
    const winterBread = offer(window, 'bread');
    const serverSpecial = await specialPrice('e2e_farmer', 0);
    check('Winter: the farmer asks 16 carrots per emerald instead of 22 (seen by the client)',
      winterCarrots && winterCarrots.realPrice === 16, winterCarrots ? `realPrice=${winterCarrots.realPrice}` : 'no carrot offer');
    check('Winter: the adjustment is applied server side while trading', serverSpecial === '-6', `specialPrice=${serverSpecial}`);
    check('Winter: golden carrots cost 4 emeralds instead of 3', winterGolden && winterGolden.realPrice === 4,
      winterGolden ? `realPrice=${winterGolden.realPrice}` : 'no offer');
    check('A 1-emerald trade never drops below one emerald', winterBread && winterBread.realPrice === 1,
      winterBread ? `realPrice=${winterBread.realPrice}` : 'no offer');
    const noticeSeen = actionBars.some((a) => /Precios de/.test(a) && /Invierno/.test(a));
    check('Opening a trade with seasonal prices shows a notice', noticeSeen, actionBars.slice(-3).join(' | '));
    await closeWindow(window);
    const afterClose = await specialPrice('e2e_farmer', 0);
    check('Closing the trade leaves no adjustment stored in the villager', afterClose === '0', `specialPrice=${afterClose}`);

    await cmd('season set autumn world');
    window = await openFarmer();
    const autumnCarrots = buyOffer(window, 'carrot');
    check('Autumn harvest: carrots are abundant, the farmer asks 28 per emerald', autumnCarrots && autumnCarrots.realPrice === 28,
      autumnCarrots ? `realPrice=${autumnCarrots.realPrice}` : 'no offer');
    await closeWindow(window);

    await cmd('season set summer world');
    window = await openFarmer();
    const summerCarrots = buyOffer(window, 'carrot');
    check('Summer (carrots out of season, not winter): vanilla price of 22', summerCarrots && summerCarrots.realPrice === 22,
      summerCarrots ? `realPrice=${summerCarrots.realPrice}` : 'no offer');
    await closeWindow(window);

    // A real trade at the winter price, with carrots tracked by Pantry
    await cmd('season set winter world');
    await cmd(`give ${BOT} minecraft:carrot 16`);
    await holdItem('carrot');
    await playerCommand('/pantry setfresh 100');
    const tracked = await cmd(`data get entity ${BOT} SelectedItem.components."minecraft:custom_data"`);
    window = await openFarmer();
    let traded = true;
    try {
      await withTimeout(window.trade(window.trades.indexOf(buyOffer(window, 'carrot')), 1), 8000);
    } catch (e) {
      traded = false;
      console.log('trade error:', e.message);
    }
    await closeWindow(window);
    await sleep(500);
    check('Fresh Pantry carrots sell at the winter price (16 carrots -> 1 emerald)',
      traded && count('emerald') === 1 && count('carrot') === 0,
      `traded=${traded} emeralds=${count('emerald')} carrots=${count('carrot')} pantryData=${/pantry/.test(tracked)}`);

    // ---------------------------------------------------------------- spoiling food refused
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:carrot 16`);
    await holdItem('carrot');
    await playerCommand('/pantry setfresh 10'); // below Pantry's stale-above (15 %): SPOILING
    const barsBefore = actionBars.length;
    window = await openFarmer();
    try {
      await withTimeout(window.trade(window.trades.indexOf(buyOffer(window, 'carrot')), 1), 5000);
    } catch (e) {
      // expected: the server cancels the trade, so the result never arrives
    }
    await closeWindow(window);
    await sleep(800);
    check('Villagers refuse carrots that are rotting and nothing is lost',
      count('emerald') === 0 && count('carrot') === 16 && actionBars.slice(barsBefore).some((a) => /rechaza/.test(a)),
      `emeralds=${count('emerald')} carrots=${count('carrot')} bars=${actionBars.slice(barsBefore).join(' | ')}`);

    // ---------------------------------------------------------------- bulk crate sales
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:carrot 27`);
    await holdItem('carrot');
    await playerCommand('/pantry setfresh 100');
    await packCrates(3);
    const packed = count('barrel');
    await holdItem('barrel');
    await bot.lookAt(bot.entity.position.offset(2, 1.2, 0), true);
    const farmer = await waitForEntity('villager');
    await sneakClickEntity(farmer);
    const sold = await nbt('e2e_farmer', 'BukkitValues."sovereignseasons-commerce:bulk_sold"');
    // Winter: 22 carrots/emerald / (1.4 x 1.15) = 13.66 per emerald -> 3 crates (27) pay 1 emerald using 2 crates
    check('Sneak + right-click sells crates wholesale at the seasonal price (2 crates -> 1 emerald)',
      packed === 3 && count('emerald') === 1 && count('barrel') === 1 && sold === '2',
      `packed=${packed} emeralds=${count('emerald')} crates=${count('barrel')} bulk_sold=${sold}`);
    check('A wholesale sale never opens the trade screen', !bot.currentWindow, bot.currentWindow ? bot.currentWindow.type : '');

    await setConfig(/max-crates-per-villager-per-day: \d+/, 'max-crates-per-villager-per-day: 2');
    const limitBefore = actionBars.length;
    await holdItem('barrel');
    await sneakClickEntity(farmer);
    check('The daily limit stops a villager from buying more crates',
      count('barrel') === 1 && count('emerald') === 1 && actionBars.slice(limitBefore).some((a) => /suficientes cajas hoy/.test(a)),
      `crates=${count('barrel')} emeralds=${count('emerald')} bars=${actionBars.slice(limitBefore).join(' | ')}`);
    await setConfig(/max-crates-per-villager-per-day: \d+/, 'max-crates-per-villager-per-day: 8');

    // Crates of rotting carrots are refused
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:carrot 27`);
    await holdItem('carrot');
    await playerCommand('/pantry setfresh 10');
    await packCrates(3);
    const rottingBefore = actionBars.length;
    await holdItem('barrel');
    await sneakClickEntity(farmer);
    check('Crates of rotting food are refused',
      count('barrel') === 3 && count('emerald') === 0 && actionBars.slice(rottingBefore).some((a) => /pudriendo/.test(a)),
      `crates=${count('barrel')} emeralds=${count('emerald')} bars=${actionBars.slice(rottingBefore).join(' | ')}`);

    // A villager that does not buy carrots
    await cmd(`kill @e[tag=e2e_farmer]`);
    await cmd(`summon villager ${fx + 0.5} ${base.y} ${fz + 0.5} {NoAI:1b,Invulnerable:1b,Silent:1b,Tags:["e2e_mason"],Xp:10,`
      + 'VillagerData:{profession:"minecraft:mason",level:2,type:"minecraft:plains"},Offers:{Recipes:['
      + '{buy:{id:"minecraft:clay_ball",count:10},sell:{id:"minecraft:emerald",count:1},maxUses:99}]}}');
    await sleep(1500);
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:carrot 27`);
    await holdItem('carrot');
    await playerCommand('/pantry setfresh 100');
    await packCrates(3);
    const masonBefore = actionBars.length;
    await holdItem('barrel');
    await bot.lookAt(bot.entity.position.offset(2, 1.2, 0), true);
    await sneakClickEntity(await waitForEntity('villager'));
    check('A villager without a trade for the crop does not buy its crates',
      count('barrel') === 3 && count('emerald') === 0 && actionBars.slice(masonBefore).some((a) => /no compra/.test(a)),
      `crates=${count('barrel')} bars=${actionBars.slice(masonBefore).join(' | ')}`);
    await cmd('kill @e[tag=e2e_mason]');

    // ---------------------------------------------------------------- wandering trader: fair and prices
    await cmd('season set summer world');
    await cmd(`summon wandering_trader ${fx + 0.5} ${base.y} ${fz + 0.5} {NoAI:1b,Invulnerable:1b,Silent:1b,Tags:["e2e_trader"],DespawnDelay:0,`
      + 'Offers:{Recipes:[{buy:{id:"minecraft:carrot",count:22},sell:{id:"minecraft:emerald",count:1},maxUses:99}]}}');
    await sleep(1500);
    await bot.lookAt(bot.entity.position.offset(2, 1.2, 0), true);
    const trader = await waitForEntity('wandering_trader');
    let traderWindow = await withTimeout(bot.openEntity(trader), 10000);
    await sleep(800);
    const summerFair = { packedIce: await sellsItem('e2e_trader', 'packed_ice'), blueIce: await sellsItem('e2e_trader', 'blue_ice'),
      campfire: await sellsItem('e2e_trader', 'campfire') };
    check('Summer fair: the wandering trader adds ice for Pantry cellars',
      summerFair.packedIce && summerFair.blueIce && !summerFair.campfire, JSON.stringify(summerFair));
    await closeWindow(traderWindow);

    await cmd('season set winter world');
    traderWindow = await withTimeout(bot.openEntity(trader), 10000);
    await sleep(800);
    const winterFair = { campfire: await sellsItem('e2e_trader', 'campfire'), packedIce: await sellsItem('e2e_trader', 'packed_ice'),
      ownOffer: await sellsItem('e2e_trader', 'emerald') };
    const traderSpecial = await specialPrice('e2e_trader', 0);
    check('When the season changes the fair offers are replaced, not piled up',
      winterFair.campfire && !winterFair.packedIce && winterFair.ownOffer, JSON.stringify(winterFair));
    check('Winter prices also apply to wandering traders', traderSpecial === '-6', `specialPrice=${traderSpecial}`);
    await closeWindow(traderWindow);
    const traderAfter = await specialPrice('e2e_trader', 0);
    traderWindow = await withTimeout(bot.openEntity(trader), 10000);
    await sleep(800);
    const traderReopen = await specialPrice('e2e_trader', 0);
    await closeWindow(traderWindow);
    check('Wandering trader prices are cleared on close and never pile up', traderAfter === '0' && traderReopen === '-6',
      `afterClose=${traderAfter} reopened=${traderReopen}`);

    // Turning fairs off takes back the offers added while they were on
    await setConfig(/(fairs:\s*\n\s*enabled: )true/, '$1false');
    traderWindow = await withTimeout(bot.openEntity(trader), 10000);
    await sleep(800);
    const offFair = { campfire: await sellsItem('e2e_trader', 'campfire'), ownOffer: await sellsItem('e2e_trader', 'emerald') };
    await closeWindow(traderWindow);
    await setConfig(/(fairs:\s*\n\s*enabled: )false/, '$1true');
    check('Disabling fairs removes the fair offers and keeps the trader own ones', !offFair.campfire && offFair.ownOffer, JSON.stringify(offFair));
    await cmd('kill @e[tag=e2e_trader]');

    // ---------------------------------------------------------------- command
    const prices = await cmd('commerce prices winter');
    check('/commerce prices lists winter scarcity', /Carrot ×1\.40/.test(prices) && /escaso/.test(prices), prices.replace(/\s+/g, ' ').slice(0, 160));
    const stats = await cmd('commerce stats');
    check('/commerce stats counts the activity of this run', /Cajas vendidas: 2 por 1 esmeraldas/.test(stats) && /rechazada: [1-9]/.test(stats),
      stats.replace(/\s+/g, ' ').slice(0, 220));
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
