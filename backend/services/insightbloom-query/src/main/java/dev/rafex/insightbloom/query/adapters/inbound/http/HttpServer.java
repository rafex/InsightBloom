package dev.rafex.insightbloom.query.adapters.inbound.http;

import dev.rafex.insightbloom.query.adapters.inbound.http.handlers.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

public class HttpServer {
    private final int port;
    private final CloudHandler cloudHandler;
    private final TimelineHandler timelineHandler;
    private final UpdateHandler updateHandler;
    private final VisibilityHandler visibilityHandler;
    private final MessageVisibilityHandler messageVisibilityHandler;
    private final HealthHandler healthHandler;
    private Server server;

    public HttpServer(int port, CloudHandler cloudHandler, TimelineHandler timelineHandler,
                      UpdateHandler updateHandler, VisibilityHandler visibilityHandler,
                      MessageVisibilityHandler messageVisibilityHandler, HealthHandler healthHandler) {
        this.port = port; this.cloudHandler = cloudHandler; this.timelineHandler = timelineHandler;
        this.updateHandler = updateHandler; this.visibilityHandler = visibilityHandler;
        this.messageVisibilityHandler = messageVisibilityHandler; this.healthHandler = healthHandler;
    }

    public void start() throws Exception {
        server = new Server(port);
        CloudHandler ch = cloudHandler;
        TimelineHandler th = timelineHandler;
        UpdateHandler uh = updateHandler;
        VisibilityHandler vh = visibilityHandler;
        MessageVisibilityHandler mvh = messageVisibilityHandler;
        HealthHandler hh = healthHandler;
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                String path = request.getHttpURI().getPath();
                if (path.contains("/cloud/")) return ch.handle(request, response, callback);
                if (path.contains("/words/") && path.endsWith("/timeline")) return th.handle(request, response, callback);
                if (path.startsWith("/internal/visibility")) return vh.handle(request, response, callback);
                if (path.startsWith("/internal/message-visibility")) return mvh.handle(request, response, callback);
                if (path.startsWith("/internal/update")) return uh.handle(request, response, callback);
                return hh.handle(request, response, callback);
            }
        });
        server.start();
        System.out.println("[query] Started on port " + port);
    }

    public void stop() throws Exception { if (server != null) server.stop(); }
    public void join() throws InterruptedException { if (server != null) server.join(); }
}
