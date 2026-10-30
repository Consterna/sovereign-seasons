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

package com.sovereignseasons.resourcepack.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Minimal HTTP/1.1 server that only serves the resource pack files it was given, from memory.
 * <p>
 * It depends on nothing but {@code java.net}, so it works on any JRE. It answers {@code GET} and {@code HEAD} for
 * exact paths, never lists anything and never touches the file system. Abuse is bounded on every side: a small
 * fixed pool of daemon workers with a bounded queue (connections beyond it are closed at once), a read timeout set
 * as soon as a connection is accepted, a size limit on request heads, and a watchdog that closes any connection
 * older than a fixed lifetime, so slow senders and slow readers cannot hold a worker.
 */
public final class PackServer {
    private static final int MAX_REQUEST_HEAD = 8 * 1024;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final long DEFAULT_MAX_CONNECTION_MS = 60_000L;
    private static final int WORKERS = 4;
    private static final int QUEUE = 32;

    private final Logger logger;
    private final long maxConnectionMillis;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    /** Open client connections and when they were accepted (for the watchdog and for stop()). */
    private final Map<Socket, Long> active = new ConcurrentHashMap<>();
    private final AtomicLong served = new AtomicLong();
    private ServerSocket socket;
    private ThreadPoolExecutor workers;
    private ScheduledExecutorService watchdog;
    private Thread acceptor;

    public PackServer(Logger logger) {
        this(logger, DEFAULT_MAX_CONNECTION_MS);
    }

    PackServer(Logger logger, long maxConnectionMillis) {
        this.logger = logger;
        this.maxConnectionMillis = maxConnectionMillis;
    }

    /**
     * Publishes a file at an exact path such as {@code /sovereignseasons/sovereign-seasons-base.zip}.
     */
    public void publish(String path, byte[] content) {
        files.put(Objects.requireNonNull(path, "path cannot be null"), Objects.requireNonNull(content, "content cannot be null"));
    }

    public synchronized void start(String bindAddress, int port) throws IOException {
        if (socket != null) {
            throw new IllegalStateException("already started");
        }
        InetAddress address = bindAddress == null || bindAddress.isBlank() ? null : InetAddress.getByName(bindAddress.trim());
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(address, port), 50);
        this.socket = server;
        this.workers = new ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE),
                daemon("SovereignSeasons-PackServer-worker"), new ThreadPoolExecutor.AbortPolicy());
        this.watchdog = Executors.newSingleThreadScheduledExecutor(daemon("SovereignSeasons-PackServer-watchdog"));
        long period = Math.max(50L, Math.min(1000L, maxConnectionMillis / 4));
        this.watchdog.scheduleAtFixedRate(this::closeExpired, period, period, TimeUnit.MILLISECONDS);
        this.acceptor = new Thread(this::acceptLoop, "SovereignSeasons-PackServer");
        this.acceptor.setDaemon(true);
        this.acceptor.start();
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Port actually bound (useful when started on port 0), or -1 if not running. */
    public synchronized int getPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    public long getServedCount() {
        return served.get();
    }

    /** Client connections currently open (for tests and diagnostics). */
    public int getOpenConnections() {
        return active.size();
    }

    public synchronized void stop() {
        if (socket != null) {
            closeQuietly(socket);
            socket = null;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
        // Blocking socket reads and writes ignore interrupts: closing the sockets is what frees the workers
        active.keySet().forEach(PackServer::closeQuietly);
        active.clear();
        acceptor = null;
    }

    private void acceptLoop() {
        ServerSocket server;
        synchronized (this) {
            server = socket;
        }
        while (server != null && !server.isClosed()) {
            Socket client;
            try {
                client = server.accept();
            } catch (SocketException e) {
                return; // socket closed by stop()
            } catch (IOException e) {
                if (logger != null) {
                    logger.warning("Resource pack server: " + e.getMessage());
                }
                continue;
            }
            try {
                client.setSoTimeout(READ_TIMEOUT_MS);
                active.put(client, System.currentTimeMillis());
                ThreadPoolExecutor pool;
                synchronized (this) {
                    pool = workers;
                }
                if (pool == null) {
                    release(client);
                    return;
                }
                pool.execute(() -> handle(client));
            } catch (RejectedExecutionException | IOException e) {
                release(client); // queue full or socket already broken
            }
        }
    }

    private void closeExpired() {
        long limit = System.currentTimeMillis() - maxConnectionMillis;
        active.forEach((client, acceptedAt) -> {
            if (acceptedAt < limit) {
                release(client);
            }
        });
    }

    private void release(Socket client) {
        active.remove(client);
        closeQuietly(client);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // closing anyway
        }
    }

    private void handle(Socket client) {
        try {
            String requestLine = readRequestHead(client.getInputStream());
            OutputStream out = client.getOutputStream();
            if (requestLine == null) {
                respond(out, 400, "Bad Request", null, false);
                return;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length != 3 || !parts[2].startsWith("HTTP/")) {
                respond(out, 400, "Bad Request", null, false);
                return;
            }
            String method = parts[0];
            if (!method.equals("GET") && !method.equals("HEAD")) {
                respond(out, 405, "Method Not Allowed", null, false);
                return;
            }
            String path = requestPath(parts[1]);
            byte[] body = path == null ? null : files.get(path);
            if (body == null) {
                respond(out, 404, "Not Found", null, false);
                return;
            }
            respond(out, 200, "OK", body, method.equals("HEAD"));
            served.incrementAndGet();
        } catch (IOException ignored) {
            // client went away, timed out or was closed by the watchdog
        } finally {
            release(client);
        }
    }

    /**
     * Path of a request target: origin-form ({@code /a/b?x}) or absolute-form ({@code http://host/a/b}, which
     * servers must accept), without the query. Null if the target cannot be parsed.
     */
    static String requestPath(String target) {
        String path = target;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                path = URI.create(path).getRawPath();
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (path == null || path.isEmpty()) {
                path = "/";
            }
        }
        int query = path.indexOf('?');
        return query >= 0 ? path.substring(0, query) : path;
    }

    /**
     * Reads the request line and skips the headers, refusing heads bigger than {@link #MAX_REQUEST_HEAD}. Lines
     * may end in CRLF or a bare LF, and blank lines before the request line are ignored (RFC 9112, 2.2).
     */
    static String readRequestHead(InputStream raw) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        StringBuilder line = new StringBuilder();
        String requestLine = null;
        int total = 0;
        while (true) {
            int b = in.read();
            if (b < 0 || ++total > MAX_REQUEST_HEAD) {
                return null;
            }
            if (b != '\n') {
                line.append((char) b);
                continue;
            }
            int length = line.length();
            if (length > 0 && line.charAt(length - 1) == '\r') {
                line.setLength(length - 1);
            }
            String text = line.toString();
            line.setLength(0);
            if (text.isEmpty()) {
                if (requestLine != null) {
                    return requestLine; // end of the headers
                }
                continue; // blank line before the request line
            }
            if (requestLine == null) {
                requestLine = text;
            }
        }
    }

    private static void respond(OutputStream out, int code, String reason, byte[] body, boolean headOnly) throws IOException {
        byte[] content = body != null ? body : (code + " " + reason + "\n").getBytes(StandardCharsets.US_ASCII);
        String type = body != null ? "application/zip" : "text/plain; charset=us-ascii";
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + content.length + "\r\n"
                + (code == 405 ? "Allow: GET, HEAD\r\n" : "")
                + "Cache-Control: no-cache\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        if (!headOnly) {
            out.write(content);
        }
        out.flush();
    }
}
