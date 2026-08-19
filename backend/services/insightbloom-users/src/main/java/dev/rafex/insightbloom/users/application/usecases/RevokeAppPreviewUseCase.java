package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;
import dev.rafex.insightbloom.users.adapters.outbound.idepublisher.HttpWorkspacePreviewPublisher;

public class RevokeAppPreviewUseCase {
    private final SandboxAppPreviewRepository previewRepository;
    private final HttpWorkspacePreviewPublisher publisher;

    public RevokeAppPreviewUseCase(final SandboxAppPreviewRepository previewRepository) {
        this(previewRepository, null);
    }

    public RevokeAppPreviewUseCase(final SandboxAppPreviewRepository previewRepository,
                                   final HttpWorkspacePreviewPublisher publisher) {
        this.previewRepository = previewRepository;
        this.publisher = publisher;
    }

    public void execute(final String conferenceUuid, final String userUuid, final String publicationId) {
        final SandboxAppPreview preview = previewRepository.findByUuid(publicationId)
                .orElseThrow(() -> new IllegalArgumentException("app_preview_not_found"));
        if (!preview.conferenceUuid().equals(conferenceUuid) || !preview.userUuid().equals(userUuid)) {
            throw new IllegalArgumentException("app_preview_not_found");
        }
        if (publisher != null) publisher.revokeAppPreview(conferenceUuid, userUuid, publicationId);
        previewRepository.deleteByUuid(publicationId);
    }
}
