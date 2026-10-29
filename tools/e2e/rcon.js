/*
 * Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
 * Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

'use strict';

// Minimal Source RCON client (the protocol used by Minecraft servers), with no external dependencies.
const net = require('net');

const TYPE_AUTH = 3;
const TYPE_COMMAND = 2;

class Rcon {
  constructor(host, port, password) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.nextId = 1;
    this.pending = new Map();
    this.buffer = Buffer.alloc(0);
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.socket = net.createConnection({ host: this.host, port: this.port }, async () => {
        try {
          await this.send(this.password, TYPE_AUTH);
          resolve(this);
        } catch (e) {
          reject(e);
        }
      });
      this.socket.on('data', (chunk) => this.onData(chunk));
      this.socket.on('error', reject);
    });
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 4) {
      const length = this.buffer.readInt32LE(0);
      if (this.buffer.length < 4 + length) return;
      const id = this.buffer.readInt32LE(4);
      const body = this.buffer.toString('utf8', 12, 4 + length - 2);
      this.buffer = this.buffer.subarray(4 + length);
      const waiter = this.pending.get(id);
      if (waiter) {
        this.pending.delete(id);
        waiter.resolve(body);
      } else if (id === -1) {
        for (const [, w] of this.pending) w.reject(new Error('RCON authentication failed'));
        this.pending.clear();
      }
    }
  }

  send(body, type = TYPE_COMMAND) {
    const id = this.nextId++;
    const payload = Buffer.from(body, 'utf8');
    const packet = Buffer.alloc(14 + payload.length);
    packet.writeInt32LE(10 + payload.length, 0);
    packet.writeInt32LE(id, 4);
    packet.writeInt32LE(type, 8);
    payload.copy(packet, 12);
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.socket.write(packet);
      setTimeout(() => {
        if (this.pending.delete(id)) reject(new Error(`RCON timeout: ${body}`));
      }, 10000);
    });
  }

  /** Runs a console command and returns its output without Minecraft colour codes. */
  async command(cmd) {
    const out = await this.send(cmd, TYPE_COMMAND);
    return out.replace(/§./g, '');
  }

  close() {
    if (this.socket) this.socket.end();
  }
}

module.exports = { Rcon };
