package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.GuestUser;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;
import dev.rafex.insightbloom.users.domain.ports.PlatformSettingsRepository;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceBlockedException;
import dev.rafex.insightbloom.users.domain.services.PlatformDeviceGuard;
import dev.rafex.insightbloom.users.domain.services.TokenService;

public class CreateGuestUseCase {
    private final GuestUserRepository guestUserRepository;
    private final ConferenceRepository conferenceRepository;
    private final TokenService tokenService;
    private final PlatformDeviceGuard platformDeviceGuard;
    private final PlatformSettingsRepository platformSettingsRepository;

    public CreateGuestUseCase(GuestUserRepository guestUserRepository,
                               ConferenceRepository conferenceRepository,
                               TokenService tokenService,
                               PlatformDeviceGuard platformDeviceGuard,
                               PlatformSettingsRepository platformSettingsRepository) {
        this.guestUserRepository = guestUserRepository;
        this.conferenceRepository = conferenceRepository;
        this.tokenService = tokenService;
        this.platformDeviceGuard = platformDeviceGuard;
        this.platformSettingsRepository = platformSettingsRepository;
    }

    public record GuestRequest(String displayName, String deviceFingerprint, String conferenceUuid) {}
    public record GuestResult(String token, String guestUuid, String displayName, String expiresAt) {}

    public GuestResult execute(GuestRequest request) {
        // Verify conference exists
        conferenceRepository.findByUuid(request.conferenceUuid())
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        GuestUser guest = new GuestUser(request.displayName(), request.deviceFingerprint(), request.conferenceUuid());
        final var access = platformDeviceGuard.checkAndRegisterLogin(
                request.deviceFingerprint(), guest.getUuid(), TokenKind.GUEST, platformSettingsRepository.get());
        if (access instanceof PlatformDeviceGuard.Result.Blocked) {
            throw new PlatformDeviceBlockedException();
        }

        // Check before persisting the guest or minting a token. Blocked devices
        // must not be able to amplify database rows and token creation.
        guestUserRepository.save(guest);
        Token token = tokenService.issueGuestToken(guest.getUuid(), request.deviceFingerprint());

        return new GuestResult(token.getTokenValue(), guest.getUuid(), guest.getDisplayName(),
                token.getExpiresAt().toString());
    }
}
