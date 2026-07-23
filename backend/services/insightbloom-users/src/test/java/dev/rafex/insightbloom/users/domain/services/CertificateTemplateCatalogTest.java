package dev.rafex.insightbloom.users.domain.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertificateTemplateCatalogTest {
    @Test
    void catalogUsesTheFullLetterLandscapeCanvas() {
        final String document = CertificateTemplateCatalog.defaultEntry().documentJson();

        assertTrue(document.contains("\"width\":1056"));
        assertTrue(document.contains("\"height\":816"));
        assertTrue(document.contains("\"layoutVersion\":2"));
        assertTrue(document.contains("\"width\":1020,\"height\":780"));
    }

    @Test
    void untouchedLegacyCatalogDocumentsAreUpgradedButCustomDocumentsAreNot() {
        final String legacy = "{\"page\":{\"background\":\"#ffffff\",\"padding\":48},\"blocks\":["
                + "{\"type\":\"shape\",\"x\":18,\"y\":18,\"width\":964,\"height\":504,\"style\":{\"border\":\"3px solid #1e1b4b\",\"borderRadius\":18}},"
                + "{\"type\":\"text\",\"x\":90,\"y\":90,\"width\":820,\"height\":55,\"text\":\"CERTIFICADO DE ASISTENCIA\"},"
                + "{\"type\":\"text\",\"x\":90,\"y\":175,\"width\":820,\"height\":38,\"text\":\"Se otorga el presente certificado a\"},"
                + "{\"type\":\"text\",\"x\":90,\"y\":225,\"width\":820,\"height\":65,\"text\":\"{{participant.displayName}}\"},"
                + "{\"type\":\"text\",\"x\":90,\"y\":320,\"width\":820,\"height\":38,\"text\":\"por su participación en\"},"
                + "{\"type\":\"text\",\"x\":90,\"y\":365,\"width\":820,\"height\":50,\"text\":\"{{event.name}}\"},"
                + "{\"type\":\"text\",\"x\":90,\"y\":445,\"width\":820,\"height\":25,\"text\":\"Emitido el {{certificate.issuedDate}} · {{platform.name}}\"}]}";

        final String upgraded = CertificateTemplateCatalog.upgradeLegacyLayout("classic", legacy);
        assertNotEquals(legacy, upgraded);
        assertTrue(upgraded.contains("\"layoutVersion\":2"));

        final String custom = legacy.replace("CERTIFICADO DE ASISTENCIA", "MI CERTIFICADO");
        assertSame(custom, CertificateTemplateCatalog.upgradeLegacyLayout("classic", custom));
    }
}
