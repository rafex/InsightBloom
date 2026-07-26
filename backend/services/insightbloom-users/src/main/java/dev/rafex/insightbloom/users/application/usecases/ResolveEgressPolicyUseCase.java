package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.EgressPolicy;
import dev.rafex.insightbloom.users.domain.model.PlatformSettings;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EgressPolicyRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Resuelve, para la IP de origen de un sandbox, la política de egress efectiva que debe aplicar
 * el egress-proxy (allowed/blocked ya combinados) -- ver InternalEgressPolicyHandler, llamado por
 * el proxy en cada conexión.
 *
 * Herencia en capas (Opción C, sin excepciones): {@code allowed = global ∪ evento},
 * {@code blocked = global ∪ evento}. BLOCKED SIEMPRE GANA sobre allowed en cualquier nivel -- un
 * evento no puede "desbloquear" algo que la plataforma prohíbe, solo puede agregar bloqueos
 * propios. Un cambio al blocklist global se propaga de inmediato a todos los eventos activos, sin
 * tocarlos uno por uno (a diferencia de {@code AiDefaultsSeeder}, que es "seed-then-independent"
 * -- acá la capa global sigue viva siempre, es una decisión de seguridad, no de autonomía por
 * evento).
 */
public class ResolveEgressPolicyUseCase {
    private final SandboxOrchestrator sandboxOrchestrator;
    private final ConferenceRepository conferenceRepository;
    private final PlatformSettingsRepository platformSettingsRepository;
    private final EgressPolicyRepository egressPolicyRepository;

    public ResolveEgressPolicyUseCase(final SandboxOrchestrator sandboxOrchestrator,
                                       final ConferenceRepository conferenceRepository,
                                       final PlatformSettingsRepository platformSettingsRepository,
                                       final EgressPolicyRepository egressPolicyRepository) {
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.conferenceRepository = conferenceRepository;
        this.platformSettingsRepository = platformSettingsRepository;
        this.egressPolicyRepository = egressPolicyRepository;
    }

    public record Resolution(String conferenceUuid, boolean internetEnabled, Set<String> allowed, Set<String> blocked) {
    }

    public Optional<Resolution> execute(final String sourceIp) {
        final Optional<String> conferenceUuid = sandboxOrchestrator.findConferenceUuidByPodIp(sourceIp);
        if (conferenceUuid.isEmpty()) return Optional.empty();
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid.get()).orElse(null);
        if (conference == null) return Optional.empty();
        final boolean internetEnabled = conference.getSandboxInternetEnabled() != null
                && conference.getSandboxInternetEnabled() == 1;

        final PlatformSettings global = platformSettingsRepository.get();
        final EgressPolicy event = egressPolicyRepository.findByConference(conferenceUuid.get()).orElse(null);

        final Set<String> allowed = union(global.getEgressAllowedHosts(), event == null ? null : event.allowedHosts());
        final Set<String> blocked = union(global.getEgressBlockedHosts(), event == null ? null : event.blockedHosts());
        return Optional.of(new Resolution(conferenceUuid.get(), internetEnabled, allowed, blocked));
    }

    private static Set<String> union(final String csvA, final String csvB) {
        final Set<String> result = new LinkedHashSet<>();
        addAll(result, csvA);
        addAll(result, csvB);
        return result;
    }

    private static void addAll(final Set<String> target, final String csv) {
        if (csv == null || csv.isBlank()) return;
        for (final String item : csv.split(",")) {
            final String trimmed = item.strip();
            if (!trimmed.isEmpty()) target.add(trimmed.toLowerCase());
        }
    }
}
