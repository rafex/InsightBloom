package dev.rafex.insightbloom.moderation.adapters.inbound.http;

import dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class HttpServer {
    private final int port;
    private final ModerationWordHandler wordHandler;
    private final ModerationMessageHandler messageHandler;
    private final InternalEvaluateHandler evaluateHandler;
    private final HealthHandler healthHandler;
    private Server server;

    public HttpServer(int port, ModerationWordHandler wordHandler, ModerationMessageHandler messageHandler,
                      InternalEvaluateHandler evaluateHandler, HealthHandler healthHandler) {
        this.port = port; this.wordHandler = wordHandler; this.messageHandler = messageHandler;
        this.evaluateHandler = evaluateHandler; this.healthHandler = healthHandler;
    }

    public void start() throws Exception {
        server = new Server(port);
        ModerationWordHandler wh = wordHandler;
        ModerationMessageHandler mh = messageHandler;
        InternalEvaluateHandler eh = evaluateHandler;
        HealthHandler hh = healthHandler;
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String path = request.getHttpURI().getPath();
                if (path.contains("/moderation/words")) return wh.handle(request, response, callback);
                if (path.contains("/moderation/messages")) return mh.handle(request, response, callback);
                if (path.startsWith("/internal/evaluate")) return eh.handle(request, response, callback);
                return hh.handle(request, response, callback);
            }
        });
        server.start();
        System.out.println("[moderation] Started on port " + port);
    }

    public void stop() throws Exception { if (server != null) server.stop(); }
    public void join() throws InterruptedException { if (server != null) server.join(); }
}
