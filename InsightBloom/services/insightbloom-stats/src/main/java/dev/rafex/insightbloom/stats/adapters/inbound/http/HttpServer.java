package dev.rafex.insightbloom.stats.adapters.inbound.http;

import dev.rafex.insightbloom.stats.adapters.inbound.http.handlers.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class HttpServer {
    private final int port;
    private final RecalcHandler recalcHandler;
    private final StatsHandler statsHandler;
    private final HealthHandler healthHandler;
    private Server server;

    public HttpServer(int port, RecalcHandler recalcHandler, StatsHandler statsHandler, HealthHandler healthHandler) {
        this.port = port; this.recalcHandler = recalcHandler; this.statsHandler = statsHandler; this.healthHandler = healthHandler;
    }

    public void start() throws Exception {
        server = new Server(port);
        RecalcHandler rh = recalcHandler;
        StatsHandler sh = statsHandler;
        HealthHandler hh = healthHandler;
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String path = request.getHttpURI().getPath();
                if (path.startsWith("/internal/recalc")) return rh.handle(request, response, callback);
                if (path.startsWith("/api/v1/conferences")) return sh.handle(request, response, callback);
                return hh.handle(request, response, callback);
            }
        });
        server.start();
        System.out.println("[stats] Started on port " + port);
    }

    public void stop() throws Exception { if (server != null) server.stop(); }
    public void join() throws InterruptedException { if (server != null) server.join(); }
}
