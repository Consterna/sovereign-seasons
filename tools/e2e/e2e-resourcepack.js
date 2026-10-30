/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// End-to-end checks for the Sovereign Seasons resource pack plugin, with the bot acting as the game client:
// it receives the pack packets, downloads every zip over HTTP and checks its SHA-1, answers accepted / loaded /
// declined, and follows the seasonal pack swaps. A second bot that never loads the pack checks that the season
// icon only reaches clients that can draw it. Logistics crates must carry their custom model data.
//
// Usage: node e2e-resourcepack.js [path/to/server.properties]

const fs = require('fs');
const path = require('path');
const http = require('http');
const crypto = require('crypto');
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
const OTHER = 'e2enopack';
const CONSOLE_LOG = path.join(serverDir, 'console.log');
const RESULT = { SUCCESSFULLY_LOADED: 0, DECLINED: 1, FAILED_DOWNLOAD: 2, ACCEPTED: 3 };
const ICONS = { spring: '', summer: '', autumn: '', winter: '' };

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

function download(url, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method, timeout: 10000 }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve({ status: res.statusCode, type: res.headers['content-type'], body: Buffer.concat(chunks) }));
    });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.end();
  });
}

function joinBot(name) {
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: name, auth: 'offline', version: process.env.MC_VERSION || '1.21.11' });
  bot.packs = [];      // add_resource_pack packets, in order
  bot.removed = [];    // remove_resource_pack packets
  bot.hudTitles = []; // not bot.bossBars: Mineflayer already defines that property
  bot.chatLines = [];
  bot._client.on('add_resource_pack', (p) => bot.packs.push({ uuid: String(p.uuid), url: p.url, hash: p.hash, forced: p.forced }));
  bot._client.on('remove_resource_pack', (p) => bot.removed.push(p.uuid === undefined ? '*' : String(p.uuid)));
  bot.on('bossBarCreated', (bar) => bot.hudTitles.push(bar.title.toString()));
  bot.on('bossBarUpdated', (bar) => bot.hudTitles.push(bar.title.toString()));
  bot.on('messagestr', (m) => bot.chatLines.push(m));
  bot.on('kicked', (reason) => console.log('KICKED', name, JSON.stringify(reason)));
  bot.on('error', (err) => console.log('BOT ERROR', name, err.message));
  return new Promise((resolve, reject) => {
    bot.once('spawn', () => resolve(bot));
    setTimeout(() => reject(new Error(`${name} spawn timeout`)), 60000);
  });
}

function answer(bot, uuid, ...codes) {
  for (const result of codes) bot._client.write('resource_pack_receive', { uuid, result });
}

async function waitFor(fn, ms = 8000) {
  for (let waited = 0; waited < ms; waited += 250) {
    const value = fn();
    if (value) return value;
    await sleep(250);
  }
  return fn();
}

