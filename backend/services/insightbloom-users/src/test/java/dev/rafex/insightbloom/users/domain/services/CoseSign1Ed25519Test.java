package dev.rafex.insightbloom.users.domain.services;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import static org.junit.jupiter.api.Assertions.*;

class CoseSign1Ed25519Test {
    @Test
    void encodesAndVerifiesOrganizerCoseSign1() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] payload = "{\"version\":1,\"conferenceUuid\":\"event-1\"}".getBytes(StandardCharsets.UTF_8);
        final var signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate()); signer.update(CoseSign1Ed25519.signatureInput(payload));
        final byte[] cose = CoseSign1Ed25519.encode("event-key-1".getBytes(StandardCharsets.UTF_8), payload, signer.sign());

        final var parts = CoseSign1Ed25519.decode(cose);
        assertArrayEquals(payload, parts.payload());
        assertEquals("event-key-1", new String(parts.keyId(), StandardCharsets.UTF_8));
        final var verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(keyPair.getPublic()); verifier.update(CoseSign1Ed25519.signatureInput(parts.payload()));
        assertTrue(verifier.verify(parts.signature()));
    }

    @Test
    void rejectsWrongAlgorithmAndTrailingBytes() throws Exception {
        final var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        final byte[] payload = new byte[] {1};
        final var signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate()); signer.update(CoseSign1Ed25519.signatureInput(payload));
        final byte[] valid = CoseSign1Ed25519.encode(new byte[] {1}, payload, signer.sign());
        final byte[] wrongAlgorithm = valid.clone(); wrongAlgorithm[4] = 0x26;
        assertEquals("cose_algorithm_invalid", assertThrows(IllegalArgumentException.class,
                () -> CoseSign1Ed25519.decode(wrongAlgorithm)).getMessage());
        final byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> CoseSign1Ed25519.decode(trailing));
    }
}
