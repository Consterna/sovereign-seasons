/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// Makes random mechanics deterministic on the local test server only, then reloads both plugins.
// Usage (server running, plugin configs already generated): node apply-test-config.js [path/to/testserver]
const fs = require('fs');
const path = require('path');
const { Rcon } = require('./rcon');

const serverDir = process.argv[2] || path.join(__dirname, '..', '..', 'testserver');
const overrides = {
  'plugins/SovereignSeasons-Core/config.yml': [
    [/spring-twin-chance: [\d.]+/, 'spring-twin-chance: 1.0'],
  ],
  'plugins/SovereignSeasons-Pantry/config.yml': [
    [/rotten-flesh-chance: [\d.]+/, 'rotten-flesh-chance: 1.0'],
    [/player-check-interval-ticks: \d+/, 'player-check-interval-ticks: 100'],
  ],
};

for (const [file, rules] of Object.entries(overrides)) {
  const full = path.join(serverDir, file);
  let text = fs.readFileSync(full, 'utf8').replace(/\r\n/g, '\n');
  for (const [pattern, replacement] of rules) {
    if (!pattern.test(text)) throw new Error(`pattern ${pattern} not found in ${file}`);
    text = text.replace(pattern, replacement);
  }
  fs.writeFileSync(full, text, 'utf8');
  console.log(`patched ${file}`);
}

const props = Object.fromEntries(
  fs.readFileSync(path.join(serverDir, 'server.properties'), 'utf8').split(/\r?\n/).filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)])
);

(async () => {
  const rcon = await new Rcon(props['server-ip'] || '127.0.0.1', Number(props['rcon.port']), props['rcon.password']).connect();
  console.log(await rcon.command('season reload'));
  console.log(await rcon.command('pantry reload'));
  rcon.close();
})().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
