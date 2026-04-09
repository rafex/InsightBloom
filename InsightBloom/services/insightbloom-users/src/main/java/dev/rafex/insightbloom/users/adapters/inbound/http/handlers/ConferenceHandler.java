package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.insightbloom.users.application.usecases.CreateConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetConferenceUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.Map;

public class ConferenceHandler extends BaseHandler {
    private final CreateConferenceUseCase createConferenceUseCase;
    private final GetConferenceUseCase getConferenceUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public ConferenceHandler(CreateConferenceUseCase createConferenceUseCase,
                              GetConferenceUseCase getConferenceUseCase,
                              ValidateTokenUseCase validateTokenUseCase) {
        this.createConferenceUseCase = createConferenceUseCase;
        this.getConferenceUseCase = getConferenceUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();

        // POST /api/v1/conferences
        if ("POST".equals(method) && path.matches(".*/conferences/?$")) {
            return handleCreate(request, response, callback);
        }
        // GET /api/v1/conferences/by-friendly/{friendlyId}
        if ("GET".equals(method) && path.contains("/by-friendly/")) {
            String friendlyId = path.substring(path.lastIndexOf("/") + 1);
            return handleGetByFriendly(friendlyId, response, callback);
        }
        // GET /api/v1/conferences/{conferenceId}
        if ("GET".equals(method) && path.matches(".*/conferences/[^/]+$")) {
            String id = path.substring(path.lastIndexOf("/") + 1);
            return handleGetById(id, response, callback);
        }
        error(response, callback, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleCreate(Request request, Response response, Callback callback) throws Exception {
        String auth = getToken(request);
        if (auth == null) {
            error(response, callback, 401, "token_missing", "Authorization required");
            return true;
        }
        var validation = validateTokenUseCase.execute(auth);
        if (!validation.valid() || !"organizer".equals(validation.role())) {
            error(response, callback, 403, "forbidden", "Only organizers can create conferences");
            return true;
        }
        var body = readBody(request, Map.class);
        Double latitude = body.get("latitude") instanceof Number n ? n.doubleValue() : null;
        Double longitude = body.get("longitude") instanceof Number n ? n.doubleValue() : null;
        var result = createConferenceUseCase.execute(
            new CreateConferenceUseCase.CreateRequest((String) body.get("name"), validation.subjectUuid(), latitude, longitude)
        );
        created(response, callback, result);
        return true;
    }

    private boolean handleGetById(String id, Response response, Callback callback) throws Exception {
        getConferenceUseCase.byId(id)
            .ifPresentOrElse(
                c -> { try { ok(response, callback, c); } catch (Exception e) { throw new RuntimeException(e); } },
                () -> { try { error(response, callback, 404, "conference_not_found", "Conference not found"); } catch (Exception e) { throw new RuntimeException(e); } }
            );
        return true;
    }

    private boolean handleGetByFriendly(String friendlyId, Response response, Callback callback) throws Exception {
        getConferenceUseCase.byFriendlyId(friendlyId)
            .ifPresentOrElse(
                c -> { try { ok(response, callback, c); } catch (Exception e) { throw new RuntimeException(e); } },
                () -> { try { error(response, callback, 404, "conference_not_found", "Conference not found"); } catch (Exception e) { throw new RuntimeException(e); } }
            );
        return true;
    }

    private String getToken(Request request) {
        String auth = request.getHeaders().get("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7);
        return null;
    }
}
