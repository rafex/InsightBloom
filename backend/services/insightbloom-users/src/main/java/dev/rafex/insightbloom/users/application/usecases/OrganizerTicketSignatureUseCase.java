package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import dev.rafex.insightbloom.users.domain.model.TicketStatus;
import dev.rafex.insightbloom.users.domain.ports.OrganizerTicketSignatureRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketRepository;
import dev.rafex.insightbloom.users.domain.ports.TicketDeviceRepository;
import dev.rafex.insightbloom.users.domain.services.CoseSign1Ed25519;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/** Event-scoped organizer key registration and individual ticket COSE verification. */
public final class OrganizerTicketSignatureUseCase {
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private final OrganizerTicketSignatureRepository signatures;
    private final TicketRepository tickets;
    private final TicketDeviceRepository devices;

    public OrganizerTicketSignatureUseCase(final OrganizerTicketSignatureRepository signatures, final TicketRepository tickets,
                                           final TicketDeviceRepository devices) {
        this.signatures = signatures; this.tickets = tickets; this.devices = devices;
    }

    public record KeyStatus(String keyId, String publicKeySpki, String state, Instant createdAt) {}
    public record PendingTicketSignature(String ticketUuid, String payload, String payloadBase64Url, Instant issuedAt) {}
    public record OfflineManifest(String payload, String payloadBase64Url, String keyId, Instant expiresAt) {}
    public record SignedOfflinePackage(String payload, String coseSign1, String keyId, String publicKeySpki, Instant expiresAt) {}

    public KeyStatus keyStatus(final String conferenceUuid) {
        return signatures.activeKey(conferenceUuid).map(k -> new KeyStatus(k.keyId(), k.publicKeySpki(), k.state(), k.createdAt())).orElse(null);
    }

