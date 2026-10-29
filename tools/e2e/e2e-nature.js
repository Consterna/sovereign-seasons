/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// Slow end-to-end checks for natural phenomena that cannot be triggered on demand
// (natural rain starts, natural mob spawns). They use '/tick sprint' to fast-forward the server.
// With the 'advance_time' gamerule off, the time of day (and therefore the calendar) stays fixed
// while weather cycles and mob spawning keep running.
//
// Usage: node e2e-nature.js [path/to/server.properties]

const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Rcon } = require('./rcon');

const serverDir = path.dirname(process.argv[2] || path.join(__dirname, '..', '..', 'testserver', 'server.properties'));
const props = Object.fromEntries(
  fs.readFileSync(path.join(serverDir, 'server.properties'), 'utf8').split(/\r?\n/).filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)])
);
const HOST = props['server-ip'] || '127.0.0.1';
const PORT = Number(props['server-port'] || 25565);
const BOT = 'e2ebot';
const CORE_CONFIG = path.join(serverDir, 'plugins', 'SovereignSeasons-Core', 'config.yml');
const CONSOLE_LOG = path.join(serverDir, 'console.log');

const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}

/** Rewrites one value of the core config (test server only) and reloads it. */
async function setCoreConfig(rcon, pattern, replacement) {
  const text = fs.readFileSync(CORE_CONFIG, 'utf8').replace(/\r\n/g, '\n');
  if (!pattern.test(text)) throw new Error(`pattern ${pattern} not found in core config`);
  fs.writeFileSync(CORE_CONFIG, text.replace(pattern, replacement), 'utf8');
  await rcon.command('season reload');
}

const logSize = () => fs.readFileSync(CONSOLE_LOG, 'utf8').length;

/** Counts the seasonal weather decisions logged (weather.debug) since a log offset, by decision and cause. */
function weatherDecisions(fromOffset) {
  const tail = fs.readFileSync(CONSOLE_LOG, 'utf8').slice(fromOffset);
  const counts = { allowed: 0, blocked: 0, ignored: 0, causes: {} };
  for (const m of tail.matchAll(/\[weather\] rain start in \S+ \(cause (\w+), season \w+\): (\w+)/g)) {
    counts[m[2]] = (counts[m[2]] || 0) + 1;
    counts.causes[m[1]] = (counts.causes[m[1]] || 0) + 1;
  }
  return counts;
}

/** Runs '/tick sprint' and waits until the server reports the sprint as finished. */
async function sprint(rcon, ticks) {
  const before = logSize();
  await rcon.command(`tick sprint ${ticks}`);
  for (let i = 0; i < 600; i++) {
    await sleep(1000);
    const tail = fs.readFileSync(CONSOLE_LOG, 'utf8').slice(before);
    if (/Sprint completed/i.test(tail)) return tail.match(/Sprint completed[^\n]*/i)[0];
  }
  throw new Error('sprint did not complete in 10 minutes');
}

const countOf = (output) => Number((output.match(/count: (\d+)/i) || [0, 0])[1]);

