package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;

public class CountAttendeesUseCase {
    private final GuestUserRepository guestUserRepository;

    public CountAttendeesUseCase(final GuestUserRepository guestUserRepository) {
        this.guestUserRepository = guestUserRepository;
    }

    public long execute(final String conferenceUuid) {
        return guestUserRepository.countByConference(conferenceUuid);
    }
}
