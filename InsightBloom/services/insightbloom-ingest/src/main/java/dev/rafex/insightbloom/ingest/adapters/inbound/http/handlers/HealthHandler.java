package dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.nio.ByteBuffer;
public class HealthHandler extends BaseHandler {
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        res.setStatus(200); res.getHeaders().put("Content-Type","application/json");
        res.write(true, ByteBuffer.wrap("{\"status\":\"ok\"}".getBytes()), cb);
        return true;
    }
}
