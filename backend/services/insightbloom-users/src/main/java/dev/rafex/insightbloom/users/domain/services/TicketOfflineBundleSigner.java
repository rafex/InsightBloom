package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.users.application.usecases.TicketDeviceUseCase.OfflineDevice;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Signs bounded, read-only public-key snapshots used by check-in scanners while disconnected. */
public final class TicketOfflineBundleSigner {
    public record SignedBundle(String payload, String signature, String publicKey, Instant expiresAt) {}
    private static final Duration MAX_AGE = Duration.ofHours(24);

    public SignedBundle sign(final String conferenceUuid, final List<OfflineDevice> devices, final Instant now) {
        final String privateValue = System.getenv("TICKET_OFFLINE_SIGNING_PRIVATE_KEY");
        final String publicValue = System.getenv("TICKET_OFFLINE_SIGNING_PUBLIC_KEY");
        if (privateValue == null || publicValue == null || privateValue.isBlank() || publicValue.isBlank())
            throw new IllegalStateException("offline_signing_not_configured");
        try {
            if (devices.size() > 5000) throw new IllegalStateException("offline_bundle_too_large");
            final byte[] privateBytes = Base64.getDecoder().decode(privateValue);
            final PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            final PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue)));
            final Instant expiresAt = now.plus(MAX_AGE);
            final List<Map<String, Object>> records = new ArrayList<>();
            for (final OfflineDevice device : devices) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put("ticketUuid", device.ticketUuid()); row.put("credentialUuid", device.credentialUuid());
                row.put("publicKeyJwk", device.publicKeyJwk()); row.put("maxDevices", device.maxDevices());
                records.add(row);
            }
            final Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("version", 1); payloadMap.put("conferenceUuid", conferenceUuid);
            payloadMap.put("issuedAt", now.getEpochSecond()); payloadMap.put("expiresAt", expiresAt.getEpochSecond());
            payloadMap.put("devices", records);
            final String payload = JsonUtils.toJson(payloadMap);
            final Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key); signer.update(payload.getBytes(StandardCharsets.UTF_8));
            final byte[] signature = signer.sign();
            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey); verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(signature)) throw new IllegalStateException("offline_signing_key_mismatch");
            return new SignedBundle(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(signature), publicValue, expiresAt);
        } catch (final IllegalStateException e) { throw e; }
        catch (final Exception e) { throw new IllegalStateException("offline_signing_not_configured", e); }
    }

    public boolean verify(final String payload, final String signatureValue) {
        final String publicValue = System.getenv("TICKET_OFFLINE_SIGNING_PUBLIC_KEY");
        if (publicValue == null || publicValue.isBlank() || payload == null || payload.length() > 5_000_000
                || signatureValue == null || signatureValue.length() > 256) return false;
        try {
            final var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicValue)));
            final byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureValue);
            final Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey); verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (final Exception e) { return false; }
    }
}
