package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

public class SetSandboxInternetUseCase {
    private final ConferenceRepository conferenceRepository;

    public SetSandboxInternetUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    /**
     * Actualiza el flag de acceso a internet para los sandboxes de un evento.
     *
     * Organizer-only. Puede cambiar en caliente sin reiniciar sandboxes
     * (NetworkPolicy se actualiza en paralelo, ver TASK-0050).
     *
     * @param conferenceUuid evento
     * @param internetEnabled 1 (habilitado) o 0 (deshabilitado)
     * @return Conference actualizada
     * @throws IllegalArgumentException si el evento no existe
     */
    public Conference execute(final String conferenceUuid, final int internetEnabled) {
        if (internetEnabled != 0 && internetEnabled != 1) {
            throw new IllegalArgumentException("invalid_internet_enabled_value");
        }

        final Conference conf = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        conf.setSandboxInternetEnabled(internetEnabled);
        conferenceRepository.save(conf);

        return conf;
    }
}
