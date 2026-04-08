package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

public abstract class BaseHandler extends Handler.Abstract {
    protected final ObjectMapper mapper;

    protected BaseHandler() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    protected String requestId() {
        return UUID.randomUUID().toString();
    }

    protected <T> void writeJson(Response response, Callback callback, int status, T body) throws IOException {
        String json = mapper.writeValueAsString(body);
        response.setStatus(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.write(true, ByteBuffer.wrap(json.getBytes()), callback);
    }

    protected <T> void ok(Response response, Callback callback, T data) throws IOException {
        String reqId = requestId();
        writeJson(response, callback, 200, new ApiResponse<>(data, ApiMeta.of(reqId)));
    }

    protected <T> void created(Response response, Callback callback, T data) throws IOException {
        String reqId = requestId();
        writeJson(response, callback, 201, new ApiResponse<>(data, ApiMeta.of(reqId)));
    }

    protected void error(Response response, Callback callback, int status, String code, String message) throws IOException {
        writeJson(response, callback, status, ApiError.of(code, message, requestId()));
    }

    protected <T> T readBody(Request request, Class<T> type) throws IOException {
        try (InputStream is = Request.asInputStream(request)) {
            return mapper.readValue(is, type);
        }
    }

    protected String pathSegment(Request request, int index) {
        String path = request.getHttpURI().getPath();
        String[] parts = path.split("/");
        if (index < parts.length) return parts[index];
        return null;
    }
}
