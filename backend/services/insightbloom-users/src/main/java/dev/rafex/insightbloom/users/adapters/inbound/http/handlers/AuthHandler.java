package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.CreateGuestUseCase;
import dev.rafex.insightbloom.users.application.usecases.LoginUseCase;
import dev.rafex.insightbloom.users.application.usecases.LogoutUseCase;
import dev.rafex.insightbloom.users.application.usecases.RegisterUseCase;
import dev.rafex.insightbloom.users.application.usecases.SendOtpUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.VerifyOtpUseCase;
import dev.rafex.insightbloom.users.domain.model.SocialLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuthHandler extends BaseResourceHandler {

    private final LoginUseCase loginUseCase;
    private final CreateGuestUseCase createGuestUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final RegisterUseCase registerUseCase;
    private final SendOtpUseCase sendOtpUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthHandler(final LoginUseCase loginUseCase, final CreateGuestUseCase createGuestUseCase,
                       final ValidateTokenUseCase validateTokenUseCase, final RegisterUseCase registerUseCase,
                       final SendOtpUseCase sendOtpUseCase, final VerifyOtpUseCase verifyOtpUseCase,
                       final LogoutUseCase logoutUseCase) {
        this.loginUseCase = loginUseCase;
        this.createGuestUseCase = createGuestUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.registerUseCase = registerUseCase;
        this.sendOtpUseCase = sendOtpUseCase;
        this.verifyOtpUseCase = verifyOtpUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @Override
    protected String basePath() {
        return "/api/v1/auth";
    }

    @Override
    protected List<Route> routes() {
        return List.of(
                Route.of("/login", Set.of("POST")),
                Route.of("/logout", Set.of("POST")),
                Route.of("/guest", Set.of("POST")),
                Route.of("/validate", Set.of("GET")),
                Route.of("/register", Set.of("POST")),
                Route.of("/otp/send", Set.of("POST")),
                Route.of("/otp/verify", Set.of("POST")));
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST");
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        final String path = jx.path();
        if (path.endsWith("/login")) return handleLogin(jx);
        if (path.endsWith("/logout")) return handleLogout(jx);
        if (path.endsWith("/guest")) return handleGuest(jx);
        if (path.endsWith("/register")) return handleRegister(jx);
        if (path.endsWith("/otp/send")) return handleSendOtp(jx);
        if (path.endsWith("/otp/verify")) return handleVerifyOtp(jx);
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/validate")) return handleValidate(jx);
        sendError(jx, 404, "not_found", "Endpoint not found");
        return true;
    }

    private boolean handleLogin(final JettyHttpExchange jx) {
        try {
            final var body = parseBody(jx);
            final String username = (String) body.get("username");
            final String password = (String) body.get("password");
            final var result = loginUseCase.execute(new LoginUseCase.LoginRequest(username, password));
            if (result.isPresent()) {
                sendOk(jx, 201, result.get());
            } else {
                sendError(jx, 401, "invalid_credentials", "Invalid username or password");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleGuest(final JettyHttpExchange jx) {
        try {
            final var body = parseBody(jx);
            final var result = createGuestUseCase.execute(new CreateGuestUseCase.GuestRequest(
                    (String) body.get("displayName"),
                    (String) body.get("deviceFingerprint"),
                    (String) body.get("conferenceUuid")));
            sendOk(jx, 201, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, e.getMessage(), "Conference not found");
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleValidate(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendError(jx, 401, "token_missing", "Authorization header missing");
            return true;
        }
        try {
            final var result = validateTokenUseCase.execute(auth.substring(7));
            if (result.valid()) {
                sendOk(jx, 200, result);
            } else {
                sendError(jx, 401, "token_invalid", "Token is invalid or expired");
            }
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleRegister(final JettyHttpExchange jx) {
        try {
            final var body = parseBody(jx);
            final List<Map<String, String>> rawLinks = (List<Map<String, String>>) body.get("socialLinks");
            final List<SocialLink> socialLinks = new ArrayList<>();
            if (rawLinks != null) {
                for (final Map<String, String> link : rawLinks) {
                    if (link.get("platform") != null && link.get("url") != null) {
                        socialLinks.add(new SocialLink(link.get("platform"), link.get("url")));
                    }
                }
            }
            final var user = registerUseCase.execute(new RegisterUseCase.Request(
                    (String) body.get("displayName"), (String) body.get("email"), (String) body.get("phone"),
                    (String) body.get("password"), socialLinks));
            sendOk(jx, 201, Map.of("userUuid", user.getUuid(), "email", user.getEmail() == null ? "" : user.getEmail(),
                    "phone", user.getPhone() == null ? "" : user.getPhone()));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 409, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleSendOtp(final JettyHttpExchange jx) {
        try {
            final var body = parseBody(jx);
            sendOtpUseCase.execute(new SendOtpUseCase.Request(
                    (String) body.get("identifier"), (String) body.get("channel")));
            sendOk(jx, 200, Map.of("status", "sent"));
        } catch (final IllegalStateException e) {
            sendError(jx, 503, e.getMessage(), e.getMessage());
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handleLogout(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            sendError(jx, 401, "token_missing", "Authorization header missing");
            return true;
        }
        try {
            logoutUseCase.execute(auth.substring(7));
            sendOk(jx, 200, Map.of("status", "logged_out"));
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal error");
        }
        return true;
    }

    private boolean handleVerifyOtp(final JettyHttpExchange jx) {
        try {
            final var body = parseBody(jx);
            final var result = verifyOtpUseCase.execute(new VerifyOtpUseCase.Request(
                    (String) body.get("identifier"), (String) body.get("code")));
            sendOk(jx, 200, result);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

}
