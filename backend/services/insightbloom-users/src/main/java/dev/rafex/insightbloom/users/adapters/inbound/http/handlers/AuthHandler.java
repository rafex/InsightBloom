package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.insightbloom.users.application.usecases.*;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.Map;

public class AuthHandler extends BaseHandler {
    private final LoginUseCase loginUseCase;
    private final CreateGuestUseCase createGuestUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;

    public AuthHandler(LoginUseCase loginUseCase, CreateGuestUseCase createGuestUseCase,
                       ValidateTokenUseCase validateTokenUseCase) {
        this.loginUseCase = loginUseCase;
        this.createGuestUseCase = createGuestUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
    }

    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
        String path = request.getHttpURI().getPath();
        String method = request.getMethod();

        if ("POST".equals(method) && path.endsWith("/login")) {
            return handleLogin(request, response, callback);
        } else if ("POST".equals(method) && path.endsWith("/guest")) {
            return handleGuest(request, response, callback);
        } else if ("GET".equals(method) && path.endsWith("/validate")) {
            return handleValidate(request, response, callback);
        }
        error(response, callback, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleLogin(Request request, Response response, Callback callback) throws Exception {
        var body = readBody(request, Map.class);
        String username = (String) body.get("username");
        var result = loginUseCase.execute(new LoginUseCase.LoginRequest(username, null));
        if (result.isPresent()) {
            created(response, callback, result.get());
        } else {
            error(response, callback, 401, "invalid_credentials", "User not found");
        }
        return true;
    }

    private boolean handleGuest(Request request, Response response, Callback callback) throws Exception {
        var body = readBody(request, Map.class);
        try {
            var result = createGuestUseCase.execute(new CreateGuestUseCase.GuestRequest(
                (String) body.get("displayName"),
                (String) body.get("deviceFingerprint"),
                (String) body.get("conferenceUuid")
            ));
            created(response, callback, result);
        } catch (IllegalArgumentException e) {
            error(response, callback, 404, e.getMessage(), "Conference not found");
        }
        return true;
    }

    private boolean handleValidate(Request request, Response response, Callback callback) throws Exception {
        String auth = request.getHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            error(response, callback, 401, "token_missing", "Authorization header missing");
            return true;
        }
        String token = auth.substring(7);
        var result = validateTokenUseCase.execute(token);
        if (result.valid()) {
            ok(response, callback, result);
        } else {
            error(response, callback, 401, "token_invalid", "Token is invalid or expired");
        }
        return true;
    }
}
