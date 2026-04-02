package dev.rafex.insightbloom.ingest.adapters.inbound.http;

import dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class HttpServer {
    private final int port;
    private final IngestHandler ingestHandler;
    private final HealthHandler healthHandler;
    private Server server;

    public HttpServer(int port, IngestHandler ingestHandler, HealthHandler healthHandler) {
        this.port = port; this.ingestHandler = ingestHandler; this.healthHandler = healthHandler;
    }

    public void start() throws Exception {
        server = new Server(port);
        IngestHandler ih = ingestHandler;
        HealthHandler hh = healthHandler;
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String path = request.getHttpURI().getPath();
                if (path.startsWith("/api/v1/messages") || path.startsWith("/api/v1/webhooks/")) {
                    return ih.handle(request, response, callback);
                }
                return hh.handle(request, response, callback);
            }
        });
        server.start();
        System.out.println("[ingest] Started on port " + port);
    }

    public void stop() throws Exception { if (server != null) server.stop(); }
    public void join() throws InterruptedException { if (server != null) server.join(); }
}
