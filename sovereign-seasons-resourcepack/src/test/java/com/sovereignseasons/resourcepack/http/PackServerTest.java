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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PackServerTest {
    private static final byte[] ZIP = {'P', 'K', 3, 4, 1, 2, 3};
    private PackServer server;

    @BeforeEach
    void start() throws Exception {
        server = new PackServer(null);
        server.publish("/sovereignseasons/sovereign-seasons-base.zip", ZIP);
        server.start("127.0.0.1", 0);
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + server.getPort() + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return connection;
    }

    private static InputStream ascii(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("Serves a published pack with its length and type, also to HEAD requests and with a query string")
    void servesPublishedFiles() throws Exception {
        HttpURLConnection get = open("/sovereignseasons/sovereign-seasons-base.zip", "GET");
        assertThat(get.getResponseCode()).isEqualTo(200);
        assertThat(get.getContentType()).isEqualTo("application/zip");
        assertThat(get.getContentLengthLong()).isEqualTo(ZIP.length);
        try (InputStream body = get.getInputStream()) {
            assertThat(body.readAllBytes()).isEqualTo(ZIP);
        }
        HttpURLConnection head = open("/sovereignseasons/sovereign-seasons-base.zip", "HEAD");
        assertThat(head.getResponseCode()).isEqualTo(200);
        assertThat(head.getContentLengthLong()).isEqualTo(ZIP.length);
        assertThat(open("/sovereignseasons/sovereign-seasons-base.zip?v=2", "GET").getResponseCode()).isEqualTo(200);
        assertThat(server.getServedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Anything else is refused: unknown paths, traversal, other methods and malformed requests")
    void refusesEverythingElse() throws Exception {
        assertThat(open("/", "GET").getResponseCode()).isEqualTo(404);
        assertThat(open("/sovereignseasons/", "GET").getResponseCode()).isEqualTo(404);
        assertThat(open("/sovereignseasons/../config.yml", "GET").getResponseCode()).isEqualTo(404);
        HttpURLConnection post = open("/sovereignseasons/sovereign-seasons-base.zip", "POST");
        post.setDoOutput(true);
        try (OutputStream out = post.getOutputStream()) {
            out.write(1);
        }
        assertThat(post.getResponseCode()).isEqualTo(405);
        assertThat(raw("NONSENSE\r\n\r\n")).startsWith("HTTP/1.1 400");
        assertThat(server.getServedCount()).isZero();
    }

    @Test
    @DisplayName("Request heads are size limited and parsed exactly")
    void readsRequestHeads() throws Exception {
        assertThat(PackServer.readRequestHead(ascii("GET /x HTTP/1.1\r\nHost: a\r\n\r\n"))).isEqualTo("GET /x HTTP/1.1");
        assertThat(PackServer.readRequestHead(ascii("GET /x HTTP/1.1\r\n"))).isNull();
        assertThat(PackServer.readRequestHead(ascii("GET /x HTTP/1.1\r\nX: " + "a".repeat(10_000) + "\r\n\r\n"))).isNull();
    }

    @Test
    @DisplayName("Tolerant parsing: bare LF line ends, blank lines before the request and absolute-form targets")
    void tolerantParsing() throws Exception {
        assertThat(PackServer.readRequestHead(ascii("GET /x HTTP/1.1\nHost: a\n\n"))).isEqualTo("GET /x HTTP/1.1");
        assertThat(PackServer.readRequestHead(ascii("\r\n\r\nGET /y HTTP/1.1\r\n\r\n"))).isEqualTo("GET /y HTTP/1.1");
        assertThat(PackServer.requestPath("http://play.example.com:8163/sovereignseasons/a.zip?v=1")).isEqualTo("/sovereignseasons/a.zip");
        assertThat(PackServer.requestPath("https://h")).isEqualTo("/");
        assertThat(PackServer.requestPath("http://bad host/")).isNull();
        assertThat(raw("GET http://127.0.0.1/sovereignseasons/sovereign-seasons-base.zip HTTP/1.1\nHost: x\n\n")).startsWith("HTTP/1.1 200");
    }

    @Test
    @DisplayName("A client that never finishes its request is cut off by the connection lifetime limit")
    void slowClientsAreCutOff() throws Exception {
        PackServer strict = new PackServer(null, 400L);
        strict.publish("/a", ZIP);
        strict.start("127.0.0.1", 0);
        try (Socket slow = new Socket("127.0.0.1", strict.getPort())) {
            slow.setSoTimeout(5000);
            slow.getOutputStream().write("GET /a HTTP/1.1\r\nX: ".getBytes(StandardCharsets.US_ASCII));
            slow.getOutputStream().flush();
            long start = System.nanoTime();
            // The server closes the connection on its own, well before the 10 s read timeout
            assertThat(slow.getInputStream().read()).isEqualTo(-1);
            assertThat((System.nanoTime() - start) / 1_000_000L).isLessThan(5000L);
            for (int i = 0; i < 50 && strict.getOpenConnections() > 0; i++) {
                Thread.sleep(20);
            }
            assertThat(strict.getOpenConnections()).isZero();
        } finally {
            strict.stop();
        }
    }

    @Test
    @DisplayName("Stopping the server also closes the connections still open")
    void stopClosesOpenConnections() throws Exception {
        try (Socket idle = new Socket("127.0.0.1", server.getPort())) {
            idle.setSoTimeout(5000);
            for (int i = 0; i < 50 && server.getOpenConnections() == 0; i++) {
                Thread.sleep(20);
            }
            assertThat(server.getOpenConnections()).isEqualTo(1);
            server.stop();
            assertThat(idle.getInputStream().read()).isEqualTo(-1);
            assertThat(server.getOpenConnections()).isZero();
        }
    }

    @Test
    @DisplayName("Stops cleanly and frees the port")
    void stops() throws Exception {
        int port = server.getPort();
        server.stop();
        assertThat(server.getPort()).isEqualTo(-1);
        PackServer again = new PackServer(null);
        again.start("127.0.0.1", port);
        again.stop();
    }

    private String raw(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
        }
    }
}
