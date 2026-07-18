package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/**
 * Estado de los dos pools (Web/CLI) de una conferencia, SIN comprometer ningun sandbox --
 * a diferencia de {@link AssignSandboxUseCase#execute}, esto solo lee (Conference + conteo de
 * filas en sandbox_assignments), nunca llama a Kubernetes. Pensado para el picker Web/CLI del
 * alumno (IdePage.vue): se consulta ANTES de elegir, para poder deshabilitar el boton de la
 * variante que ya esta agotada.
 */
public class GetSandboxAvailabilityUseCase {
    private static final int DEFAULT_POOL_SIZE = 1;
    private static final int DEFAULT_SEATS_PER_POD = 4;

    private final ConferenceRepository conferenceRepository;
    private final SandboxRepository sandboxRepository;

    public GetSandboxAvailabilityUseCase(final ConferenceRepository conferenceRepository,
                                          final SandboxRepository sandboxRepository) {
        this.conferenceRepository = conferenceRepository;
        this.sandboxRepository = sandboxRepository;
    }

    public record VariantAvailability(boolean available, int activeCount, int capacity) {
    }

    public record Availability(VariantAvailability web, VariantAvailability cli) {
    }

    public Availability execute(final String conferenceUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        final var active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        int webCount = 0;
        int cliCount = 0;
        for (final Sandbox s : active) {
            if (Sandbox.VARIANT_CLI.equals(s.getVariant())) {
                cliCount++;
            } else {
                webCount++;
            }
        }

        final int webPoolSize = conference.getSandboxPoolSize() != null ? conference.getSandboxPoolSize() : DEFAULT_POOL_SIZE;
        final int cliPoolSize = conference.getSandboxCliPoolSize() != null ? conference.getSandboxCliPoolSize() : DEFAULT_POOL_SIZE;
        final int cliSeatsPerPod = conference.getSandboxSeatsPerPod() != null
                ? conference.getSandboxSeatsPerPod() : DEFAULT_SEATS_PER_POD;

        final int webCapacity = webPoolSize; // 1 asiento por pod siempre en "web"
        final int cliCapacity = cliPoolSize * cliSeatsPerPod;

        return new Availability(
            new VariantAvailability(webCount < webCapacity, webCount, webCapacity),
            new VariantAvailability(cliCount < cliCapacity, cliCount, cliCapacity)
        );
    }
}
