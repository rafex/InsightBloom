package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.GuestUserRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/** Cubre el ciclo completo del codigo de intercambio SSO del chat: mint (CreateSsoExchangeUseCase)
 *  -> canje (ConsumeSsoExchangeUseCase), incluyendo los mismos invariantes de un solo
 *  uso/TTL/firma que ya prueba GenerateWorkspaceDownloadUrlUseCaseTest para WorkspaceDownloadToken. */
class SsoExchangeTest {
    private ValidateTokenUseCase validateTokenUseCase;
    private SsoExchangeToken tokenCodec;
    private CreateSsoExchangeUseCase createUseCase;
    private ConsumeSsoExchangeUseCase consumeUseCase;

    @BeforeEach
    void setup() {
        final var tokenService = Mockito.mock(TokenService.class);
        final var userRepo = Mockito.mock(UserRepository.class);
        final var guestRepo = Mockito.mock(GuestUserRepository.class);
        validateTokenUseCase = Mockito.spy(new ValidateTokenUseCase(tokenService, userRepo, guestRepo));
        tokenCodec = new SsoExchangeToken("test-secret");
        createUseCase = new CreateSsoExchangeUseCase(validateTokenUseCase, tokenCodec);
        consumeUseCase = new ConsumeSsoExchangeUseCase(tokenCodec);
    }

    private void stubValidation(final ValidateTokenUseCase.ValidationResult result) {
        Mockito.doReturn(result).when(validateTokenUseCase).execute(Mockito.anyString());
    }

    @Test
    void mintThenConsumeRoundTrips() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "organizer", "2099-01-01T00:00:00Z"));

        final var minted = createUseCase.execute("raw-jwt");
        assertTrue(minted.isPresent());
        assertEquals(SsoExchangeToken.EXPIRY_SECONDS, minted.get().expiresInSeconds());

        final var consumed = consumeUseCase.execute(minted.get().code());
        assertTrue(consumed.valid());
        assertEquals("user-1", consumed.subjectUuid());
        assertEquals("user", consumed.kind());
        assertEquals("organizer", consumed.role());
    }

    @Test
    void codeIsSingleUse() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "organizer", null));

        final String code = createUseCase.execute("raw-jwt").orElseThrow().code();
        assertTrue(consumeUseCase.execute(code).valid());
        assertFalse(consumeUseCase.execute(code).valid()); // segundo canje: invalido
    }

    @Test
    void rejectsInvalidToken() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(false, null, null, null, null));

        assertTrue(createUseCase.execute("bad-jwt").isEmpty());
    }

    @Test
    void rejectsGuestToken() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(true, "guest-1", "guest", "guest", null));

        assertTrue(createUseCase.execute("guest-jwt").isEmpty());
    }

    @Test
    void consumeRejectsTamperedCode() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "organizer", null));

        final String code = createUseCase.execute("raw-jwt").orElseThrow().code();
        assertFalse(consumeUseCase.execute(code + "x").valid());
    }

    @Test
    void consumeRejectsCodeSignedWithDifferentSecret() {
        stubValidation(new ValidateTokenUseCase.ValidationResult(true, "user-1", "user", "organizer", null));

        final String code = createUseCase.execute("raw-jwt").orElseThrow().code();
        final var otherCodec = new SsoExchangeToken("other-secret");
        assertFalse(new ConsumeSsoExchangeUseCase(otherCodec).execute(code).valid());
    }
}
