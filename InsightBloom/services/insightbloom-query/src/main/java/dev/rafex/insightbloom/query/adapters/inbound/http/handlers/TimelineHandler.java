package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.query.application.usecases.GetTimelineUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
public class TimelineHandler extends BaseHandler {
    private final GetTimelineUseCase useCase;
    public TimelineHandler(GetTimelineUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"GET".equals(req.getMethod())) { error(res,cb,405,"method_not_allowed","GET required"); return true; }
        String path = req.getHttpURI().getPath();
        // /api/v1/conferences/{id}/words/{word}/timeline
        String[] parts = path.split("/");
        String conferenceId = null; String word = null;
        for (int i = 0; i < parts.length; i++) {
            if ("conferences".equals(parts[i]) && i+1 < parts.length) conferenceId = parts[i+1];
            if ("words".equals(parts[i]) && i+1 < parts.length) word = URLDecoder.decode(parts[i+1], StandardCharsets.UTF_8);
        }
        if (conferenceId == null || word == null) { error(res,cb,400,"bad_request","Invalid path"); return true; }
        ok(res, cb, useCase.execute(conferenceId, word));
        return true;
    }
}
