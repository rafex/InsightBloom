package dev.rafex.insightbloom.users.adapters.outbound.presentationsclient;

import java.util.Map;

public interface CertificateRenderer {
    byte[] render(String documentJson, Map<String, Object> data);
}