async function main() {
  const rcon = await new Rcon(HOST, Number(props['rcon.port']), props['rcon.password']).connect();
  const cmd = (c) => rcon.command(c);
  await cmd('season set spring world');
  const bot = await joinBot(BOT);
  await sleep(1000);
  const say = async (text, ms = 1500) => {
    const start = bot.chatLines.length;
    bot.chat(text);
    await sleep(ms);
    return bot.chatLines.slice(start).join('\n');
  };
  let other = null;

  try {
    await cmd(`op ${BOT}`);
    const plugins = await cmd('plugins');
    check('ResourcePack plugin enabled', /SovereignSeasons-ResourcePack/.test(plugins), plugins.replace(/\s+/g, ' '));
    const log = fs.readFileSync(CONSOLE_LOG, 'utf8');
    check('The built-in pack server started', /Hosting: built-in server on port \d+/.test(log));

    // ---------------------------------------------------------------- join: base pack + spring pack
    await waitFor(() => bot.packs.length >= 2);
    const [base, spring] = bot.packs;
    check('On join the client gets the base pack and the pack of the current season (spring)',
      bot.packs.length === 2 && /sovereign-seasons-base\.zip$/.test(base.url) && /sovereign-seasons-spring\.zip$/.test(spring.url),
      bot.packs.map((p) => p.url).join(' | '));
    check('Packs are optional (not forced) by default', bot.packs.every((p) => p.forced === false));

    let downloadsOk = true;
    const detail = [];
    for (const pack of bot.packs) {
      const res = await download(pack.url);
      const sha1 = crypto.createHash('sha1').update(res.body).digest('hex');
      const ok = res.status === 200 && res.type === 'application/zip' && res.body.subarray(0, 2).toString() === 'PK' && sha1 === pack.hash;
      downloadsOk = downloadsOk && ok;
      detail.push(`${path.basename(pack.url)} ${res.status} ${res.body.length}B sha1=${ok ? 'ok' : sha1 + '!=' + pack.hash}`);
    }
    check('Every pack downloads from the advertised URL and matches its SHA-1', downloadsOk, detail.join(' | '));
    const notFound = await download(`${new URL(base.url).origin}/sovereignseasons/../config.yml`);
    const post = await download(base.url, 'POST');
    check('The pack server serves nothing else (404) and only GET/HEAD (405)', notFound.status === 404 && post.status === 405,
      `traversal=${notFound.status} post=${post.status}`);

    answer(bot, base.uuid, RESULT.ACCEPTED, RESULT.SUCCESSFULLY_LOADED);
    answer(bot, spring.uuid, RESULT.ACCEPTED, RESULT.SUCCESSFULLY_LOADED);
    await sleep(1000);
    const status = await say('/seasonpack status');
    check('The server tracks both packs as loaded', /base: successfully_loaded/.test(status) && /estación: spring/.test(status), status.replace(/\s+/g, ' '));

    // ---------------------------------------------------------------- season icon only with the pack
    // The HUD state is stored per player and an earlier suite may have left it on: make sure it ends up on
    if (/ocultado/.test(await say('/season hud'))) await say('/season hud');
    const springIcon = await waitFor(() => bot.hudTitles.some((t) => t.includes(ICONS.spring)), 6000);
    check('With the base pack loaded, the HUD shows the spring icon', springIcon, JSON.stringify(bot.hudTitles.slice(-1)));

    other = await joinBot(OTHER);
    await sleep(2000);
    other.chat('/season hud'); // a new player: the HUD starts off, so this turns it on
    await waitFor(() => other.hudTitles.length > 0, 6000);
    await sleep(2500);
    check('A client that never loaded the pack gets no icon (it would draw an empty box)',
      other.hudTitles.length > 0 && !other.hudTitles.some((t) => /[-]/.test(t)), JSON.stringify(other.hudTitles.slice(-1)));
    other.quit();
    other = null;

    // ---------------------------------------------------------------- seasonal swaps
    bot.packs.length = 0;
    bot.removed.length = 0;
    await cmd('season set autumn world');
    await waitFor(() => bot.packs.length >= 1);
    const autumn = bot.packs[0];
    check('Autumn: the spring pack is removed and the autumn pack added, the base pack stays',
      bot.removed.length === 1 && bot.removed[0] === spring.uuid && bot.packs.length === 1 && /autumn\.zip$/.test(autumn.url),
      `removed=${bot.removed.join(',')} added=${bot.packs.map((p) => path.basename(p.url)).join(',')}`);
    answer(bot, autumn.uuid, RESULT.ACCEPTED, RESULT.SUCCESSFULLY_LOADED);
    const autumnIcon = await waitFor(() => bot.hudTitles.some((t) => t.includes(ICONS.autumn)), 6000);
    check('The HUD icon follows the season (autumn)', autumnIcon, JSON.stringify(bot.hudTitles.slice(-1)));

    bot.packs.length = 0;
    bot.removed.length = 0;
    await cmd('season set summer world');
    await waitFor(() => bot.removed.length >= 1);
    await sleep(1500);
    check('Summer keeps the vanilla colours: the autumn pack is removed and nothing is added',
      bot.removed.length === 1 && bot.removed[0] === autumn.uuid && bot.packs.length === 0,
      `removed=${bot.removed.length} added=${bot.packs.length}`);

    // ---------------------------------------------------------------- declining is respected
    bot.packs.length = 0;
    await cmd('season set winter world');
    await waitFor(() => bot.packs.length >= 1);
    const winter = bot.packs[0];
    answer(bot, winter.uuid, RESULT.DECLINED);
    await sleep(800);
    bot.packs.length = 0;
    bot.removed.length = 0;
    await cmd('season set spring world');
    await sleep(2500);
    const declinedStatus = await say('/seasonpack status');
    check('After declining, no new seasonal pack is pushed',
      /winter\.zip$/.test(winter.url) && bot.packs.length === 0 && /rechazado/.test(declinedStatus),
      `added=${bot.packs.length} removed=${bot.removed.length} status=${declinedStatus.replace(/\s+/g, ' ')}`);

    bot.packs.length = 0;
    await say('/seasonpack resend');
    await waitFor(() => bot.packs.length >= 2);
    check('/seasonpack resend sends the base pack and the current seasonal pack again',
      bot.packs.length === 2 && /base\.zip$/.test(bot.packs[0].url) && /spring\.zip$/.test(bot.packs[1].url),
      bot.packs.map((p) => path.basename(p.url)).join(','));
    for (const pack of bot.packs) answer(bot, pack.uuid, RESULT.ACCEPTED, RESULT.SUCCESSFULLY_LOADED);
    await sleep(800);

    // ---------------------------------------------------------------- crates carry their model number
    await cmd(`clear ${BOT}`);
    await cmd(`give ${BOT} minecraft:carrot 9`);
    await sleep(800);
    const carrot = bot.inventory.items().find((i) => i.name === 'carrot');
    if (carrot) await bot.equip(carrot, 'hand');
    await bot.look(bot.entity.yaw, 0, true);
    bot.setControlState('sneak', true);
    await sleep(500);
    bot.activateItem();
    await sleep(900);
    bot.setControlState('sneak', false);
    await sleep(600);
    const model = await cmd(`data get entity ${BOT} SelectedItem.components."minecraft:custom_model_data"`);
    check('A Logistics crate of carrots carries custom model data 7301001 for the pack', /7301001/.test(model), model.trim().slice(0, 160));

    const stats = await cmd('seasonpack stats');
    const sent = Number((stats.match(/Enviados: (\d+)/) || [0, 0])[1]);
    const loaded = Number((stats.match(/Cargados: (\d+)/) || [0, 0])[1]);
    const declined = Number((stats.match(/Rechazados: (\d+)/) || [0, 0])[1]);
    const served = Number((stats.match(/Descargas servidas: (\d+)/) || [0, 0])[1]);
    check('/seasonpack stats counts sends, loads, declines and downloads', sent >= 6 && loaded >= 5 && declined >= 1 && served >= 2,
      `sent=${sent} loaded=${loaded} declined=${declined} served=${served}`);
  } catch (e) {
    check('Unexpected exception during scenario', false, e.stack);
  } finally {
    if (other) other.quit();
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
