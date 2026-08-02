package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

/**
 * Estado de los tres pools (Web/CLI Neovim/CLI LazyVim) de una conferencia, SIN comprometer
 * ningun sandbox --
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

    public record Availability(VariantAvailability web, VariantAvailability cli,
                               VariantAvailability cliLazyVim) {
    }

    /**
     * @param userUuid usuario que consulta -- si ya tiene un sandbox propio en una variante, esa
     *                 variante se marca {@code available=true} sin importar el cupo (para que
     *                 pueda reconectarse a su propio workspace aunque el pool esté lleno con SU
     *                 PROPIO asiento; bug real detectado en producción 2026-07-19: sin esto, el
     *                 dueño del único asiento de un pool de tamaño 1 quedaba con el botón
     *                 deshabilitado y no podía volver a entrar a su propio sandbox).
     */
    public Availability execute(final String conferenceUuid, final String userUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        final var active = sandboxRepository.findByConferenceUuid(conferenceUuid);
        int webCount = 0;
        int cliCount = 0;
        int cliLazyVimCount = 0;
        // Una fila con userUuid == null representa un Pod preprovisionado, no una plaza
        // ocupada. El botón "Preparar sandboxes" crea precisamente esas filas libres.
        for (final Sandbox s : active) {
            if (s.getUserUuid() == null) continue;
            if (Sandbox.VARIANT_CLI.equals(s.getVariant())) {
                cliCount++;
            } else if (Sandbox.VARIANT_CLI_LAZYVIM.equals(s.getVariant())) {
                cliLazyVimCount++;
            } else {
                webCount++;
            }
        }

        final String ownVariant = sandboxRepository.findByConferenceAndUser(conferenceUuid, userUuid)
                .map(Sandbox::getVariant).orElse(null);

        final int webPoolSize = conference.getSandboxPoolSize() != null ? conference.getSandboxPoolSize() : DEFAULT_POOL_SIZE;
        final int cliPoolSize = conference.getSandboxCliPoolSize() != null ? conference.getSandboxCliPoolSize() : DEFAULT_POOL_SIZE;
        final int cliLazyVimPoolSize = conference.getSandboxCliLazyVimPoolSize() != null
                ? conference.getSandboxCliLazyVimPoolSize() : 0;
        final int cliSeatsPerPod = conference.getSandboxSeatsPerPod() != null
                ? conference.getSandboxSeatsPerPod() : DEFAULT_SEATS_PER_POD;

        final int webCapacity = webPoolSize; // 1 asiento por pod siempre en "web"
        final int cliCapacity = cliPoolSize * cliSeatsPerPod;
        final int cliLazyVimCapacity = cliLazyVimPoolSize * cliSeatsPerPod;

        final boolean webAvailable = webCount < webCapacity || Sandbox.VARIANT_WEB.equals(ownVariant);
        final boolean cliAvailable = cliCount < cliCapacity || Sandbox.VARIANT_CLI.equals(ownVariant);
        final boolean cliLazyVimAvailable = cliLazyVimCount < cliLazyVimCapacity
                || Sandbox.VARIANT_CLI_LAZYVIM.equals(ownVariant);

        return new Availability(
            new VariantAvailability(webAvailable, webCount, webCapacity),
            new VariantAvailability(cliAvailable, cliCount, cliCapacity),
            new VariantAvailability(cliLazyVimAvailable, cliLazyVimCount, cliLazyVimCapacity)
        );
    }
}
