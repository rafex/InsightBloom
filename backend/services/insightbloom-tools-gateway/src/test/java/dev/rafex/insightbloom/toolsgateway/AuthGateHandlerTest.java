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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class AuthGateHandlerTest {

    static {
        // El cliente HTTP del JDK bloquea setear el header "Host" por defecto (restricted
        // header) — necesario aqui para simular distintos hosts virtuales contra un solo
        // gateway en localhost, tal como hace HAProxy/el Ingress real via el header Host.
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
    }

    private HttpServer upstream;
    private HttpServer authServer;
    private Server gateway;
    private int gatewayPort;
    private volatile String lastAuthHeaderSeen;
    private volatile String lastUpstreamAcceptEncoding;
    private volatile boolean authServerAccepts = true;

    @BeforeEach
    void setUp() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        upstream.createContext("/", exchange -> {
            lastUpstreamAcceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
            final byte[] body = "hello from upstream".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();

        authServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        authServer.createContext("/validate", exchange -> {
            lastAuthHeaderSeen = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.sendResponseHeaders(authServerAccepts ? 200 : 401, -1);
            exchange.close();
        });
        authServer.start();

        final Map<String, String> routes = Map.of(
                "toolhost", "http://localhost:" + upstream.getAddress().getPort());
        final AuthGateHandler handler = new AuthGateHandler(
                routes,
                "http://localhost:" + authServer.getAddress().getPort() + "/validate",
                "http://login.example/login");

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
        authServer.stop(0);
    }

    private HttpResponse<String> request(final String path, final String hostHeader, final String cookie)
            throws Exception {
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path))
                .header("Host", hostHeader)
                .GET();
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void unknownHost_returns502() throws Exception {
        final var resp = request("/index.html", "not-a-registered-host", null);
        assertEquals(502, resp.statusCode());
    }

    @Test
    void noTokenNoCookie_returns401WithLoginPage() throws Exception {
        final var resp = request("/index.html", "toolhost", null);
        assertEquals(401, resp.statusCode());
        assertTrue(resp.body().contains("login.example"));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        authServerAccepts = false;
        final var resp = request("/index.html?ib_token=bad-token", "toolhost", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void validToken_proxiesRequestAndSetsSessionCookie() throws Exception {
        final var resp = request("/index.html?ib_token=good-token", "toolhost", null);
        assertEquals(200, resp.statusCode());
        assertEquals("hello from upstream", resp.body());
        assertEquals("Bearer good-token", lastAuthHeaderSeen);
        assertEquals("identity", lastUpstreamAcceptEncoding);

        final Optional<String> setCookie = resp.headers().firstValue("Set-Cookie");
        assertTrue(setCookie.isPresent());
        assertTrue(setCookie.get().startsWith("ib_gw="));
    }

    @Test
    void sessionCookie_allowsFollowUpRequestsWithoutToken() throws Exception {
        final var first = request("/index.html?ib_token=good-token", "toolhost", null);
        final String setCookie = first.headers().firstValue("Set-Cookie").orElseThrow();
        final Matcher matcher = Pattern.compile("ib_gw=([^;]+)").matcher(setCookie);
        assertTrue(matcher.find());
        final String cookieValue = "ib_gw=" + matcher.group(1);

        final var second = request("/other.html", "toolhost", cookieValue);
        assertEquals(200, second.statusCode());
        assertEquals("hello from upstream", second.body());
    }
}
