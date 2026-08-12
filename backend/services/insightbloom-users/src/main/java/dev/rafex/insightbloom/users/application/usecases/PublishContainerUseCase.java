package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ContainerBuildResult;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;
import dev.rafex.insightbloom.users.domain.services.ContainerfileValidator;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Publica un contenedor construido desde un Containerfile del workspace del alumno (Fase 4b) --
 * ata las piezas de Fase 4a (validación de política de imágenes) con el pod Podman COMPARTIDO
 * (ver {@link SandboxOrchestrator#ensureRuntimePodmanPod}) y con el mecanismo de proxy en vivo que
 * ya usa "Publicar API" ({@link SandboxAppPreview}/{@code AppPreviewGateHandler}) -- cero código
 * nuevo de proxy.
 *
 * MVP (2026-08, decidido con el usuario): un único pod Podman para TODOS los alumnos mientras se
 * prueba que el aislamiento (user namespaces nativos de Kubernetes, {@code hostUsers: false})
 * funciona en el cluster real -- por eso NO pasa por {@link SandboxRepository}/{@code
 * AssignSandboxUseCase} (esos asumen "un sandbox por alumno/evento", y acá el alumno necesita su
 * sandbox IDE Y el pod Podman al mismo tiempo). Cada publicación obtiene su propio puerto dentro
 * del pod compartido -- ver {@link #allocateHostPort}.
 */
public class PublishContainerUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Ver PublishAppPreviewUseCase.MAX_TTL_SECONDS -- mismo criterio, tope duro de 1h. */
    private static final long MAX_TTL_SECONDS = 3600;
    private static final Pattern EXPOSE_LINE = Pattern.compile(
            "^\\s*EXPOSE\\s+(\\d+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    /** Sin puerto expuesto en el Containerfile -- el contenedor corre igual, pero no queda publicable. */
    private static final int NO_CONTAINER_PORT = -1;

    private final SandboxRepository sandboxRepository;
    private final SandboxOrchestrator sandboxOrchestrator;
    private final SandboxAppPreviewRepository previewRepository;
    private final ResolveImagePolicyUseCase resolveImagePolicyUseCase;
    private final String sharedPodName;
    private final int podmanAppBasePort;
    private final int maxConcurrentPublications;

    public PublishContainerUseCase(final SandboxRepository sandboxRepository,
                                    final SandboxOrchestrator sandboxOrchestrator,
                                    final SandboxAppPreviewRepository previewRepository,
                                    final ResolveImagePolicyUseCase resolveImagePolicyUseCase,
                                    final String sharedPodName,
                                    final int podmanAppBasePort,
                                    final int maxConcurrentPublications) {
        this.sandboxRepository = sandboxRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.previewRepository = previewRepository;
        this.resolveImagePolicyUseCase = resolveImagePolicyUseCase;
        this.sharedPodName = sharedPodName;
        this.podmanAppBasePort = podmanAppBasePort;
        this.maxConcurrentPublications = maxConcurrentPublications;
    }

    public record Result(boolean published, SandboxAppPreview preview) {
    }

    public Result execute(final String conferenceUuid, final String userUuid, final String containerfilePath,
                           final long ttlSeconds) {
        final Sandbox ideSandbox = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .orElseThrow(() -> new IllegalArgumentException("sandbox_not_assigned"));
        final WorkspaceFileContent file = sandboxOrchestrator.readWorkspaceFile(
                ideSandbox.podName(), ideSandbox.getSeatIndex(), containerfilePath);

        final ResolveImagePolicyUseCase.Resolution policy = resolveImagePolicyUseCase.execute(conferenceUuid);
        final ContainerfileValidator.ValidationResult validation =
                ContainerfileValidator.validate(file.content(), policy);
        if (!validation.valid()) {
            throw new ContainerValidationException(validation.errorCode(), validation.errorDetail());
        }

        sandboxOrchestrator.ensureRuntimePodmanPod(sharedPodName);

        final int containerPort = extractExposedPort(file.content());
        final int hostPort = allocateHostPort(conferenceUuid, userUuid);

        final ContainerBuildResult buildResult = sandboxOrchestrator.buildAndRunContainer(
                sharedPodName, file.content(), hostPort, containerPort);
        if (!buildResult.success()) {
            throw new ContainerValidationException(buildResult.errorCode(), buildResult.errorDetail());
        }

        if (containerPort <= 0) {
            return new Result(false, null);
        }

        final Instant now = Instant.now();
        final SandboxAppPreview preview = new SandboxAppPreview(
                UUID.randomUUID().toString(), conferenceUuid, userUuid, sharedPodName, hostPort,
                generateAccessToken(), now, now.plusSeconds(Math.min(ttlSeconds, MAX_TTL_SECONDS)));
        return new Result(true, previewRepository.save(preview));
    }

    /**
     * Reusa el puerto propio si el alumno ya tenía una publicación activa en el pod compartido
     * (republicar no debe competir por un slot nuevo); si no, toma el primer offset libre del
     * rango reservado. Lanza {@code publish_container_pool_full} si todos los slots están
     * ocupados por OTROS alumnos -- límite MVP explícito, ver clase.
     */
    private int allocateHostPort(final String conferenceUuid, final String userUuid) {
        final var own = previewRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (own.isPresent() && sharedPodName.equals(own.get().podName()) && !own.get().isExpired()) {
            return own.get().targetPort();
        }
        final Set<Integer> usedOffsets = new HashSet<>();
        for (final SandboxAppPreview active : previewRepository.findActiveByPodName(sharedPodName)) {
            if (conferenceUuid.equals(active.conferenceUuid()) && userUuid.equals(active.userUuid())) {
                continue;
            }
            usedOffsets.add(active.targetPort() - podmanAppBasePort);
        }
        for (int offset = 0; offset < maxConcurrentPublications; offset++) {
            if (!usedOffsets.contains(offset)) {
                return podmanAppBasePort + offset;
            }
        }
        throw new IllegalArgumentException("publish_container_pool_full");
    }

    private static int extractExposedPort(final String containerfileContent) {
        final Matcher matcher = EXPOSE_LINE.matcher(containerfileContent);
        int lastPort = NO_CONTAINER_PORT;
        while (matcher.find()) {
            lastPort = Integer.parseInt(matcher.group(1));
        }
        return lastPort;
    }

    private static String generateAccessToken() {
        final byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Fallo de validación de política de imágenes o de build -- ver ContainerfileValidator/errorCode. */
    public static final class ContainerValidationException extends RuntimeException {
        private final String errorCode;
        private final String errorDetail;

        public ContainerValidationException(final String errorCode, final String errorDetail) {
            super(errorCode);
            this.errorCode = errorCode;
            this.errorDetail = errorDetail;
        }

        public String errorCode() {
            return errorCode;
        }

        public String errorDetail() {
            return errorDetail;
        }
    }
}
