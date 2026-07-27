package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;
import dev.rafex.insightbloom.users.domain.ports.SandboxAppPreviewRepository;

public class RevokeAppPreviewUseCase {
    private final SandboxAppPreviewRepository previewRepository;

    public RevokeAppPreviewUseCase(final SandboxAppPreviewRepository previewRepository) {
        this.previewRepository = previewRepository;
    }

    public void execute(final String conferenceUuid, final String userUuid, final String publicationId) {
        final SandboxAppPreview preview = previewRepository.findByUuid(publicationId)
                .orElseThrow(() -> new IllegalArgumentException("app_preview_not_found"));
        if (!preview.conferenceUuid().equals(conferenceUuid) || !preview.userUuid().equals(userUuid)) {
            throw new IllegalArgumentException("app_preview_not_found");
        }
        previewRepository.deleteByUuid(publicationId);
    }
}
