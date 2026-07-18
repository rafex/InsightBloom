package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.ConferenceStatus;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

import java.util.Optional;

/**
 * Activa/desactiva manualmente una conferencia (alterna entre ACTIVE/CLOSED). Solo aplica a
 * conferencias sin fecha de expiración — las que expiran se desactivan solas, por tiempo.
 */
public class SetConferenceActiveUseCase {
    private final ConferenceRepository conferenceRepository;

    public SetConferenceActiveUseCase(final ConferenceRepository conferenceRepository) {
        this.conferenceRepository = conferenceRepository;
    }

    /** Solo el creador puede activar/desactivar. */
    public Optional<Conference> execute(final String uuid, final String requestingUserUuid, final boolean active) {
        return conferenceRepository.findByUuid(uuid)
                .filter(c -> c.getCreatedByUserUuid().equals(requestingUserUuid))
                .filter(c -> c.getExpiresAt() == null)
                .map(c -> {
                    c.setStatus(active ? ConferenceStatus.ACTIVE : ConferenceStatus.CLOSED);
                    conferenceRepository.save(c);
                    return c;
                });
    }
}
