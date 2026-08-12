package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.core.HttpExchange.EventStream;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.common.http.BaseResourceHandler;
import dev.rafex.insightbloom.users.application.usecases.AssignSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.EnsureUnassignedSandboxUseCase;
import dev.rafex.insightbloom.users.application.usecases.GenerateWorkspaceDownloadUrlUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetSandboxAvailabilityUseCase;
import dev.rafex.insightbloom.users.application.usecases.GetWorkspaceZipJobStatusUseCase;
import dev.rafex.insightbloom.users.application.usecases.StartWorkspaceZipJobUseCase;
import dev.rafex.insightbloom.users.application.usecases.SetSandboxConfigUseCase;
import dev.rafex.insightbloom.users.application.usecases.ValidateTokenUseCase;
import dev.rafex.insightbloom.users.application.usecases.PublishWorkspacePreviewUseCase;
import dev.rafex.insightbloom.users.application.usecases.RevokeWorkspacePreviewUseCase;
import dev.rafex.insightbloom.users.application.usecases.PublishAppPreviewUseCase;
import dev.rafex.insightbloom.users.application.usecases.RevokeAppPreviewUseCase;
import dev.rafex.insightbloom.users.application.usecases.PublishContainerUseCase;
import dev.rafex.insightbloom.users.domain.model.EventCapability;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.ToolKey;
import dev.rafex.insightbloom.users.application.usecases.ToolAccessUseCase;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.services.DeviceBlockedException;
import dev.rafex.insightbloom.users.domain.services.EventCapabilityGuard;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SandboxHandler extends BaseResourceHandler {
    private static final Logger LOGGER = Logger.getLogger(SandboxHandler.class.getName());
    private static final long SANDBOX_STREAM_INTERVAL_SECONDS = 2;
    private static final long SANDBOX_STREAM_TIMEOUT_SECONDS = 5 * 60;
    private final AssignSandboxUseCase assignSandboxUseCase;
    private final GetSandboxAvailabilityUseCase getSandboxAvailabilityUseCase;
    private final ValidateTokenUseCase validateTokenUseCase;
    private final GenerateWorkspaceDownloadUrlUseCase generateWorkspaceDownloadUrlUseCase;
    private final StartWorkspaceZipJobUseCase startWorkspaceZipJobUseCase;
    private final GetWorkspaceZipJobStatusUseCase getWorkspaceZipJobStatusUseCase;
    private final SetSandboxConfigUseCase setSandboxConfigUseCase;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final ConferenceRepository conferenceRepository;
    private final EventCapabilityGuard eventCapabilityGuard;
    private final ToolAccessUseCase toolAccessUseCase;
    private final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase;
    private final PublishWorkspacePreviewUseCase publishWorkspacePreviewUseCase;
    private final RevokeWorkspacePreviewUseCase revokeWorkspacePreviewUseCase;
    private final long previewTtlSeconds;
    private final String gatewayBaseUrl; // ej. "https://ide-insightbloom.v1.rafex.cloud"
    private final PublishAppPreviewUseCase publishAppPreviewUseCase;
    private final RevokeAppPreviewUseCase revokeAppPreviewUseCase;
    private final long appPreviewTtlSeconds;
    private final String appPreviewBaseUrl; // ej. "https://app-insightbloom.v1.rafex.cloud"
    private final PublishContainerUseCase publishContainerUseCase;
    private final ScheduledExecutorService sandboxStreamScheduler = Executors.newScheduledThreadPool(8, runnable -> {
        final Thread thread = new Thread(runnable, "sandbox-status-sse");
        thread.setDaemon(true);
        return thread;
    });

    public SandboxHandler(final AssignSandboxUseCase assignSandboxUseCase,
                         final GetSandboxAvailabilityUseCase getSandboxAvailabilityUseCase,
                         final ValidateTokenUseCase validateTokenUseCase,
                         final GenerateWorkspaceDownloadUrlUseCase generateWorkspaceDownloadUrlUseCase,
                         final StartWorkspaceZipJobUseCase startWorkspaceZipJobUseCase,
                         final GetWorkspaceZipJobStatusUseCase getWorkspaceZipJobStatusUseCase,
                         final SetSandboxConfigUseCase setSandboxConfigUseCase,
                         final SandboxOrchestrator sandboxOrchestrator,
                         final ConferenceRepository conferenceRepository,
                         final EventCapabilityGuard eventCapabilityGuard,
                         final ToolAccessUseCase toolAccessUseCase,
                         final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase,
                         final String gatewayBaseUrl,
                         final PublishWorkspacePreviewUseCase publishWorkspacePreviewUseCase,
                         final RevokeWorkspacePreviewUseCase revokeWorkspacePreviewUseCase,
                         final long previewTtlSeconds,
                         final PublishAppPreviewUseCase publishAppPreviewUseCase,
                         final RevokeAppPreviewUseCase revokeAppPreviewUseCase,
                         final long appPreviewTtlSeconds,
                         final String appPreviewBaseUrl,
                         final PublishContainerUseCase publishContainerUseCase) {
        this.assignSandboxUseCase = assignSandboxUseCase;
        this.getSandboxAvailabilityUseCase = getSandboxAvailabilityUseCase;
        this.validateTokenUseCase = validateTokenUseCase;
        this.generateWorkspaceDownloadUrlUseCase = generateWorkspaceDownloadUrlUseCase;
        this.startWorkspaceZipJobUseCase = startWorkspaceZipJobUseCase;
        this.getWorkspaceZipJobStatusUseCase = getWorkspaceZipJobStatusUseCase;
        this.setSandboxConfigUseCase = setSandboxConfigUseCase;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.conferenceRepository = conferenceRepository;
        this.eventCapabilityGuard = eventCapabilityGuard;
        this.toolAccessUseCase = toolAccessUseCase;
        this.ensureUnassignedSandboxUseCase = ensureUnassignedSandboxUseCase;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.publishWorkspacePreviewUseCase = publishWorkspacePreviewUseCase;
        this.revokeWorkspacePreviewUseCase = revokeWorkspacePreviewUseCase;
        this.previewTtlSeconds = previewTtlSeconds;
        this.publishAppPreviewUseCase = publishAppPreviewUseCase;
        this.revokeAppPreviewUseCase = revokeAppPreviewUseCase;
        this.appPreviewTtlSeconds = appPreviewTtlSeconds;
        this.appPreviewBaseUrl = appPreviewBaseUrl;
        this.publishContainerUseCase = publishContainerUseCase;
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
            Route.of("/{id}/sandbox/stream", Set.of("GET")),
            Route.of("/{id}/sandbox/availability", Set.of("GET")),
            Route.of("/{id}/sandbox/download", Set.of("POST")),
            Route.of("/{id}/sandbox/download/start", Set.of("POST")),
            Route.of("/{id}/sandbox/download/status/{jobUuid}", Set.of("GET")),
            Route.of("/{id}/sandbox/preview", Set.of("POST")),
            Route.of("/{id}/sandbox/preview/{publicationId}", Set.of("DELETE")),
            Route.of("/{id}/sandbox/app-preview", Set.of("POST")),
            Route.of("/{id}/sandbox/app-preview/{publicationId}", Set.of("DELETE")),
            Route.of("/{id}/sandbox/publish-container", Set.of("POST")),
            Route.of("/{id}/sandbox/config", Set.of("PUT"))
        );
    }

    @Override
    public Set<String> supportedMethods() {
        return Set.of("GET", "POST", "PUT", "DELETE");
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().contains("/sandbox/download/status/")) {
            return handleGetDownloadJobStatus(jx, jx.pathParam("jobUuid"));
        }
        if (jx.path().endsWith("/sandbox/availability")) {
            return handleGetAvailability(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/stream")) {
            return handleSandboxStream(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox")) {
            return handleGetSandbox(jx, jx.pathParam("id"));
        }
        return false;
    }

    @Override
    public boolean post(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().endsWith("/sandbox/download/start")) {
            return handleStartDownloadJob(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/download")) {
            return handleDownloadRequest(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/preview")) {
            return handlePublishPreview(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/app-preview")) {
            return handlePublishAppPreview(jx, jx.pathParam("id"));
        }
        if (jx.path().endsWith("/sandbox/publish-container")) {
            return handlePublishContainer(jx, jx.pathParam("id"));
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

    @Override
    public boolean delete(final HttpExchange x) {
        final var jx = asJetty(x);
        if (jx.path().contains("/sandbox/app-preview/")) {
            return handleRevokeAppPreview(jx, jx.pathParam("id"), jx.pathParam("publicationId"));
        }
        if (jx.path().contains("/sandbox/preview/")) {
            return handleRevokePreview(jx, jx.pathParam("id"), jx.pathParam("publicationId"));
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
            final Sandbox sandbox = assignSandboxUseCase.execute(conferenceId, v.subjectUuid(), requestedVariant,
                    extractDeviceFingerprint(jx));

            // Mantiene un siguiente Pod libre cuando todavía hay capacidad configurada. En CLI
            // no crea trabajo innecesario: primero aprovecha los asientos disponibles del Pod
            // compartido y solo crea otro cuando los actuales están llenos.
            try {
                ensureUnassignedSandboxUseCase.ensureSpare(conferenceId, sandbox.getVariant());
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, "No se pudo reponer el sandbox libre de " + conferenceId, e);
            }

            // El Pod pasa a fase "Running" en cuanto arrancan sus contenedores, sin esperar a que
            // pasen su readiness probe -- con el Pod de dos contenedores (ide+runtime) eso dejaba
            // cargar el IDE antes de que 'runtime' estuviera realmente listo (502/WS rechazado,
            // visto en produccion 2026-07-16). READY exige el condition Ready agregado del Pod, Y
            // (Pods terminal-nvim multi-asiento) que ESTE asiento especifico ya este provisionado
            // en el seat-agent -- ver AssignSandboxUseCase.isSeatFullyProvisioned/DEC-0027.
            final String status = sandboxOrchestrator.isReady(sandbox.podName())
                    && assignSandboxUseCase.isSeatFullyProvisioned(sandbox, v.subjectUuid())
                    ? "READY" : "PENDING";

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
        } catch (final DeviceBlockedException e) {
            sendError(jx, 403, "device_blocked", "Este dispositivo fue bloqueado por uso con múltiples cuentas");
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
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    /**
     * Mantiene una conexión SSE mientras el Pod o el asiento CLI termina de provisionarse.
     * La asignación inicial sigue pasando por el mismo caso de uso y el chequeo se ejecuta en
     * el backend, no desde el navegador; así desaparece la tormenta de GET /sandbox del cliente.
     * El frontend conserva el GET inicial y un polling con backoff como fallback de reconexión.
     */
    private boolean handleSandboxStream(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) {
                return true;
            }

            final String requestedVariant = queryParam(jx, "variant");
            final String deviceFingerprint = extractDeviceFingerprint(jx);
            final Sandbox sandbox = assignSandboxUseCase.execute(
                    conferenceId, validation.subjectUuid(), requestedVariant, deviceFingerprint);
            final EventStream stream = jx.startEventStream();
            final AtomicReference<ScheduledFuture<?>> watcher = new AtomicReference<>();
            stream.onClose(() -> {
                final ScheduledFuture<?> future = watcher.get();
                if (future != null) future.cancel(true);
            });

            final boolean initialReady = isSandboxReady(sandbox, validation.subjectUuid());
            stream.send("status", sandboxStatusPayload(sandbox, initialReady));
            if (initialReady) {
                stream.close();
                return true;
            }

            final long streamDeadlineNanos = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(SANDBOX_STREAM_TIMEOUT_SECONDS);
            final ScheduledFuture<?> future = sandboxStreamScheduler.scheduleWithFixedDelay(() -> {
                try {
                    if (System.nanoTime() >= streamDeadlineNanos) {
                        stream.send("failure", JsonUtils.toJson(Map.of(
                                "code", "sandbox_timeout",
                                "message", "El sandbox está tardando más de lo esperado")));
                        stream.close();
                        return;
                    }
                    // Reejecutar la asignación es idempotente y también recrea el Pod si fue
                    // eliminado mientras el navegador seguía esperando.
                    final Sandbox current = assignSandboxUseCase.execute(
                            conferenceId, validation.subjectUuid(), requestedVariant, deviceFingerprint);
                    final boolean ready = isSandboxReady(current, validation.subjectUuid());
                    stream.send("status", sandboxStatusPayload(current, ready));
                    if (ready) {
                        stream.close();
                    }
                } catch (final DeviceBlockedException e) {
                    stream.send("failure", JsonUtils.toJson(Map.of(
                            "code", "device_blocked",
                            "message", "Este dispositivo fue bloqueado por uso con múltiples cuentas")));
                    stream.close();
                } catch (final IllegalArgumentException e) {
                    stream.send("failure", JsonUtils.toJson(Map.of(
                            "code", e.getMessage() != null ? e.getMessage() : "invalid_request",
                            "message", e.getMessage() != null ? e.getMessage() : "No se pudo preparar el sandbox")));
                    stream.close();
                } catch (final Exception e) {
                    LOGGER.log(Level.WARNING, "Sandbox SSE: no se pudo consultar " + conferenceId, e);
                    stream.send("failure", JsonUtils.toJson(Map.of(
                            "code", "sandbox_status_unavailable",
                            "message", "No se pudo consultar el estado del sandbox")));
                    stream.close();
                }
            }, SANDBOX_STREAM_INTERVAL_SECONDS, SANDBOX_STREAM_INTERVAL_SECONDS, TimeUnit.SECONDS);
            watcher.set(future);
            return true;
        } catch (final DeviceBlockedException e) {
            sendError(jx, 403, "device_blocked", "Este dispositivo fue bloqueado por uso con múltiples cuentas");
        } catch (final IllegalArgumentException e) {
            if ("conference_not_found".equals(e.getMessage())) {
                sendError(jx, 404, "conference_not_found", "Conference not found");
            } else if ("sandbox_pool_full".equals(e.getMessage())) {
                sendError(jx, 409, "sandbox_pool_full", "Sandbox pool is full");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en SSE " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    private boolean isSandboxReady(final Sandbox sandbox, final String userUuid) {
        return sandboxOrchestrator.isReady(sandbox.podName())
                && assignSandboxUseCase.isSeatFullyProvisioned(sandbox, userUuid);
    }

    private String sandboxStatusPayload(final Sandbox sandbox, final boolean ready) {
        final Map<String, Object> payload = new java.util.LinkedHashMap<>(Map.of(
                "sandboxUuid", sandbox.getUuid(),
                "sandboxSlot", sandbox.getSandboxSlot(),
                "variant", sandbox.getVariant(),
                "status", ready ? "READY" : "PENDING",
                "gatewayUrl", gatewayBaseUrl,
                "sandboxPath", "/"));
        final String reason = sandboxOrchestrator.getRuntimeStatus(sandbox.podName()).reason();
        if (reason != null && !reason.isBlank()) payload.put("reason", reason);
        return JsonUtils.toJson(payload);
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
                    "capacity", availability.cli().capacity()),
                "cliLazyVim", Map.of(
                    "available", availability.cliLazyVim().available(),
                    "activeCount", availability.cliLazyVim().activeCount(),
                    "capacity", availability.cliLazyVim().capacity())
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
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    private String extractToken(final JettyHttpExchange jx) {
        final String auth = jx.request().getHeaders().get("Authorization");
        return (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
    }

    private String extractDeviceFingerprint(final JettyHttpExchange jx) {
        return jx.request().getHeaders().get("X-Device-Fingerprint");
    }

    /**
     * Las acciones de entrega del IDE se controlan por separado de la entrada al IDE. La
     * validación vive en backend para que un asistente no pueda saltarse el botón deshabilitado
     * llamando directamente al endpoint.
     */
    private boolean rejectIfToolNotReleased(final JettyHttpExchange jx,
                                             final String conferenceId,
                                             final String userUuid,
                                             final ToolKey toolKey) {
        if (!toolAccessUseCase.resolveForUser(conferenceId, userUuid).contains(toolKey)) {
            sendError(jx, 403, "tool_locked", "El moderador aún no habilitó esta acción del IDE");
            return true;
        }
        return false;
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
            if (rejectIfToolNotReleased(jx, conferenceId, v.subjectUuid(), ToolKey.IDE_DOWNLOAD)) return true;

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
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
            return true;
        }
    }

    /** Arranca el armado del zip en background (ver StartWorkspaceZipJobUseCase) y devuelve de
     * inmediato -- reemplaza a handleDownloadRequest para workspaces pesados que antes daban
     * timeout. El botón de IdePage.vue pasa a pollear handleGetDownloadJobStatus. */
    private boolean handleStartDownloadJob(final JettyHttpExchange jx, final String conferenceId) {
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
            if (rejectIfToolNotReleased(jx, conferenceId, v.subjectUuid(), ToolKey.IDE_DOWNLOAD)) return true;

            final var job = startWorkspaceZipJobUseCase.execute(conferenceId, v.subjectUuid());
            sendOk(jx, 201, Map.of("jobUuid", job.uuid(), "status", job.status()));
        } catch (final IllegalArgumentException e) {
            if ("sandbox_not_assigned".equals(e.getMessage())) {
                sendError(jx, 404, "sandbox_not_assigned", "No sandbox assigned");
            } else {
                sendError(jx, 400, "invalid_request", e.getMessage());
            }
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
    }

    private boolean handleGetDownloadJobStatus(final JettyHttpExchange jx, final String jobUuid) {
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
            final var status = getWorkspaceZipJobStatusUseCase.execute(jobUuid, v.subjectUuid());
            final Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("status", status.status());
            response.put("downloadUrl", status.downloadUrl());
            response.put("errorMessage", status.errorMessage());
            sendOk(jx, 200, response);
        } catch (final IllegalArgumentException e) {
            sendError(jx, 404, "job_not_found", "No se encontró el pedido de descarga");
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", "Internal server error");
        }
        return true;
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
            final String sandboxRemoteGitUrl = (String) body.get("sandboxRemoteGitUrl");
            final Integer sandboxJvmHeapMb = (Integer) body.get("sandboxJvmHeapMb");
            final Integer sandboxSeatsPerPod = (Integer) body.get("sandboxSeatsPerPod");
            final Integer sandboxCliPoolSize = (Integer) body.get("sandboxCliPoolSize");
            final Integer sandboxCliLazyVimPoolSize = (Integer) body.get("sandboxCliLazyVimPoolSize");

            final var updated = setSandboxConfigUseCase.execute(
                conferenceId,
                sandboxVariant,
                sandboxPoolSize,
                sandboxRemoteGitUrl,
                sandboxJvmHeapMb,
                sandboxSeatsPerPod,
                sandboxCliPoolSize,
                sandboxCliLazyVimPoolSize
            );

            sendOk(jx, 200, updated);
            return true;
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: error inesperado en " + jx.path(), e);
            sendError(jx, 500, "internal_error", e.getMessage());
        }
        return true;
    }

    private boolean handlePublishPreview(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) return true;
            if (rejectIfToolNotReleased(jx, conferenceId, validation.subjectUuid(), ToolKey.IDE_PUBLISH_PAGE)) return true;
            final var publication = publishWorkspacePreviewUseCase.execute(
                    conferenceId, validation.subjectUuid(), previewTtlSeconds);
            sendOk(jx, 201, Map.of(
                    "publicationId", publication.publicationId(),
                    "url", publication.url(),
                    "expiresAt", publication.expiresAt().toString(),
                    "artifactHash", publication.artifactHash(),
                    "files", publication.files()));
        } catch (final IllegalArgumentException e) {
            final int status = switch (e.getMessage()) {
                case "sandbox_not_assigned" -> 404;
                case "sandbox_expired" -> 410;
                default -> 422;
            };
            sendError(jx, status, e.getMessage(), e.getMessage());
        } catch (final IllegalStateException e) {
            final String errorMessage = e.getMessage() == null ? "" : e.getMessage();
            final boolean artifactRejected = errorMessage.startsWith("preview_artifact_rejected");
            final String rejectionDetails = artifactRejected && errorMessage.length() > "preview_artifact_rejected:".length()
                    ? errorMessage.substring("preview_artifact_rejected:".length())
                    : "El sitio contiene contenido no permitido para una publicación estática";
            sendError(jx, artifactRejected ? 422 : 503,
                    artifactRejected ? "preview_artifact_rejected" : errorMessage,
                    artifactRejected ? rejectionDetails : "El publicador de páginas no está disponible");
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: no se pudo publicar el workspace", e);
            sendError(jx, 500, "preview_publication_failed", "No se pudo publicar el workspace");
        }
        return true;
    }

    private boolean handleRevokePreview(final JettyHttpExchange jx, final String conferenceId,
                                        final String publicationId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            revokeWorkspacePreviewUseCase.execute(conferenceId, validation.subjectUuid(), publicationId);
            sendOk(jx, 200, Map.of("revoked", true));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        } catch (final IllegalStateException e) {
            sendError(jx, 503, e.getMessage(), "El publicador de páginas no está disponible");
        }
        return true;
    }

    private boolean handlePublishAppPreview(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) return true;
            if (rejectIfToolNotReleased(jx, conferenceId, validation.subjectUuid(), ToolKey.IDE_PUBLISH_API)) return true;
            final var preview = publishAppPreviewUseCase.execute(
                    conferenceId, validation.subjectUuid(), appPreviewTtlSeconds);
            sendOk(jx, 201, Map.of(
                    "publicationId", preview.uuid(),
                    "url", appPreviewBaseUrl + "/p/" + preview.uuid(),
                    "accessToken", preview.accessToken(),
                    "expiresAt", preview.expiresAt().toString()));
        } catch (final IllegalArgumentException e) {
            final int status = switch (e.getMessage()) {
                case "sandbox_not_assigned" -> 404;
                case "sandbox_expired" -> 410;
                default -> 422;
            };
            sendError(jx, status, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: no se pudo publicar el app-preview", e);
            sendError(jx, 500, "app_preview_publication_failed", "No se pudo publicar la aplicación");
        }
        return true;
    }

    /**
     * Fase 4b (MVP): construye y corre un contenedor desde un Containerfile del workspace del
     * alumno, en el pod Podman COMPARTIDO -- ver {@code PublishContainerUseCase}. Reusa el gate de
     * {@link ToolKey#IDE_PUBLISH_API} a propósito (misma categoría "publicar algo vivo desde el
     * sandbox"; agregar un ToolKey dedicado, con su propio toggle en el Dashboard de moderación,
     * queda para cuando esta función salga del MVP).
     */
    private boolean handlePublishContainer(final JettyHttpExchange jx, final String conferenceId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            if (rejectIfCodeIdeNotAvailable(jx, conferenceId)) return true;
            if (rejectIfToolNotReleased(jx, conferenceId, validation.subjectUuid(), ToolKey.IDE_PUBLISH_API)) return true;
            final var body = parseBody(jx);
            final String path = body.get("path") instanceof String s && !s.isBlank() ? s : "Containerfile";
            final var result = publishContainerUseCase.execute(
                    conferenceId, validation.subjectUuid(), path, appPreviewTtlSeconds);
            if (!result.published()) {
                sendOk(jx, 200, Map.of("published", false,
                        "message", "El contenedor se construyó y corrió, pero el Containerfile no declara EXPOSE -- no queda publicable"));
                return true;
            }
            final var preview = result.preview();
            sendOk(jx, 201, Map.of(
                    "published", true,
                    "publicationId", preview.uuid(),
                    "url", appPreviewBaseUrl + "/p/" + preview.uuid(),
                    "accessToken", preview.accessToken(),
                    "expiresAt", preview.expiresAt().toString()));
        } catch (final PublishContainerUseCase.ContainerValidationException e) {
            sendError(jx, 422, e.errorCode(), e.errorDetail() != null ? e.errorDetail() : e.errorCode());
        } catch (final IllegalArgumentException e) {
            final int status = switch (e.getMessage()) {
                case "sandbox_not_assigned", "file_not_found" -> 404;
                case "publish_container_pool_full" -> 503;
                default -> 422;
            };
            sendError(jx, status, e.getMessage(), e.getMessage());
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "SandboxHandler: no se pudo publicar el contenedor", e);
            sendError(jx, 500, "publish_container_failed", "No se pudo publicar el contenedor");
        }
        return true;
    }

    private boolean handleRevokeAppPreview(final JettyHttpExchange jx, final String conferenceId,
                                            final String publicationId) {
        final String token = extractToken(jx);
        if (token == null) {
            sendError(jx, 401, "token_missing", "Authorization required");
            return true;
        }
        try {
            final var validation = validateTokenUseCase.execute(token);
            if (!validation.valid()) {
                sendError(jx, 401, "token_invalid", "Invalid token");
                return true;
            }
            revokeAppPreviewUseCase.execute(conferenceId, validation.subjectUuid(), publicationId);
            sendOk(jx, 200, Map.of("revoked", true));
        } catch (final IllegalArgumentException e) {
            sendError(jx, 400, e.getMessage(), e.getMessage());
        }
        return true;
    }
}
