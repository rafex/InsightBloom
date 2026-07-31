package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ToolKey;
import dev.rafex.insightbloom.users.domain.ports.ToolAccessRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Candado por herramienta (2026-07-27): mismo concepto que el candado de Encuesta
 * (insightbloom-survey), centralizado acá porque Conference vive en insightbloom-users y el
 * resto de las herramientas (a diferencia de Encuesta) no tienen servicio propio con su
 * propia base de datos.
 */
public class ToolAccessUseCase {
    private final ToolAccessRepository repository;
    private final ListConferenceAttendeesUseCase listAttendeesUseCase;

    public ToolAccessUseCase(final ToolAccessRepository repository,
                              final ListConferenceAttendeesUseCase listAttendeesUseCase) {
        this.repository = repository;
        this.listAttendeesUseCase = listAttendeesUseCase;
    }

    public Set<ToolKey> resolveForUser(final String conferenceUuid, final String userUuid) {
        return repository.resolveReleasedForUser(conferenceUuid, userUuid);
    }

    public void release(final String conferenceUuid, final ToolKey toolKey, final boolean all,
                         final List<String> userUuids) {
        if (all) {
            repository.releaseForAll(conferenceUuid, toolKey);
        } else {
            repository.releaseUsers(conferenceUuid, toolKey, userUuids);
        }
    }

    public void lock(final String conferenceUuid, final ToolKey toolKey, final boolean all,
                      final List<String> userUuids) {
        if (all) {
            repository.lockForAll(conferenceUuid, toolKey);
        } else {
            repository.lockUsers(conferenceUuid, toolKey, userUuids);
        }
    }

    /** Botón de recuperación de un clic: libera todas las herramientas y acciones para todos. */
    public void releaseAll(final String conferenceUuid) {
        repository.releaseAllTools(conferenceUuid, List.of(ToolKey.values()));
    }

    /** Matriz herramienta×asistente para el panel de moderación. */
    public Map<ToolKey, ToolManagementView> managementView(final String conferenceUuid) {
        final var attendees = listAttendeesUseCase.execute(conferenceUuid);
        final Map<ToolKey, ToolManagementView> result = new LinkedHashMap<>();
        for (final ToolKey toolKey : ToolKey.values()) {
            final boolean releasedForAll = repository.isReleasedForAll(conferenceUuid, toolKey);
            final List<String> releasedIndividually = repository.releasedUserUuids(conferenceUuid, toolKey);
            final List<AttendeeAccess> attendeeAccess = attendees.stream()
                    .map(a -> new AttendeeAccess(a.uuid(), a.displayName(), a.email(),
                            releasedForAll || releasedIndividually.contains(a.uuid())))
                    .toList();
            result.put(toolKey, new ToolManagementView(releasedForAll, attendeeAccess));
        }
        return result;
    }

    public record AttendeeAccess(String uuid, String displayName, String email, boolean released) {}

    public record ToolManagementView(boolean releasedForAll, List<AttendeeAccess> attendees) {}
}
