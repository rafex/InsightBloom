package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
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
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    protected String requestId() { return UUID.randomUUID().toString(); }
    protected <T> void writeJson(Response res, Callback cb, int status, T body) throws IOException {
        String json = mapper.writeValueAsString(body);
        res.setStatus(status); res.getHeaders().put("Content-Type","application/json");
        res.write(true, ByteBuffer.wrap(json.getBytes()), cb);
    }
    protected <T> void ok(Response res, Callback cb, T data) throws IOException {
        writeJson(res,cb,200,new ApiResponse<>(data, ApiMeta.of(requestId())));
    }
    protected void error(Response res, Callback cb, int status, String code, String msg) throws IOException {
        writeJson(res,cb,status,ApiError.of(code,msg,requestId()));
    }
    protected <T> T readBody(Request req, Class<T> type) throws IOException {
        try (InputStream is = Request.asInputStream(req)) { return mapper.readValue(is,type); }
    }
}
