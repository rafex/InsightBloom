package dev.rafex.insightbloom.users.domain.ports;

import java.time.Instant;

/** Publica una copia estática validada del workspace, nunca el sandbox vivo. */
public interface WorkspacePreviewPublisher {
    PreviewPublication publish(String conferenceUuid, String ownerUuid, byte[] workspaceZip, long ttlSeconds);

    void revoke(String conferenceUuid, String ownerUuid, String publicationId);

    record PreviewPublication(String publicationId, String url, Instant expiresAt, String artifactHash, int files) { }
}
