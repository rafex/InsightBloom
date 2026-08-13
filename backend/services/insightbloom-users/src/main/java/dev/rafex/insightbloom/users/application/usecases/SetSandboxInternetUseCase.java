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
     * Organizer-only. Fase 7 (2026-08): ya no toca Kubernetes en absoluto -- el bloqueo real de
     * egress externo lo aplica nftables dentro de cada pod (ver
     * KubernetesPodClient#buildInitContainer/lockdown-egress.sh, siempre activo, no depende de
     * este flag) y el permiso/bloqueo POR DOMINIO lo decide dinámicamente
     * insightbloom-egress-proxy en cada request, vía {@code ResolveEgressPolicyUseCase}, leyendo
     * este mismo valor directo de SQLite (TTL corto, ~10s) -- cambia en caliente sin recrear
     * ningún sandbox, sin necesitar ninguna llamada a la API de Kubernetes.
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
