package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.AssignSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateWorkspaceDownloadUrlUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetSandboxAvailabilityUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class SandboxHandler extends BaseResourceHandler {
    private final AssignSandboxUseCase assignSandboxUseCase;
    private final GetSandboxAvailabilityUseCase getSandboxAvailabilityUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final GenerateWorkspaceDownloadUrlUseCase generateWorkspaceDownloadUrlUseCase;
    private final SetSandboxConfigUseCase setSandboxConfigUseCase;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final ConferenceRepository conferenceRepository;
    private final EventCapabilityGuard eventCapabilityGuard;
    private final String gatewayBaseUrl; // ej. "https://ide-insightbloom.v1.rafex.cloud"

    public SandboxHandler(final AssignSandboxUseCase assignSandboxUseCase,
                         final GetSandboxAvailabilityUseCase getSandboxAvailabilityUseCase,
                         final ValidateTokenUseCase validateTokenUseCase,
                         final GenerateWorkspaceDownloadUrlUseCase generateWorkspaceDownloadUrlUseCase,
                         final SetSandboxConfigUseCase setSandboxConfigUseCase,
                         final SandboxOrchestrator sandboxOrchestrator,
                         final ConferenceRepository conferenceRepository,
                         final EventCapabilityGuard eventCapabilityGuard,
                         final String gatewayBaseUrl) {
        this.assignSandboxUseCase = assignSandboxUseCase;
        this.getSandboxAvailabilityUseCase = getSandboxAvailabilityUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.generateWorkspaceDownloadUrlUseCase = generateWorkspaceDownloadUrlUseCase;
        this.setSandboxConfigUseCase = setSandboxConfigUseCase;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.conferenceRepository = conferenceRepository;
        this.eventCapabilityGuard = eventCapabilityGuard;
        this.gatewayBaseUrl = gatewayBaseUrl;
    }

    /**
     * Solo conferencias cuyo tipo de evento tiene la capacidad CODE_IDE ("IDE" en el Dashboard,
     * ver EventTypesAdminPage.vue) pueden usar Web o CLI -- mismo patron 409
     * "capability_not_available" que ya usan notas/diagramas/video en ConferenceHandler
     * (ConferenceHandler.hasCapability), replicado aca porque SandboxHandler no comparte
     * handler con esos otros gates.
     *
     * @return true si ya se envio una respuesta de error (404 o 409) y el caller debe cortar.
     */
    private boolean rejectIfCodeIdeNotAvailable(final JettyHttpExchange jx, final String conferenceId) {
        final var conference = conferenceRepository.findByUuid(conferenceId);
        if (conference.isEmpty()) {
            sendError(jx, 404, "conference_not_found", "Conference not found");
            return true;
        }
        if (!eventCapabilityGuard.hasCapability(conference.get(), EventCapability.CODE_IDE)) {
            sendError(jx, 409, "capability_not_available", "El tipo de evento no habilita el IDE");
            return true;
        }
        return false;
    }

    @Override
    public String basePath() {
        return "/api/v1/conferences";
    }

    @Override
    public List<Route> routes() {
        return List.of(
            Route.of("/{id}/sandbox", Set.of("GET")),
            Route.of("/{id}/sandbox/availability", Set.of("GET")),
            Route.of("/{id}/sandbox/download", Set.of("POST")),
            Route.of("/{id}/sandbox/config", Set.of("PUT"))
        );
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/availability")) {
            return handleGetAvailability(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox")) {
            return handleGetSandbox(jx, jx.pathParam("id"));
        }
        return false;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/download")) {
            return handleDownloadRequest(jx, jx.pathParam("id"));
        }
        return false;
    }

    @Override
    public boolean put(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/config")) {
            return handleSetSandboxConfig(jx, jx.pathParam("id"));
        }
        return false;
    }

    private boolean handleGetSandbox(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) {
                return true;
            }

            // "web" es el default -- pedido explicito de una variante concreta desde el picker
            // de IdePage.vue (ver AssignSandboxUseCase, Sandbox.VARIANT_WEB/VARIANT_CLI).
            final String requestedVariant = queryParam(jx, "variant");
            final Sandbox sandbox = assignSandboxUseCase.execute(conferenceId, v.subjectUuid(), requestedVariant);

            // El Pod pasa a fase "Running" en cuanto arrancan sus contenedores, sin esperar a que
            // pasen su readiness probe -- con el Pod de dos contenedores (ide+runtime) eso dejaba
            // cargar el IDE antes de que 'runtime' estuviera realmente listo (502/WS rechazado,
            // visto en produccion 2026-07-16). READY exige el condition Ready agregado del Pod.
            final String status = sandboxOrchestrator.isReady(sandbox.podName()) ? "READY" : "PENDING";

            final Map<String, Object> response = Map.of(
                "sandboxUuid", sandbox.getUuid(),
                "sandboxSlot", sandbox.getSandboxSlot(),
                "variant", sandbox.getVariant(),
                "status", status,
                "gatewayUrl", gatewayBaseUrl,
                "sandboxPath", "/"
            );
            sendOk(jx, 200, response);
            return true;
        } catch (final IllegalArgumentException e) {
            if ("conference_not_found".equals(e.getMessage())) {
                sendError(jx, 404, "conference_not_found", "Conference not found");
            } else if ("sandbox_pool_full".equals(e.getMessage())) {
                sendError(jx, 409, "sandbox_pool_full", "Sandbox pool is full");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    private boolean handleGetAvailability(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) {
                return true;
            }

            final var availability = getSandboxAvailabilityUseCase.execute(conferenceId, v.subjectUuid());
            final Map<String, Object> response = Map.of(
                "web", Map.of(
                    "available", availability.web().available(),
                    "activeCount", availability.web().activeCount(),
                    "capacity", availability.web().capacity()),
                "cli", Map.of(
                    "available", availability.cli().available(),
                    "activeCount", availability.cli().activeCount(),
                    "capacity", availability.cli().capacity())
            );
            sendOk(jx, 200, response);
            return true;
        } catch (final IllegalArgumentException e) {
            if ("conference_not_found".equals(e.getMessage())) {
                sendError(jx, 404, "conference_not_found", "Conference not found");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private boolean handleDownloadRequest(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }

            final var downloadInfo = generateWorkspaceDownloadUrlUseCase.execute(conferenceId, v.subjectUuid());

            final Map<String, Object> response = Map.of(
                "sandboxUuid", downloadInfo.sandboxUuid,
                "downloadUrl", downloadInfo.downloadUrl,
                "expiresInSeconds", downloadInfo.expiresInSeconds
            );
            sendOk(jx, 200, response);
            return true;
        } catch (final IllegalArgumentException e) {
            if ("sandbox_not_assigned".equals(e.getMessage())) {
                sendError(jx, 404, "sandbox_not_assigned", "No sandbox assigned");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
            return true;
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    private boolean handleSetSandboxConfig(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var v = validateTokenUseCase.execute(token);
            if (!v.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }

            final var body = parseBody(jx);
            final String sandboxVariant = (String) body.get("sandboxVariant");
            final Integer sandboxPoolSize = (Integer) body.get("sandboxPoolSize");
            final String sandboxExtraPackages = (String) body.get("sandboxExtraPackages");
            final String sandboxRemoteGitUrl = (String) body.get("sandboxRemoteGitUrl");
            final Integer sandboxJvmHeapMb = (Integer) body.get("sandboxJvmHeapMb");
            final Integer sandboxSeatsPerPod = (Integer) body.get("sandboxSeatsPerPod");
            final Integer sandboxCliPoolSize = (Integer) body.get("sandboxCliPoolSize");

            final var updated = setSandboxConfigUseCase.execute(
                conferenceId,
                sandboxVariant,
                sandboxPoolSize,
                sandboxExtraPackages,
                sandboxRemoteGitUrl,
                sandboxJvmHeapMb,
                sandboxSeatsPerPod,
                sandboxCliPoolSize
            );

            sendOk(jx, 200, updated);
            return true;
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }
}
