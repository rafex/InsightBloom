package dev.rafex.insightbloom.toolsgateway;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.websocket.server.WebSocketUpgradeHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Punto de entrada del gateway de herramientas: unico servicio publico delante de
 * drawio/Excalidraw/Etherpad, exige sesion valida de InsightBloom antes de reenviar cualquier
 * request al pod real. Ver {@link AuthGateHandler}.
 */
public final class GatewayApplication {

    /** Ver comentario en el armado de {@code wsHandler} en {@link #main}. */
    static final long WS_MAX_MESSAGE_SIZE = 20L * 1024 * 1024;

    private GatewayApplication() {
    }

    public static void main(final String[] args) throws Exception {
        System.setProperty("jdk.httpclient.keepalive.timeout", "180");
        final int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8090"));
        final String authValidateUrl = System.getenv().getOrDefault(
                "AUTH_VALIDATE_URL", "http://insightbloom-users:8081/api/v1/auth/validate");
        final String loginUrl = System.getenv().getOrDefault(
                "GATEWAY_LOGIN_URL", "https://insightbloom.v1.rafex.cloud/login");
        final Map<String, String> routes = parseRoutes(System.getenv().getOrDefault("GATEWAY_ROUTES", ""));
        // Fase 3b del IDE: ruteo dinamico por-sesion hacia el sandbox de cada usuario — null si
        // GATEWAY_IDE_HOST no esta configurado (el gateway sigue funcionando igual sin esto,
        // solo con los targets estaticos de GATEWAY_ROUTES).
        final String ideHost = System.getenv("GATEWAY_IDE_HOST");
        final String sandboxResolveUrl = System.getenv().getOrDefault(
                "GATEWAY_SANDBOX_RESOLVE_URL", "http://insightbloom-users:8081/internal/sandbox-target");
        final String internalApiKey = System.getenv("INTERNAL_API_KEY");

        final Server server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        // Diagnostico 2026-07-19: el idle timeout de WebSocketUpgradeHandler.container (mas
        // abajo) y del WebSocketClient/HttpClient del proxy saliente (ver
        // LoggingWebSocketProxyEndpoint) SOLO gobiernan la capa de sesion WebSocket -- el
        // ServerConnector es quien realmente posee el EndPoint TCP crudo de cada conexion
        // aceptada, y nunca tuvo su propio idle timeout seteado (default de Jetty: 30s). Probado
        // en vivo conectando directo al pod del sandbox (bypaseando este gateway por completo,
        // ver kubectl port-forward + cliente websocket crudo): la conexion aguantaba 90s+ sin
        // cortes, descartando a ttyd/K8s como causa -- confirma que el corte pasaba en esta capa.
        connector.setIdleTimeout(java.time.Duration.ofMinutes(10).toMillis());
        server.addConnector(connector);

        final AuthGateHandler authGateHandler = new AuthGateHandler(
                routes, authValidateUrl, loginUrl, ideHost, sandboxResolveUrl, internalApiKey);
        // El upgrade de WebSocket (Etherpad socket.io, code-server) se intercepta antes que
        // el AuthGateHandler HTTP; las requests que no son upgrade caen al AuthGateHandler
        // sin cambios (WebSocketUpgradeHandler.handle delega al wrapped Handler si no aplica).
        // maxBinaryMessageSize: el limite por defecto de Jetty (64KB) es insuficiente para el
        // canal de management de code-server -- confirmado en vivo (2026-07-17) que cerraba con
        // 1009 "Binary message too large: 69,622 > 65,536" en loop constante de reconexion.
        final WebSocketUpgradeHandler wsHandler = WebSocketUpgradeHandler.from(server, container -> {
            container.setMaxBinaryMessageSize(WS_MAX_MESSAGE_SIZE);
            // Default de Jetty es 30s -- insuficiente para una terminal interactiva (ttyd/code-server)
            // donde el usuario puede pasar minutos sin escribir nada; confirmado en vivo (2026-07-19)
            // que el idle timeout de 30s tumbaba la sesion en loop constante de reconexion, dejando
            // la terminal visible pero no operable.
            container.setIdleTimeout(java.time.Duration.ofMinutes(10));
            container.addMapping("/*", new WebSocketProxyCreator(routes, authGateHandler));
        });
        wsHandler.setHandler(authGateHandler);
        server.setHandler(wsHandler);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }));

        server.start();
        server.join();
    }

    /** Formato: "host1=http://target1:port,host2=http://target2:port". */
    private static Map<String, String> parseRoutes(final String raw) {
        final Map<String, String> routes = new HashMap<>();
        for (final String pair : raw.split(",")) {
            if (pair.isBlank()) continue;
            final int eq = pair.indexOf('=');
            if (eq < 0) continue;
            routes.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return routes;
    }
}
