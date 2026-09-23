package dev.rafex.insightbloom.users.adapters.inbound.http.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpClientMetadataTest {
    @Test
    void usesLastValidForwardedHopSoClientSuppliedPrefixCannotSpoofAddress() {
        assertEquals("198.51.100.23", OtpClientMetadata.lastForwardedAddress(
                "192.0.2.99, 198.51.100.23").orElseThrow());
    }

    @Test
    void acceptsIpv6Address() {
        assertEquals("2001:db8::1", OtpClientMetadata.lastForwardedAddress("198.51.100.1, 2001:db8::1").orElseThrow());
    }

    @Test
    void rejectsHostnameAndInvalidAddress() {
        assertTrue(OtpClientMetadata.lastForwardedAddress("attacker.example").isEmpty());
        assertTrue(OtpClientMetadata.lastForwardedAddress("999.999.999.999").isEmpty());
    }

    @Test
    void requiresAdminRoleTokenRatherThanSubstring() {
        assertTrue(OtpRequestAuditHandler.hasAdminRole("attendee,admin"));
        assertTrue(!OtpRequestAuditHandler.hasAdminRole("superadmin"));
        assertTrue(!OtpRequestAuditHandler.hasAdminRole(""));
    }
}
