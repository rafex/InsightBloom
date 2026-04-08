package dev.rafex.insightbloom.users.adapters.inbound.http;

import dev.rafex.insightbloom.users.adapters.inbound.http.handlers.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class HttpServer {
    private final int port;
    private final AuthHandler authHandler;
    private final ConferenceHandler conferenceHandler;
    private final HealthHandler healthHandler;
    private Server server;

    public HttpServer(int port, AuthHandler authHandler, ConferenceHandler conferenceHandler, HealthHandler healthHandler) {
        this.port = port;
        this.authHandler = authHandler;
        this.conferenceHandler = conferenceHandler;
        this.healthHandler = healthHandler;
    }

    public void start() throws Exception {
        server = new Server(port);
        AuthHandler auth = authHandler;
        ConferenceHandler conf = conferenceHandler;
        HealthHandler health = healthHandler;
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String path = request.getHttpURI().getPath();
                if (path.startsWith("/api/v1/auth/")) return auth.handle(request, response, callback);
                if (path.startsWith("/api/v1/conferences")) return conf.handle(request, response, callback);
                return health.handle(request, response, callback);
            }
        });
        server.start();
        System.out.println("[users] Started on port " + port);
    }

    public void stop() throws Exception {
        if (server != null) server.stop();
    }

    public void join() throws InterruptedException {
        if (server != null) server.join();
    }
}
