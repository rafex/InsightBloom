package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ToolKey;

import java.util.List;
import java.util.Set;

/** Persistencia del candado de acceso por herramienta (2026-07-27). Ausencia de fila = bloqueado. */
public interface ToolAccessRepository {
    boolean isReleased(String conferenceUuid, ToolKey toolKey, String userUuid);

    boolean isReleasedForAll(String conferenceUuid, ToolKey toolKey);

    /** Herramientas liberadas (para todos o para este usuario puntualmente) de un solo golpe. */
    Set<ToolKey> resolveReleasedForUser(String conferenceUuid, String userUuid);

    /** UUIDs liberados individualmente para esa herramienta, sin incluir el comodín "*". */
    List<String> releasedUserUuids(String conferenceUuid, ToolKey toolKey);

    void releaseForAll(String conferenceUuid, ToolKey toolKey);

    void releaseUsers(String conferenceUuid, ToolKey toolKey, List<String> userUuids);

    /** Vuelve a bloquear la herramienta para todos: borra la liberación global y las individuales. */
    void lockForAll(String conferenceUuid, ToolKey toolKey);

    void lockUsers(String conferenceUuid, ToolKey toolKey, List<String> userUuids);

    /** Libera todas las herramientas y acciones para todos de un solo golpe (botón de recuperación). */
    void releaseAllTools(String conferenceUuid, List<ToolKey> toolKeys);
}
