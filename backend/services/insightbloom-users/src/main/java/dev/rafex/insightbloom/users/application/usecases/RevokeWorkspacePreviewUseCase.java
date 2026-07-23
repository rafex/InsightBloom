package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.WorkspacePreviewPublisher;

public final class RevokeWorkspacePreviewUseCase {
    private final WorkspacePreviewPublisher publisher;

    public RevokeWorkspacePreviewUseCase(final WorkspacePreviewPublisher publisher) {
        this.publisher = publisher;
    }

    public void execute(final String conferenceUuid, final String userUuid, final String publicationId) {
        if (publicationId == null || !publicationId.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("invalid_publication_id");
        }
        publisher.revoke(conferenceUuid, userUuid, publicationId);
    }
}
