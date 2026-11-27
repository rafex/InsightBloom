package dev.rafex.insightbloom.users.application.usecases;

/** Internal site signer. Implementations must return only public metadata and signatures. */
public interface PlatformTicketSigner {
    record Signed(String keyId, String publicKeySpki, String signatureBase64Url) {}
    Signed sign(String conferenceUuid, String canonicalPayload);
}
