package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.*;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import java.util.*;

/** Resolves current authority and configuration sources; never handles decrypted credentials. */
public final class LlmConfigurationUseCase {
  public static final Set<String> CAPABILITIES =
      Set.of("chat", "tutor", "survey", "seatLayout", "email");
  private final ConferenceRepository conferences;
  private final UserRepository users;
  private final EventPermissionGuard permissions;
  private final LlmConfigurationRepository sources;
  private final LlmCredentialsPort credentials;

  public LlmConfigurationUseCase(
      ConferenceRepository conferences,
      UserRepository users,
      EventPermissionGuard permissions,
      LlmConfigurationRepository sources,
      LlmCredentialsPort credentials) {
    this.conferences = conferences;
    this.users = users;
    this.permissions = permissions;
    this.sources = sources;
    this.credentials = credentials;
  }

  private void capability(String value) {
    if (!CAPABILITIES.contains(value)) throw new IllegalArgumentException("capability_invalid");
  }

  public boolean eligible(String id) {
    return users
        .findByUuid(id)
        .map(
            u ->
                u.getStatus() == UserStatus.ACTIVE
                    && (u.hasRole(UserRole.ADMIN) || u.hasRole(UserRole.ORGANIZER)))
        .orElse(false);
  }

  public boolean canManage(String eventId, String actor) {
    if (!eligible(actor)) return false;
    var event = conferences.findByUuid(eventId);
    if (event.isEmpty()) return false;
    var user = users.findByUuid(actor).orElseThrow();
    return user.hasRole(UserRole.ADMIN)
        || actor.equals(event.get().getCreatedByUserUuid())
        || permissions.hasPermission(
            eventId, actor, UserRole.toCsv(user.getRoles()), Permission.MANAGE_EVENT_AI);
  }

  private void authorize(String eventId, String actor) {
    if (!canManage(eventId, actor)) throw new SecurityException("forbidden");
  }

  private void personal(String actor) {
    if (!eligible(actor)) throw new SecurityException("forbidden");
  }

  public Map<String, Object> context(String eventId, String cap) {
    capability(cap);
    var event =
        conferences
            .findByUuid(eventId)
            .orElseThrow(() -> new IllegalArgumentException("event_not_found"));
    var source = sources.find(eventId, cap).orElse(new EventLlmSource(eventId, cap, "AUTO", null));
    var out = new LinkedHashMap<String, Object>();
    out.put("mode", source.mode());
    out.put("ownerId", event.getCreatedByUserUuid());
    out.put("ownerEligible", eligible(event.getCreatedByUserUuid()));
    if (source.memberId() != null) {
      out.put("memberId", source.memberId());
      out.put("memberEligible", canManage(eventId, source.memberId()));
      out.put(
          "linkedProfileName",
          users
              .findByUuid(source.memberId())
              .map(User::getUsername)
              .orElse("Perfil no disponible"));
    }
    return out;
  }

  public Map<String, Object> status(String eventId, String cap, String actor) {
    capability(cap);
    authorize(eventId, actor);
    var out = new LinkedHashMap<>(credentials.effectiveStatus(eventId, cap));
    out.remove("scopeId");
    out.remove("scopeType");
    out.remove("credentialOwnerId");
    out.remove("ownerId");
    out.put("canManage", true);
    return out;
  }

  public Map<String, Object> saveEvent(
      String eventId, String cap, String actor, Map<String, Object> body) {
    capability(cap);
    authorize(eventId, actor);
    credentials.saveCredential("event", eventId, cap, configuration(body), actor);
    sources.save(new EventLlmSource(eventId, cap, "EVENT", null), actor);
    return status(eventId, cap, actor);
  }

