package dev.rafex.insightbloom.users.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.EventRoleRepository;
import dev.rafex.insightbloom.users.domain.ports.RoleRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.DeviceAccessGuard;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenerateJaasTokenUseCaseTest {

    private static String base64EnvelopedPem(final KeyPair keyPair) {
        final String base64Key = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        final String pem = "-----BEGIN PRIVATE KEY-----\n" + base64Key + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void notConfigured_whenNoPrivateKey() {
        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final DeviceAccessGuard deviceAccessGuard = Mockito.mock(DeviceAccessGuard.class);
        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);
        final var useCase = new GenerateJaasTokenUseCase(
                "app-id", "key-id", "", guard, userRepo, conferenceRepo, deviceAccessGuard);

        assertFalse(useCase.isConfigured());
        assertInstanceOf(GenerateJaasTokenUseCase.JaasResult.NotConfigured.class,
                useCase.execute("event-1", "user-1", "attendee", null));
    }

    @Test
    void generatesValidRs256TokenSignedWithProvidedKey() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        final KeyPair keyPair = generator.generateKeyPair();

        final EventRoleRepository eventRoleRepo = Mockito.mock(EventRoleRepository.class);
        final RoleRepository roleRepo = Mockito.mock(RoleRepository.class);
        final UserRepository userRepo = Mockito.mock(UserRepository.class);
        Mockito.when(userRepo.findByUuid("user-1")).thenReturn(Optional.of(
                new User("user-1", "jdoe", "Jane Doe", "jdoe@example.com", UserRole.ATTENDEE)));
        final ConferenceRepository conferenceRepo = Mockito.mock(ConferenceRepository.class);
        final DeviceAccessGuard deviceAccessGuard = Mockito.mock(DeviceAccessGuard.class);

        final var guard = new EventPermissionGuard(eventRoleRepo, roleRepo);
        final var useCase = new GenerateJaasTokenUseCase("vpaas-magic-cookie-test", "key-id-1",
                base64EnvelopedPem(keyPair), guard, userRepo, conferenceRepo, deviceAccessGuard);

        assertTrue(useCase.isConfigured());
        // deviceFingerprint=null -- se omite el control de acceso por dispositivo (ver comentario
        // en GenerateJaasTokenUseCase.execute), este test cubre solo la firma del JWT.
        final var result = useCase.execute("conference-uuid-1", "user-1", "attendee", null);
        assertInstanceOf(GenerateJaasTokenUseCase.JaasResult.Issued.class, result);

        final var jaasToken = ((GenerateJaasTokenUseCase.JaasResult.Issued) result).token();
        assertEquals("vpaas-magic-cookie-test", jaasToken.appId());
        assertEquals("insightbloom-conferenceuuid1", jaasToken.roomName());

        final String[] parts = jaasToken.token().split("\\.");
        assertEquals(3, parts.length);

        final String data = parts[0] + "." + parts[1];
        final byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        final Signature verifier = Signature.getInstance("SHA256withRSA");
        final PublicKey publicKey = keyPair.getPublic();
        verifier.initVerify(publicKey);
        verifier.update(data.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(signatureBytes));

        final var mapper = new ObjectMapper();
        final var payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertEquals("jitsi", payload.get("aud").asText());
        assertEquals("false", payload.get("context").get("user").get("moderator").asText());
    }
}
