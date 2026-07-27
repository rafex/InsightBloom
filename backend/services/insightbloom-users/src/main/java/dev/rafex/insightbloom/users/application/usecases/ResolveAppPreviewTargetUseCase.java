package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Resuelve, para insightbloom-tools-gateway, a que Service:puerto proxear una request de
 * app-preview -- mismo rol que {@link ResolveSandboxTargetUseCase} para IDE Web/CLI, pero la
 * autenticacion acá es el {@code accessToken} de la publicación (ver {@link SandboxAppPreview}),
 * no una sesión de InsightBloom. Llamado solo desde el endpoint interno protegido por
 * X-Internal-Auth (nunca directamente por el cliente externo que consume la API publicada).
 */
public class ResolveAppPreviewTargetUseCase {
    private final SandboxAppPreviewRepository previewRepository;
    private final String namespace;

    public ResolveAppPreviewTargetUseCase(final SandboxAppPreviewRepository previewRepository,
                                           final String namespace) {
        this.previewRepository = previewRepository;
        this.namespace = namespace;
    }

    public Optional<String> execute(final String publicationId, final String accessToken) {
        if (publicationId == null || accessToken == null) return Optional.empty();
        return previewRepository.findByUuid(publicationId)
                .filter(preview -> !preview.isExpired())
                .filter(preview -> constantTimeEquals(preview.accessToken(), accessToken))
                .map(preview -> "http://" + preview.podName() + "-svc." + namespace
                        + ".svc.cluster.local:" + preview.targetPort());
    }

    private static boolean constantTimeEquals(final String a, final String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