  public Map<String, Object> changeMode(
      String eventId, String cap, String actor, String mode, boolean confirm) {
    capability(cap);
    authorize(eventId, actor);
    if (!Set.of("INHERIT_OWNER", "MEMBER_PROFILE", "DISABLED").contains(mode))
      throw new IllegalArgumentException("mode_invalid");
    if (mode.equals("MEMBER_PROFILE")
        && !Boolean.TRUE.equals(credentials.credentialStatus("user", actor, cap).get("configured")))
      throw new IllegalArgumentException("personal_configuration_required");
    if (Boolean.TRUE.equals(
        credentials.credentialStatus("event", eventId, cap).get("configured"))) {
      if (!confirm) throw new IllegalArgumentException("confirm_credential_removal_required");
      credentials.clearCredential("event", eventId, cap, actor);
    }
    sources.save(
        new EventLlmSource(eventId, cap, mode, mode.equals("MEMBER_PROFILE") ? actor : null),
        actor);
    return status(eventId, cap, actor);
  }

  public Map<String, Object> personalStatus(String actor, String cap) {
    capability(cap);
    personal(actor);
    return credentials.credentialStatus("user", actor, cap);
  }

  public Map<String, Object> savePersonal(String actor, String cap, Map<String, Object> body) {
    capability(cap);
    personal(actor);
    credentials.saveCredential("user", actor, cap, configuration(body), actor);
    sources.audit(actor, "personal_set", null, cap);
    return personalStatus(actor, cap);
  }

  public void clearPersonal(String actor, String cap) {
    capability(cap);
    personal(actor);
    credentials.clearCredential("user", actor, cap, actor);
    sources.audit(actor, "personal_cleared", null, cap);
  }

  public List<Map<String, Object>> dependencies(String actor) {
    var out = new ArrayList<Map<String, Object>>();
    for (var event : conferences.findByUser(actor)) {
      if (!actor.equals(event.getCreatedByUserUuid())) continue;
      for (var cap : CAPABILITIES) {
        var mode = sources.find(event.getUuid(), cap).map(EventLlmSource::mode).orElse("AUTO");
        // AUTO events with existing credentials are preserved as EVENT.
        if (mode.equals("AUTO")
            && Boolean.TRUE.equals(
                credentials.credentialStatus("event", event.getUuid(), cap).get("configured")))
          continue;
        if (mode.equals("AUTO") || mode.equals("INHERIT_OWNER"))
          out.add(
              Map.of(
                  "eventId",
                  event.getUuid(),
                  "name",
                  event.getName(),
                  "capability",
                  cap,
                  "kind",
                  "OWNER"));
      }
    }
    for (var source : sources.linkedTo(actor))
      conferences
          .findByUuid(source.eventId())
          .ifPresent(
              e ->
                  out.add(
                      Map.of(
                          "eventId",
                          e.getUuid(),
                          "name",
                          e.getName(),
                          "capability",
                          source.capability(),
                          "kind",
                          "MEMBER_PROFILE")));
    return out;
  }

  public void withdraw(String actor, String eventId, String cap) {
    capability(cap);
    if (!sources.withdrawMemberProfile(actor, eventId, cap))
      throw new SecurityException("forbidden");
  }

  private Map<String, Object> configuration(Map<String, Object> body) {
    if (!Set.of("provider", "model", "baseUrl", "apiKey").containsAll(body.keySet()))
      throw new IllegalArgumentException("configuration_invalid");
    var out = new LinkedHashMap<String, Object>();
    for (var key : List.of("provider", "model")) {
      var value = body.get(key);
      int max = key.equals("provider") ? 32 : 128;
      if (!(value instanceof String text) || text.isBlank() || text.length() > max)
        throw new IllegalArgumentException("configuration_invalid");
      out.put(key, text.trim());
    }
    for (var key : List.of("baseUrl", "apiKey")) {
      var value = body.getOrDefault(key, "");
      int max = key.equals("baseUrl") ? 2048 : 4096;
      if (!(value instanceof String text) || text.length() > max)
        throw new IllegalArgumentException("configuration_invalid");
      out.put(key, text.trim());
    }
    return out;
  }
}
