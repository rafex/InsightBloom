package dev.rafex.insightbloom.query.adapters.inbound.http.handlers;
import dev.rafex.insightbloom.query.application.usecases.GetCloudUseCase;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
public class CloudHandler extends BaseHandler {
    private final GetCloudUseCase useCase;
    public CloudHandler(GetCloudUseCase useCase) { this.useCase = useCase; }
    @Override
    public boolean handle(Request req, Response res, Callback cb) throws Exception {
        if (!"GET".equals(req.getMethod())) { error(res,cb,405,"method_not_allowed","GET required"); return true; }
        String path = req.getHttpURI().getPath();
        // /api/v1/conferences/{id}/cloud/doubts or /topics
        String[] parts = path.split("/");
        String conferenceId = null;
        MessageType type = null;
        for (int i = 0; i < parts.length; i++) {
            if ("conferences".equals(parts[i]) && i+1 < parts.length) conferenceId = parts[i+1];
            if ("doubts".equals(parts[i])) type = MessageType.DOUBT;
            if ("topics".equals(parts[i])) type = MessageType.TOPIC;
        }
        if (conferenceId == null || type == null) { error(res,cb,400,"bad_request","Invalid path"); return true; }
        ok(res, cb, useCase.execute(conferenceId, type));
        return true;
    }
}