async function main() {
  const rcon = await new Rcon(HOST, Number(props['rcon.port']), props['rcon.password']).connect();
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: BOT, auth: 'offline', version: process.env.MC_VERSION || '1.21.11' });
  let rainStarts = 0;
  bot.on('rain', () => {
    if (bot.isRaining) rainStarts++;
  });
  await new Promise((resolve, reject) => {
    bot.once('spawn', resolve);
    setTimeout(() => reject(new Error('bot spawn timeout')), 60000);
  });
  await sleep(1500);

  try {
    await rcon.command(`gamemode creative ${BOT}`); // mobs ignore creative players
    await rcon.command('gamerule advance_time false');
    await rcon.command('gamerule advance_weather true');
    await rcon.command('gamerule spawn_mobs false');
    // Other suites may have changed biomes nearby (deserts stay HOT even in winter): move to untouched plains
    const here = bot.entity.position.floored();
    const fresh = { x: here.x + 1000, y: here.y, z: here.z };
    await rcon.command(`tp ${BOT} ${fresh.x + 0.5} ${fresh.y} ${fresh.z + 0.5}`);
    for (let i = 0; i < 60 && bot.entity.position.distanceTo({ x: fresh.x + 0.5, y: fresh.y, z: fresh.z + 0.5 }) > 2; i++) await sleep(500);
    for (let i = 0; i < 60 && !bot.blockAt(bot.entity.position.offset(0, -1, 0)); i++) await sleep(500);
    await sleep(2000);
    await rcon.command('kill @e[type=!player]');

    // ---------------------------------------------------------------- natural rain
    // weather.debug logs every rain start with its cause and decision: the server log is the source of truth
    await setCoreConfig(rcon, /(\n\s*debug: )\w+/, '$1true');
    await setCoreConfig(rcon, /(rain-chance:\s*\n\s*spring: )[\d.]+/, '$11.0');
    await rcon.command('season set spring world');
    await rcon.command('weather clear');
    rainStarts = 0;
    let logMark = logSize();
    let summary = await sprint(rcon, 1200000);
    const springRains = rainStarts;
    let decisions = weatherDecisions(logMark);
    check('Spring (rain-chance 1.0) lets natural rain start while fast-forwarding',
      springRains > 0 && decisions.allowed > 0 && decisions.blocked === 0,
      `bot rain starts=${springRains}; log ${JSON.stringify(decisions)}; ${summary}`);

    await setCoreConfig(rcon, /(rain-chance:\s*\n\s*spring: [\d.]+\s*\n\s*summer: )[\d.]+/, '$10.0');
    await rcon.command('season set summer world');
    await rcon.command('weather clear');
    // Rain fades out over several seconds and the client keeps receiving rain-level updates meanwhile
    for (let i = 0; i < 60 && bot.isRaining; i++) await sleep(500);
    await sleep(1000);
    rainStarts = 0;
    logMark = logSize();
    summary = await sprint(rcon, 1200000);
    decisions = weatherDecisions(logMark);
    check('Summer (rain-chance 0.0) blocks every natural rain start',
      rainStarts === 0 && decisions.allowed === 0 && decisions.blocked > 0,
      `bot rain starts=${rainStarts}; log ${JSON.stringify(decisions)}; ${summary}`);
    const tick = await rcon.command('tick query');
    const mspt = Number(((tick.match(/Average time per tick: ([\d.,]+)ms/) || [0, '99'])[1]).replace(',', '.'));
    check('Server stays healthy after blocking rain (no event loop)', mspt < 10, `average ${mspt} ms per tick`);
    await setCoreConfig(rcon, /(rain-chance:\s*\n\s*spring: [\d.]+\s*\n\s*summer: )[\d.]+/, '$10.5');
    await setCoreConfig(rcon, /(\n\s*debug: )\w+/, '$1false');

    // ---------------------------------------------------------------- natural mob variants
    // Vanilla already spawns strays in snowy biomes and husks in deserts, so the checks use biomes where
    // vanilla never does: plains in winter (~3 °C, band COLD) and savanna in summer (band HOT).
    await setCoreConfig(rcon, /winter-stray-chance: [\d.]+/, 'winter-stray-chance: 1.0');
    await setCoreConfig(rcon, /summer-husk-chance: [\d.]+/, 'summer-husk-chance: 1.0');
    const base = bot.entity.position.floored();
    const R = 80;
    const inArea = (type) => `@e[type=${type},x=${base.x - R},y=-64,z=${base.z - R},dx=${2 * R},dy=24,dz=${2 * R}]`;
    await rcon.command('time set 18000'); // midnight, so monsters can spawn on the surface

    await rcon.command('season set winter world');
    await rcon.command('kill @e[type=!player]');
    await rcon.command('gamerule spawn_mobs true');
    await sprint(rcon, 6000);
    await rcon.command('gamerule spawn_mobs false');
    const strays = countOf(await rcon.command(`execute if entity ${inArea('stray')}`));
    const skeletons = countOf(await rcon.command(`execute if entity ${inArea('skeleton')}`));
    check('Winter in plains (cold) turns natural skeletons into strays', strays > 0 && skeletons === 0, `strays=${strays} skeletons=${skeletons}`);

    await rcon.command('season set summer world');
    // fillbiome is limited to 32768 blocks per call, so the area around the bot is filled in tiles
    for (let x = base.x - R; x <= base.x + R; x += 33) {
      for (let z = base.z - R; z <= base.z + R; z += 33) {
        await rcon.command(`fillbiome ${x} -64 ${z} ${Math.min(x + 32, base.x + R)} -56 ${Math.min(z + 32, base.z + R)} minecraft:savanna`);
      }
    }
    await rcon.command('kill @e[type=!player]');
    await rcon.command('gamerule spawn_mobs true');
    await sprint(rcon, 6000);
    await rcon.command('gamerule spawn_mobs false');
    const husks = countOf(await rcon.command(`execute if entity ${inArea('husk')}`));
    const zombies = countOf(await rcon.command(`execute if entity ${inArea('zombie')}`));
    check('Summer in savanna (hot) turns natural zombies into husks', husks > 0 && zombies === 0, `husks=${husks} zombies=${zombies}`);
    await rcon.command('kill @e[type=!player]');

    await setCoreConfig(rcon, /winter-stray-chance: [\d.]+/, 'winter-stray-chance: 0.5');
    await setCoreConfig(rcon, /summer-husk-chance: [\d.]+/, 'summer-husk-chance: 0.5');
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
