package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.json.JsonUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.Map;

/** Validates Web Crypto ECDSA P-256 public JWKs and raw IEEE-P1363 signatures. */
public final class TicketDeviceCrypto {
    private TicketDeviceCrypto() {}

    public static boolean validPublicJwk(final String json) {
        try { publicKey(json); return true; }
        catch (final Exception ignored) { return false; }
    }

    public static boolean verify(final String publicJwk, final String payload, final String rawSignature) {
        try {
            final byte[] raw = Base64.getUrlDecoder().decode(rawSignature);
            if (raw.length != 64) return false;
            final BigInteger r = new BigInteger(1, java.util.Arrays.copyOfRange(raw, 0, 32));
            final BigInteger s = new BigInteger(1, java.util.Arrays.copyOfRange(raw, 32, 64));
            final Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey(publicJwk));
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(der(r, s));
        } catch (final Exception ignored) { return false; }
    }

    @SuppressWarnings("unchecked")
    private static ECPublicKey publicKey(final String json) throws Exception {
        final Map<String, Object> jwk = JsonUtils.codec().readValue(json, Map.class);
        if (!"EC".equals(jwk.get("kty")) || !"P-256".equals(jwk.get("crv"))
                || jwk.containsKey("d") || !(jwk.get("x") instanceof String x) || !(jwk.get("y") instanceof String y)) {
            throw new IllegalArgumentException("ticket_device_public_key_invalid");
        }
        final AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        final var curve = parameters.getParameterSpec(java.security.spec.ECParameterSpec.class);
        final byte[] xb = Base64.getUrlDecoder().decode(x);
        final byte[] yb = Base64.getUrlDecoder().decode(y);
        if (xb.length != 32 || yb.length != 32) throw new IllegalArgumentException("ticket_device_public_key_invalid");
        final var spec = new ECPublicKeySpec(new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb)), curve);
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
    }

    private static byte[] der(final BigInteger r, final BigInteger s) {
        final byte[] rb = r.toByteArray(), sb = s.toByteArray();
        final int length = 2 + rb.length + 2 + sb.length;
        final byte[] out = new byte[2 + length];
        out[0] = 0x30; out[1] = (byte) length; out[2] = 0x02; out[3] = (byte) rb.length;
        System.arraycopy(rb, 0, out, 4, rb.length);
        final int p = 4 + rb.length; out[p] = 0x02; out[p + 1] = (byte) sb.length;
        System.arraycopy(sb, 0, out, p + 2, sb.length);
        return out;
    }
}
