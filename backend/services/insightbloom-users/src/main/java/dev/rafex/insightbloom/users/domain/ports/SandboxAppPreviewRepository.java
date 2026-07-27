package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;

import java.util.Optional;

public interface SandboxAppPreviewRepository {
    Optional<SandboxAppPreview> findByUuid(String uuid);

    Optional<SandboxAppPreview> findByConferenceAndUser(String conferenceUuid, String userUuid);

    /** Reemplaza cualquier publicación previa del mismo (conferenceUuid, userUuid). */
    SandboxAppPreview save(SandboxAppPreview preview);

    void deleteByUuid(String uuid);
}