    public KeyStatus registerKey(final String conferenceUuid, final String keyId, final String publicKeySpki,
                                 final String proofBase64Url, final String actorUuid) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) throw new IllegalArgumentException("organizer_ticket_key_id_invalid");
        try {
            final byte[] encodedKey = Base64.getUrlDecoder().decode(publicKeySpki);
            if (encodedKey.length < 40 || encodedKey.length > 256) throw new IllegalArgumentException();
            final var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encodedKey));
            final byte[] proof = Base64.getUrlDecoder().decode(proofBase64Url);
            final String challenge = "insightbloom-event-key-proof-v1|" + conferenceUuid + "|" + keyId;
            final Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(publicKey);
            verifier.update(challenge.getBytes(StandardCharsets.UTF_8));
            if (proof.length != 64 || !verifier.verify(proof)) throw new IllegalArgumentException("organizer_ticket_key_proof_invalid");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("organizer_ticket_public_key_invalid", e); }
        signatures.registerKey(conferenceUuid, keyId, publicKeySpki, actorUuid, Instant.now());
        signatures.invalidateOfflinePackage(conferenceUuid);
        return keyStatus(conferenceUuid);
    }

    public List<PendingTicketSignature> pending(final String conferenceUuid) {
        if (signatures.activeKey(conferenceUuid).isEmpty()) throw new IllegalStateException("organizer_ticket_signing_not_configured");
        final List<PendingTicketSignature> result = new ArrayList<>();
        for (final Ticket ticket : tickets.findByConference(conferenceUuid)) {
            if (ticket.getStatus() != TicketStatus.PENDING_SIGNATURE
                    || ticket.isOperational() || signatures.hasTicketSignature(conferenceUuid, ticket.getUuid())) continue;
            final String payload = canonicalTicketPayload(ticket);
            result.add(new PendingTicketSignature(ticket.getUuid(), payload,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)), ticket.getIssuedAt()));
        }
        return List.copyOf(result);
    }

    public boolean acceptTicketSignature(final String conferenceUuid, final String ticketUuid,
                                         final String coseBase64Url, final String actorUuid) {
        final Ticket ticket = tickets.findByUuid(ticketUuid).filter(t -> conferenceUuid.equals(t.getConferenceUuid()))
                .orElseThrow(() -> new IllegalArgumentException("ticket_not_found"));
        if (ticket.getStatus() != TicketStatus.PENDING_SIGNATURE) {
            if (signatures.ticketSignature(conferenceUuid, ticketUuid).filter(coseBase64Url::equals).isPresent()) return true;
            throw new IllegalStateException("ticket_not_signable");
        }
        if (ticket.isOperational() || ticket.getStatus() != TicketStatus.PENDING_SIGNATURE)
            throw new IllegalStateException("ticket_not_signable");
        final byte[] expected = canonicalTicketPayload(ticket).getBytes(StandardCharsets.UTF_8);
        final String keyId = verifyCose(conferenceUuid, coseBase64Url, expected, true);
        if (!signatures.saveTicketSignature(conferenceUuid, ticketUuid, keyId, coseBase64Url, actorUuid, Instant.now())
                && !signatures.hasTicketSignature(conferenceUuid, ticketUuid)) throw new IllegalStateException("ticket_signature_store_failed");
        return true;
    }

    public void saveOfflinePackage(final String conferenceUuid, final String expectedPayload,
                                   final String coseBase64Url, final String actorUuid) {
        final String draft = signatures.offlineDraft(conferenceUuid, Instant.now())
                .orElseThrow(() -> new IllegalStateException("offline_manifest_expired"));
        if (!java.security.MessageDigest.isEqual(draft.getBytes(StandardCharsets.UTF_8), expectedPayload.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("offline_manifest_stale");
        final byte[] payload = expectedPayload.getBytes(StandardCharsets.UTF_8);
        final String keyId = verifyCose(conferenceUuid, coseBase64Url, payload, true);
        final MapFields fields = manifestTimes(expectedPayload);
        final Instant now = Instant.now();
        if (fields.issuedAt().isAfter(now.plusSeconds(60)) || !fields.expiresAt().isAfter(now)
                || fields.expiresAt().isAfter(fields.issuedAt().plusSeconds(24 * 60 * 60)))
            throw new IllegalArgumentException("offline_bundle_expiry_invalid");
        signatures.saveOfflinePackage(new OrganizerTicketSignatureRepository.OfflinePackage(
                conferenceUuid, keyId, expectedPayload, coseBase64Url, fields.issuedAt(), fields.expiresAt()), actorUuid);
        signatures.clearOfflineDraft(conferenceUuid);
    }

    public OfflineManifest createOfflineManifest(final String conferenceUuid) {
        final var active = signatures.activeKey(conferenceUuid).orElseThrow(() -> new IllegalStateException("organizer_ticket_signing_not_configured"));
        final Instant issued = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        final Instant expires = issued.plusSeconds(24 * 60 * 60);
        final StringBuilder json = new StringBuilder(1024);
        json.append("{\"version\":2,\"conferenceUuid\":\"").append(escape(conferenceUuid))
                .append("\",\"keyId\":\"").append(escape(active.keyId())).append("\",\"organizerPublicKeySpki\":\"")
                .append(escape(active.publicKeySpki())).append("\",\"issuedAt\":").append(issued.getEpochSecond())
                .append(",\"expiresAt\":").append(expires.getEpochSecond()).append(",\"organizerKeys\":[");
        boolean first = true;
        for (final var eventKey : signatures.keys(conferenceUuid)) {
            if (!first) json.append(','); first = false;
            json.append("{\"keyId\":\"").append(escape(eventKey.keyId())).append("\",\"publicKeySpki\":\"")
                    .append(escape(eventKey.publicKeySpki())).append("\",\"state\":\"").append(eventKey.state()).append("\"}");
        }
        json.append("],\"tickets\":[");
        first = true;
        for (final Ticket ticket : tickets.findByConference(conferenceUuid)) {
            if (ticket.isOperational()) continue;
            if (ticket.getStatus() == TicketStatus.PENDING_SIGNATURE)
                throw new IllegalStateException("ticket_signatures_pending");
            if (!first) json.append(','); first = false;
            json.append("{\"ticketUuid\":\"").append(escape(ticket.getUuid())).append("\",\"status\":\"")
                    .append(ticket.getStatus().name()).append("\",\"issuedAt\":\"").append(ticket.getIssuedAt())
                    .append("\",\"seatUuid\":").append(ticket.getSeatUuid() == null ? "null" : "\"" + escape(ticket.getSeatUuid()) + "\"")
                    .append(",\"coseSign1\":").append(signatures.ticketSignature(conferenceUuid, ticket.getUuid())
                            .map(value -> "\"" + escape(value) + "\"").orElse("null")).append('}');
        }
        json.append("],\"devices\":["); first = true;
        for (final var device : devices.listActiveForConference(conferenceUuid)) {
            final Ticket ticket = tickets.findByUuid(device.ticketUuid()).orElse(null);
            if (ticket == null || ticket.getStatus() == TicketStatus.REVOKED || ticket.getStatus() == TicketStatus.EXPIRED) continue;
            if (!first) json.append(','); first = false;
            json.append("{\"ticketUuid\":\"").append(escape(device.ticketUuid())).append("\",\"credentialUuid\":\"")
                    .append(escape(device.uuid())).append("\",\"publicKeyJwk\":\"").append(escape(device.publicKeyJwk())).append("\"}");
        }
        json.append("]}");
        final String payload = json.toString();
        signatures.saveOfflineDraft(conferenceUuid, payload, Instant.now().plusSeconds(10 * 60));
        return new OfflineManifest(payload, Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)),
                active.keyId(), expires);
    }

    public void invalidateOfflinePackage(final String conferenceUuid) { signatures.invalidateOfflinePackage(conferenceUuid); }

    public void revokeActiveKey(final String conferenceUuid, final String actorUuid) {
        if (!signatures.revokeActiveKey(conferenceUuid, actorUuid, Instant.now())) throw new IllegalStateException("organizer_ticket_signing_not_configured");
        signatures.invalidateOfflinePackage(conferenceUuid);
    }

    public SignedOfflinePackage offlinePackage(final String conferenceUuid) {
        final var saved = signatures.offlinePackage(conferenceUuid).filter(p -> p.expiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new IllegalStateException("offline_bundle_not_prepared"));
        final var key = signatures.key(conferenceUuid, saved.keyId()).orElseThrow(() -> new IllegalStateException("offline_bundle_key_missing"));
        return new SignedOfflinePackage(saved.payload(), saved.coseSign1(), saved.keyId(), key.publicKeySpki(), saved.expiresAt());
    }

    public boolean isStoredOfflinePackage(final String conferenceUuid, final String payload, final String coseSign1) {
        return signatures.isKnownOfflinePackage(conferenceUuid, payload, coseSign1, Instant.now());
    }

    private String verifyCose(final String conferenceUuid, final String coseBase64Url, final byte[] expected,
                              final boolean requireActiveKey) {
        final var activeKey = signatures.activeKey(conferenceUuid).orElseThrow(() -> new IllegalStateException("organizer_ticket_signing_not_configured"));
        final byte[] cose;
        try { cose = Base64.getUrlDecoder().decode(coseBase64Url); }
        catch (Exception e) { throw new IllegalArgumentException("cose_structure_invalid"); }
        final CoseSign1Ed25519.Parts parts = CoseSign1Ed25519.decode(cose);
        final String keyId = new String(parts.keyId(), StandardCharsets.UTF_8);
        if (requireActiveKey && !activeKey.keyId().equals(keyId)) throw new IllegalArgumentException("organizer_ticket_key_mismatch");
        if (!java.security.MessageDigest.isEqual(expected, parts.payload())) throw new IllegalArgumentException("ticket_signature_payload_mismatch");
        try {
            final var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                    new X509EncodedKeySpec(Base64.getUrlDecoder().decode(activeKey.publicKeySpki())));
            final Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(publicKey);
            verifier.update(CoseSign1Ed25519.signatureInput(expected));
            if (!verifier.verify(parts.signature())) throw new IllegalArgumentException("ticket_signature_invalid");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("ticket_signature_verification_failed", e); }
        return keyId;
    }

    private record MapFields(Instant issuedAt, Instant expiresAt) {}
    private static MapFields manifestTimes(final String payload) {
        try {
            final var issued = java.util.regex.Pattern.compile("\\\"issuedAt\\\":(\\d+)").matcher(payload);
            final var expires = java.util.regex.Pattern.compile("\\\"expiresAt\\\":(\\d+)").matcher(payload);
            if (!issued.find() || !expires.find()) throw new IllegalArgumentException("offline_bundle_payload_invalid");
            return new MapFields(Instant.ofEpochSecond(Long.parseLong(issued.group(1))), Instant.ofEpochSecond(Long.parseLong(expires.group(1))));
        } catch (NumberFormatException e) { throw new IllegalArgumentException("offline_bundle_payload_invalid"); }
    }

    private static String escape(final String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String canonicalTicketPayload(final Ticket ticket) {
        final String seat = ticket.getSeatUuid() == null ? "null" : "\"" + ticket.getSeatUuid() + "\"";
        return "{\"version\":1,\"conferenceUuid\":\"" + ticket.getConferenceUuid()
                + "\",\"ticketUuid\":\"" + ticket.getUuid() + "\",\"ticketType\":\""
                + (ticket.getSeatUuid() == null ? "GENERAL" : "SEATED") + "\",\"seatUuid\":" + seat
                + ",\"issuedAt\":\"" + ticket.getIssuedAt() + "\"}";
    }
}
