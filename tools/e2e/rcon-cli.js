/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// Usage: node rcon-cli.js <server.properties> <command...>
const fs = require('fs');
const { Rcon } = require('./rcon');

const [propsPath, ...words] = process.argv.slice(2);
const props = Object.fromEntries(
  fs.readFileSync(propsPath, 'utf8').split(/\r?\n/).filter((l) => l.includes('='))
    .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)])
);

(async () => {
  const rcon = await new Rcon(props['server-ip'] || '127.0.0.1', Number(props['rcon.port']), props['rcon.password']).connect();
  console.log(await rcon.command(words.join(' ')));
  rcon.close();
})().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
