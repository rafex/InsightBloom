package dev.rafex.insightbloom.users.application.usecases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.rafex.insightbloom.users.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.users.domain.model.*;
import dev.rafex.insightbloom.users.domain.ports.*;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class LlmConfigurationUseCaseTest {
  @TempDir Path temp;
  ConferenceRepository conferences = mock(ConferenceRepository.class);
  UserRepository users = mock(UserRepository.class);
  EventPermissionGuard permissions = mock(EventPermissionGuard.class);
  LlmCredentialsPort credentials = mock(LlmCredentialsPort.class);
  DatabaseManager db;
  SqliteLlmConfigurationRepository sources;
  LlmConfigurationUseCase llm;
  User owner = new User("owner", "owner", "Owner", "owner@example.test", UserRole.ORGANIZER);
  User member = new User("member", "member", "Member", "member@example.test", UserRole.ORGANIZER);
  Conference event = new Conference("test", "Event", "owner");

  @BeforeEach
  void setup() {
    db = new DatabaseManager(temp.resolve("users.db").toString());
    db.initialize();
    sources = new SqliteLlmConfigurationRepository(db);
    when(users.findByUuid("owner")).thenReturn(Optional.of(owner));
    when(users.findByUuid("member")).thenReturn(Optional.of(member));
    when(conferences.findByUuid("event")).thenReturn(Optional.of(event));
    when(credentials.credentialStatus(anyString(), anyString(), anyString()))
        .thenReturn(Map.of("configured", false));
    when(credentials.effectiveStatus(anyString(), anyString()))
        .thenReturn(
            Map.of("configured", true, "mode", "INHERIT_OWNER", "effectiveSource", "OWNER"));
    llm = new LlmConfigurationUseCase(conferences, users, permissions, sources, credentials);
  }

  @Test
  void onlyOwnerAdminOrExplicitEligibleDelegateMayManage() {
    assertTrue(llm.canManage("event", "owner"));
    assertFalse(llm.canManage("event", "member"));
    when(permissions.hasPermission(
            eq("event"), eq("member"), anyString(), eq(Permission.MANAGE_EVENT_AI)))
        .thenReturn(true);
    assertTrue(llm.canManage("event", "member"));
    assertFalse(llm.canManage("other", "member"));
    member.setRoles(Set.of(UserRole.ATTENDEE));
    assertFalse(llm.canManage("event", "member"));
    member.setRoles(Set.of(UserRole.ADMIN));
    assertTrue(llm.canManage("event", "member"));
  }

  @Test
  void contextRechecksCurrentRolePermissionAndSuspension() {
    sources.save(new EventLlmSource("event", "chat", "MEMBER_PROFILE", "member"), "member");
    when(permissions.hasPermission(
            eq("event"), eq("member"), anyString(), eq(Permission.MANAGE_EVENT_AI)))
        .thenReturn(true);
    assertEquals(true, llm.context("event", "chat").get("memberEligible"));
    when(permissions.hasPermission(
            eq("event"), eq("member"), anyString(), eq(Permission.MANAGE_EVENT_AI)))
        .thenReturn(false);
    assertEquals(false, llm.context("event", "chat").get("memberEligible"));
    member.setStatus(UserStatus.BANNED);
    assertEquals(false, llm.context("event", "chat").get("memberEligible"));
    owner.setStatus(UserStatus.BANNED);
    assertEquals(false, llm.context("event", "chat").get("ownerEligible"));
  }

  @Test
  void noSourcePreservesExistingGatewayOverrides() {
    assertEquals("AUTO", llm.context("event", "chat").get("mode"));
    assertThrows(IllegalArgumentException.class, () -> llm.context("event", "unknown"));
    assertThrows(IllegalArgumentException.class, () -> llm.context("missing", "chat"));
  }

  @Test
  void linkingUsesActorAndRequiresPersonalCredential() {
    assertThrows(
        IllegalArgumentException.class,
        () -> llm.changeMode("event", "chat", "owner", "MEMBER_PROFILE", true));
    when(credentials.credentialStatus("user", "owner", "chat"))
        .thenReturn(Map.of("configured", true));
    llm.changeMode("event", "chat", "owner", "MEMBER_PROFILE", true);
    assertEquals("owner", sources.find("event", "chat").orElseThrow().memberId());
  }

  @Test
  void changingAwayFromEventRequiresConfirmationAndRemovesCredential() {
    when(credentials.credentialStatus("event", "event", "chat"))
        .thenReturn(Map.of("configured", true));
    assertThrows(
        IllegalArgumentException.class,
        () -> llm.changeMode("event", "chat", "owner", "DISABLED", false));
    verify(credentials, never()).clearCredential(any(), any(), any(), any());
    llm.changeMode("event", "chat", "owner", "DISABLED", true);
    verify(credentials).clearCredential("event", "event", "chat", "owner");
    assertEquals("DISABLED", sources.find("event", "chat").orElseThrow().mode());
  }

  @Test
  void writesAreIsolatedAndRejectArbitraryProfileReferences() {
    assertThrows(SecurityException.class, () -> llm.saveEvent("event", "chat", "member", Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            llm.savePersonal(
                "owner",
                "chat",
                Map.of("provider", "openai", "model", "gpt-4o-mini", "memberId", "victim")));
    llm.savePersonal(
        "owner", "chat", Map.of("provider", "openai", "model", "gpt-4o-mini", "apiKey", "secret"));
    verify(credentials).saveCredential(eq("user"), eq("owner"), eq("chat"), anyMap(), eq("owner"));
    llm.saveEvent("event", "chat", "owner", Map.of("provider", "openai", "model", "gpt-4o-mini"));
    assertEquals("EVENT", sources.find("event", "chat").orElseThrow().mode());
  }

  @Test
  void contributorMayWithdrawAfterLosingDelegationButOthersCannot() {
    sources.save(new EventLlmSource("event", "chat", "MEMBER_PROFILE", "member"), "member");
    assertThrows(SecurityException.class, () -> llm.withdraw("owner", "event", "chat"));
    llm.withdraw("member", "event", "chat");
    assertEquals("INHERIT_OWNER", sources.find("event", "chat").orElseThrow().mode());
  }

  @Test
  void staleWithdrawCannotReplaceANewerProfileLink() {
    sources.save(new EventLlmSource("event", "chat", "MEMBER_PROFILE", "member"), "member");
    sources.save(new EventLlmSource("event", "chat", "MEMBER_PROFILE", "owner"), "owner");
    assertFalse(sources.withdrawMemberProfile("member", "event", "chat"));
    assertEquals("owner", sources.find("event", "chat").orElseThrow().memberId());
  }

  @Test
  void modeAndAuditSurviveRestartAndContainNoSecrets() throws Exception {
    llm.savePersonal(
        "owner",
        "chat",
        Map.of("provider", "openai", "model", "gpt-4o-mini", "apiKey", "sensitive-value"));
    sources.save(new EventLlmSource("event", "email", "DISABLED", null), "owner");
    db.initialize();
    assertEquals("DISABLED", sources.find("event", "email").orElseThrow().mode());
    try (var c = db.getConnection();
        var p = c.prepareStatement("SELECT * FROM llm_configuration_audit");
        var rs = p.executeQuery()) {
      while (rs.next())
        assertFalse((rs.getString("actor") + rs.getString("action")).contains("sensitive-value"));
    }
  }
}
