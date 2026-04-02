package dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.rafex.insightbloom.contracts.ApiError;
import dev.rafex.insightbloom.contracts.ApiMeta;
import dev.rafex.insightbloom.contracts.ApiResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;
public abstract class BaseHandler extends Handler.Abstract {
    protected final ObjectMapper mapper;
    protected BaseHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    protected String requestId() { return UUID.randomUUID().toString(); }
    protected <T> void writeJson(Response response, Callback callback, int status, T body) throws IOException {
        String json = mapper.writeValueAsString(body);
        response.setStatus(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.write(true, ByteBuffer.wrap(json.getBytes()), callback);
    }
    protected <T> void ok(Response response, Callback callback, T data) throws IOException {
        writeJson(response, callback, 200, new ApiResponse<>(data, ApiMeta.of(requestId())));
    }
    protected <T> void created(Response response, Callback callback, T data) throws IOException {
        writeJson(response, callback, 201, new ApiResponse<>(data, ApiMeta.of(requestId())));
    }
    protected void okPaged(Response response, Callback callback, Object data, int page, int pageSize, long total) throws IOException {
        writeJson(response, callback, 200, new ApiResponse<>(data, ApiMeta.paged(requestId(), page, pageSize, total)));
    }
    protected void error(Response response, Callback callback, int status, String code, String message) throws IOException {
        writeJson(response, callback, status, ApiError.of(code, message, requestId()));
    }
    protected <T> T readBody(Request request, Class<T> type) throws IOException {
        try (InputStream is = Request.asInputStream(request)) { return mapper.readValue(is, type); }
    }
    protected int queryParam(Request request, String name, int def) {
        String v = request.getHttpURI().getQuery();
        if (v == null) return def;
        for (String part : v.split("&")) {
            String[] kv = part.split("=");
            if (kv.length == 2 && name.equals(kv[0])) return Integer.parseInt(kv[1]);
        }
        return def;
    }
}
