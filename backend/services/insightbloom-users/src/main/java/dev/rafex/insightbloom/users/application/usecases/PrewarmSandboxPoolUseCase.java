package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;

/**
 * Prepara explícitamente el pool configurado de IDE antes de que entren los asistentes. La
 * creación del Pod sigue siendo asíncrona en Kubernetes: este caso de uso solo crea los recursos
 * idempotentes y el dashboard consulta después su fase/ready mediante sandbox-status.
 */
public class PrewarmSandboxPoolUseCase {
    private static final int DEFAULT_POOL_SIZE = 1;

    private final ConferenceRepository conferenceRepository;
    private final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase;

    public PrewarmSandboxPoolUseCase(final ConferenceRepository conferenceRepository,
                                     final EnsureUnassignedSandboxUseCase ensureUnassignedSandboxUseCase) {
        this.conferenceRepository = conferenceRepository;
        this.ensureUnassignedSandboxUseCase = ensureUnassignedSandboxUseCase;
    }

    public record VariantResult(String variant, int desiredPods, int createdPods) {
    }

    public record Result(String conferenceUuid, List<VariantResult> variants) {
    }

    public Result execute(final String conferenceUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        final int webPoolSize = configuredPoolSize(conference.getSandboxPoolSize());
        final int cliPoolSize = configuredPoolSize(conference.getSandboxCliPoolSize());
        final int webCreated = ensureUnassignedSandboxUseCase.ensurePool(
            conferenceUuid, Sandbox.VARIANT_WEB, webPoolSize);
        final int cliCreated = ensureUnassignedSandboxUseCase.ensurePool(
            conferenceUuid, Sandbox.VARIANT_CLI, cliPoolSize);
        return new Result(conferenceUuid, List.of(
            new VariantResult(Sandbox.VARIANT_WEB, webPoolSize, webCreated),
            new VariantResult(Sandbox.VARIANT_CLI, cliPoolSize, cliCreated)
        ));
    }

    private static int configuredPoolSize(final Integer configured) {
        return configured != null ? configured : DEFAULT_POOL_SIZE;
    }
}
