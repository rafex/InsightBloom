package dev.rafex.insightbloom.users.adapters.outbound.presentationsclient;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpCertificateRendererTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesTransientRendererFailure() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/v1/certificates/render", exchange -> {
            final int call = calls.incrementAndGet();
            if (call == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            final byte[] pdf = "%PDF-test".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/pdf");
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });
        server.start();

        final byte[] result = new HttpCertificateRenderer(
                "http://localhost:" + server.getAddress().getPort(), "internal-key")
                .render("{}", Map.of("participant.displayName", "Ana Pérez"));

        assertArrayEquals("%PDF-test".getBytes(StandardCharsets.UTF_8), result);
        assertEquals(2, calls.get());
    }
}
