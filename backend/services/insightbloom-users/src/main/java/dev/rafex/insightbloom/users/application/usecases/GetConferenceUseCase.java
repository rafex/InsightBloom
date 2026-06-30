package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.CascadeDeletePort;
import dev.rafex.insightbloom.users.domain.ports.ConferenceMembershipRepository;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.List;
import java.util.Optional;

public class GetConferenceUseCase {
    private final ConferenceRepository conferenceRepository;
    private final CascadeDeletePort cascadeDeletePort;
    private final ConferenceMembershipRepository membershipRepository;

    public GetConferenceUseCase(ConferenceRepository conferenceRepository, CascadeDeletePort cascadeDeletePort,
                                 ConferenceMembershipRepository membershipRepository) {
        this.conferenceRepository = conferenceRepository;
        this.cascadeDeletePort = cascadeDeletePort;
        this.membershipRepository = membershipRepository;
    }

    public Optional<Conference> byId(String uuid) {
        return conferenceRepository.findByUuid(uuid);
    }

    public Optional<Conference> byFriendlyId(String friendlyId) {
        return conferenceRepository.findByFriendlyId(friendlyId);
    }

    public Optional<Conference> byShortCode(String shortCode) {
        return conferenceRepository.findByShortCode(shortCode);
    }

    public List<Conference> byUser(String userUuid) {
        return conferenceRepository.findByUser(userUuid);
    }

    private static final java.util.regex.Pattern UUID_RE = java.util.regex.Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern SHORT_RE = java.util.regex.Pattern.compile(
            "^[0-9a-f]{7}$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Resuelve un identificador de conferencia sea UUID completo, short code (7 hex) o friendly-id. */
    public Optional<Conference> resolveAny(final String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        final String trimmed = identifier.trim();
        if (UUID_RE.matcher(trimmed).matches()) {
            final var byUuid = byId(trimmed);
            if (byUuid.isPresent()) return byUuid;
        }
        if (SHORT_RE.matcher(trimmed).matches()) {
            final var byShort = byShortCode(trimmed);
            if (byShort.isPresent()) return byShort;
        }
        return byFriendlyId(trimmed);
    }

    public boolean delete(String uuid, String requestingUserUuid) {
        return conferenceRepository.findByUuid(uuid)
            .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
            .map(c -> {
                conferenceRepository.delete(uuid);
                membershipRepository.deleteByConference(uuid);
                try {
                    cascadeDeletePort.deleteConferenceData(uuid);
                } catch (final Exception e) {
                    System.err.println("cascade_delete_error conference=" + uuid + " error=" + e.getMessage());
                }
                return true;
            })
            .orElse(false);
    }
}
