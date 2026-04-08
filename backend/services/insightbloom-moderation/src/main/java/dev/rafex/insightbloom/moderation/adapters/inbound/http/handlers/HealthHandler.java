package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.nio.ByteBuffer;
public class HealthHandler extends BaseHandler {
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        response.setStatus(200);
        response.getHeaders().put("Content-Type","application/json");
        response.write(true, ByteBuffer.wrap("{\"status\":\"ok\"}".getBytes()), callback);
        return true;
    }
}
