package dev.rafex.insightbloom.toolsgateway;

import com.sun.net.httpserver.HttpServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AppPreviewGateHandlerTest {
    private static final String VALID_PUBLICATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String VALID_TOKEN = "good-token";

    private HttpServer upstream;
    private HttpServer usersServer;
    private Server gateway;
    private int gatewayPort;
    private volatile String lastForwardedPath;

    @BeforeEach
    void setUp() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/", exchange -> {
            lastForwardedPath = exchange.getRequestURI().getPath();
            final byte[] body = "hello from published app".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();

        usersServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        usersServer.createContext("/internal/app-preview-target", exchange -> {
            final String query = exchange.getRequestURI().getQuery();
            final boolean valid = query != null && query.contains("publicationId=" + VALID_PUBLICATION_ID)
                    && query.contains("token=" + VALID_TOKEN);
            if (!valid) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            final String json = "{\"data\":{\"target\":\"http://localhost:" + upstream.getAddress().getPort() + "\"}}";
            final byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        usersServer.start();

        final AppPreviewGateHandler handler = new AppPreviewGateHandler(
                "http://localhost:" + usersServer.getAddress().getPort() + "/internal/app-preview-target",
                "internal-key");

        gateway = new Server();
        final ServerConnector connector = new ServerConnector(gateway);
        connector.setPort(0);
        gateway.addConnector(connector);
        gateway.setHandler(handler);
        gateway.start();
        gatewayPort = connector.getLocalPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        gateway.stop();
        upstream.stop(0);
        usersServer.stop(0);
    }

    private HttpResponse<String> request(final String path, final String token) throws Exception {
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path)).GET();
        if (token != null) builder.header("X-Preview-Token", token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unknownPath_returns404() throws Exception {
        final var resp = request("/not-a-preview-path", VALID_TOKEN);
        assertEquals(404, resp.statusCode());
    }

    @Test
    void missingToken_returns401() throws Exception {
        final var resp = request("/p/" + VALID_PUBLICATION_ID + "/api/hello", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void invalidToken_returns404() throws Exception {
        final var resp = request("/p/" + VALID_PUBLICATION_ID + "/api/hello", "wrong-token");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void validTokenAndPublication_proxiesToTargetWithPathStripped() throws Exception {
        final var resp = request("/p/" + VALID_PUBLICATION_ID + "/api/hello", VALID_TOKEN);
        assertEquals(200, resp.statusCode());
        assertEquals("hello from published app", resp.body());
        assertEquals("/api/hello", lastForwardedPath);
    }

    @Test
    void rootPathOfPublication_forwardsAsSlash() throws Exception {
        final var resp = request("/p/" + VALID_PUBLICATION_ID, VALID_TOKEN);
        assertEquals(200, resp.statusCode());
        assertEquals("/", lastForwardedPath);
    }
}
