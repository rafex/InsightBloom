package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.GuestUser;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

public class CreateGuestUseCase {
    private final GuestUserRepository guestUserRepository;
    private final ConferenceRepository conferenceRepository;
    private final TokenService tokenService;

    public CreateGuestUseCase(GuestUserRepository guestUserRepository,
                               ConferenceRepository conferenceRepository,
                               TokenService tokenService) {
        this.guestUserRepository = guestUserRepository;
        this.conferenceRepository = conferenceRepository;
        this.tokenService = tokenService;
    }

    public record GuestRequest(String displayName, String deviceFingerprint, String conferenceUuid) {}
    public record GuestResult(String token, String guestUuid, String displayName) {}

    public GuestResult execute(GuestRequest request) {
        // Verify conference exists
        conferenceRepository.findByUuid(request.conferenceUuid())
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        GuestUser guest = new GuestUser(request.displayName(), request.deviceFingerprint(), request.conferenceUuid());
        guestUserRepository.save(guest);

        Token token = tokenService.issueGuestToken(guest.getUuid());
        return new GuestResult(token.getTokenValue(), guest.getUuid(), guest.getDisplayName());
    }
}
