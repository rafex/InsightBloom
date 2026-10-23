package dev.rafex.insightbloom.users.domain.services;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TicketDeviceCryptoTest {
    @Test
    void acceptsOnlyPublicP256JwkAndVerifiesWebCryptoRawSignature() throws Exception {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        final var pair = generator.generateKeyPair();
        final var publicKey = (ECPublicKey) pair.getPublic();
        final String jwk = "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"" + b64(fixed(publicKey.getW().getAffineX()))
                + "\",\"y\":\"" + b64(fixed(publicKey.getW().getAffineY())) + "\"}";
        final String payload = "insightbloom-ticket-v1|ticket|credential|1234567890";
        final Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(pair.getPrivate()); signer.update(payload.getBytes(StandardCharsets.UTF_8));
        final String rawSignature = b64(derToRaw(signer.sign()));

        assertTrue(TicketDeviceCrypto.validPublicJwk(jwk));
        assertFalse(TicketDeviceCrypto.validPublicJwk(jwk.replace("\"y\":", "\"d\":\"secret\",\"y\":")));
        assertTrue(TicketDeviceCrypto.verify(jwk, payload, rawSignature));
        assertFalse(TicketDeviceCrypto.verify(jwk, payload + "-changed", rawSignature));
        assertFalse(TicketDeviceCrypto.verify(jwk, payload, "not-a-signature"));
    }

    private static byte[] fixed(final BigInteger value) {
        final byte[] source = value.toByteArray();
        final byte[] result = new byte[32];
        final int start = source.length > 32 ? source.length - 32 : 0;
        System.arraycopy(source, start, result, 32 - (source.length - start), source.length - start);
        return result;
    }

    private static byte[] derToRaw(final byte[] der) {
        int offset = 2;
        if (der[offset++] != 0x02) throw new IllegalArgumentException("bad signature");
        final int rLength = der[offset++] & 0xff;
        final byte[] r = java.util.Arrays.copyOfRange(der, offset, offset + rLength); offset += rLength;
        if (der[offset++] != 0x02) throw new IllegalArgumentException("bad signature");
        final int sLength = der[offset++] & 0xff;
        final byte[] s = java.util.Arrays.copyOfRange(der, offset, offset + sLength);
        final byte[] raw = new byte[64];
        final int rStart = r.length > 32 ? r.length - 32 : 0;
        final int sStart = s.length > 32 ? s.length - 32 : 0;
        System.arraycopy(r, rStart, raw, 32 - (r.length - rStart), r.length - rStart);
        System.arraycopy(s, sStart, raw, 64 - (s.length - sStart), s.length - sStart);
        return raw;
    }

    private static String b64(final byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
