package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.EventLlmSource;
import java.util.List;
import java.util.Optional;

public interface LlmConfigurationRepository {
  Optional<EventLlmSource> find(String eventId, String capability);

  void save(EventLlmSource source, String actor);

  List<EventLlmSource> linkedTo(String userId);

  boolean withdrawMemberProfile(String userId, String eventId, String capability);

  void audit(String actor, String action, String eventId, String capability);
}
