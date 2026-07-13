package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;

public class SetSandboxInternetUseCase {
    private final ConferenceRepository conferenceRepository;
    private final SandboxOrchestrator sandboxOrchestrator;

    public SetSandboxInternetUseCase(final ConferenceRepository conferenceRepository,
                                      final SandboxOrchestrator sandboxOrchestrator) {
        this.conferenceRepository = conferenceRepository;
        this.sandboxOrchestrator = sandboxOrchestrator;
    }

    /**
     * Actualiza el flag de acceso a internet para los sandboxes de un evento.
     *
     * Organizer-only. Cambia en caliente sin reiniciar sandboxes ya corriendo: crea/borra la
     * NetworkPolicy de egress-allow del evento (ver KubernetesPodClient#allowInternetEgress),
     * el default-deny del namespace hace el resto.
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

        final String conferenceLabel = Sandbox.conferenceLabel(conferenceUuid);
        try {
            if (internetEnabled == 1) {
                sandboxOrchestrator.allowInternetEgress(conferenceLabel);
            } else {
                sandboxOrchestrator.denyInternetEgress(conferenceLabel);
            }
        } catch (final IllegalStateException e) {
            if (!"kubernetes_not_configured".equals(e.getMessage())) {
                throw e;
            }
            // Sin Kubernetes configurado (dev local): el flag queda guardado igual, se aplicara
            // la proxima vez que se cree un sandbox para este evento con internetEnabled=1.
        }

        return conf;
    }
}
