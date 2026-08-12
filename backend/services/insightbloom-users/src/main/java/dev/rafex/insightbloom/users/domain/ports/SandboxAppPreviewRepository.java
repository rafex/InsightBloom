package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.SandboxAppPreview;

import java.util.List;
import java.util.Optional;

public interface SandboxAppPreviewRepository {
    Optional<SandboxAppPreview> findByUuid(String uuid);

    Optional<SandboxAppPreview> findByConferenceAndUser(String conferenceUuid, String userUuid);

    /**
     * Fase 4b (MVP): publicaciones activas (no expiradas) que apuntan a un pod específico -- usado
     * por {@code PublishContainerUseCase} para calcular qué puerto del pod Podman COMPARTIDO está
     * libre (varios alumnos distintos publican contenedores en el mismo pod, cada uno con su
     * propio puerto). No tiene sentido para los demás usos de este repositorio (un sandbox por
     * alumno/evento nunca comparte podName con otro).
     */
    List<SandboxAppPreview> findActiveByPodName(String podName);

    /** Reemplaza cualquier publicación previa del mismo (conferenceUuid, userUuid). */
    SandboxAppPreview save(SandboxAppPreview preview);

    void deleteByUuid(String uuid);
}
