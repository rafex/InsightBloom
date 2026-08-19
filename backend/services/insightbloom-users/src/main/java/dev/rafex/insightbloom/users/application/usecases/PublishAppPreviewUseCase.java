package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.adapters.outbound.idepublisher.HttpWorkspacePreviewPublisher;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Publica el backend/API REST que el alumno corre dentro de su sandbox -- a diferencia de
 * {@link PublishWorkspacePreviewUseCase} (ZIP estatico), acá el proceso sigue vivo y
 * insightbloom-tools-gateway lo proxea en tiempo real (ver AppPreviewGateHandler,
 * ResolveAppPreviewTargetUseCase).
 *
 * No requiere que el proceso del alumno ya este escuchando: publicar solo registra el target
 * (puerto de su asiento) y emite el token de acceso -- si el alumno todavia no arranco su server,
 * el gateway simplemente devuelve 502 al proxear hasta que lo haga. Un solo preview activo por
 * sandbox (ver SqliteSandboxAppPreviewRepository, UNIQUE(conference_uuid, user_uuid)).
 */
public class PublishAppPreviewUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    /**
     * Tope duro, independiente de lo que pida el caller: expone un proceso vivo del alumno a
     * internet con solo un token de acceso (sin sesión de InsightBloom) -- no debe poder quedar
     * publicado indefinidamente aunque {@code APP_PREVIEW_TTL_SECONDS} se configure más alto por
     * error. Mismo criterio en {@link PublishWorkspacePreviewUseCase}/{@link PublishContainerUseCase}.
     */
    private static final long MAX_TTL_SECONDS = 3600;

    private final SandboxRepository sandboxRepository;
    private final SandboxAppPreviewRepository previewRepository;
    private final int appBasePort;
    private final HttpWorkspacePreviewPublisher publisher;

    /**
     * @param appBasePort MISMO valor que {@code SANDBOX_APP_BASE_PORT} usado para construir
     *                    {@code KubernetesPodClient} (ver UsersApplication) -- duplicado a
     *                    proposito en vez de acoplar este caso de uso a la clase de
     *                    infraestructura, mismo criterio que {@link ResolveSandboxTargetUseCase}.
     */
    public PublishAppPreviewUseCase(final SandboxRepository sandboxRepository,
                                     final SandboxAppPreviewRepository previewRepository,
                                     final int appBasePort) {
        this(sandboxRepository, previewRepository, appBasePort, null);
    }

    public PublishAppPreviewUseCase(final SandboxRepository sandboxRepository,
                                     final SandboxAppPreviewRepository previewRepository,
                                     final int appBasePort,
                                     final HttpWorkspacePreviewPublisher publisher) {
        this.sandboxRepository = sandboxRepository;
        this.previewRepository = previewRepository;
        this.appBasePort = appBasePort;
        this.publisher = publisher;
    }

    public SandboxAppPreview execute(final String conferenceUuid, final String userUuid, final long ttlSeconds) {
        final Sandbox sandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        if (sandbox.getExpiresAt() != null && !sandbox.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("sandbox_expired");
        }
        final Instant now = Instant.now();
        final SandboxAppPreview preview = new SandboxAppPreview(
                UUID.randomUUID().toString(),
                conferenceUuid,
                userUuid,
                sandbox.podName(),
                sandbox.seatPort(appBasePort),
                generateAccessToken(),
                now,
                now.plusSeconds(Math.min(ttlSeconds, MAX_TTL_SECONDS)));
        final SandboxAppPreview saved = previewRepository.save(preview);
        if (publisher != null) {
            publisher.registerAppPreview(conferenceUuid, userUuid, saved.uuid(), saved.podName(),
                    saved.targetPort(), saved.accessToken(), saved.expiresAt());
        }
        return saved;
    }

    private static String generateAccessToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
