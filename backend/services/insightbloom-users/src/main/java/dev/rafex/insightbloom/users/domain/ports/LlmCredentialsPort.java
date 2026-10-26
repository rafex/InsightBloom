package dev.rafex.insightbloom.users.domain.ports;

import java.util.Map;

public interface LlmCredentialsPort {
  Map<String, Object> credentialStatus(String scope, String id, String capability);

  Map<String, Object> effectiveStatus(String eventId, String capability);

  void saveCredential(
      String scope, String id, String capability, Map<String, Object> configuration, String actor);

  void clearCredential(String scope, String id, String capability, String actor);
}
