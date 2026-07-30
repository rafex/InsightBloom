package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.adapters.outbound.llm.EmailLlmClient;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerateEmailDraftUseCaseTest {

    @Test
    void generatesDraftWhenAiEnabled() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EmailLlmClient emailLlm = mock(EmailLlmClient.class);

        final var conference = new Conference("event", "Evento", "owner");
        when(conferences.findByUuid("event")).thenReturn(Optional.of(conference));
        when(emailLlm.isEnabled()).thenReturn(true);
        when(emailLlm.complete(anyString())).thenReturn("Borrador generado");

        final var useCase = new GenerateEmailDraftUseCase(conferences, emailLlm);
        final String draft = useCase.execute("event", "Recordar que el evento cambia");

        assertEquals("Borrador generado", draft);
        verify(emailLlm).complete(contains("Evento"));
    }

    @Test
    void rejectsWhenAiNotConfigured() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EmailLlmClient emailLlm = mock(EmailLlmClient.class);

        when(emailLlm.isEnabled()).thenReturn(false);

        final var useCase = new GenerateEmailDraftUseCase(conferences, emailLlm);
        assertThrows(IllegalStateException.class, () ->
                useCase.execute("event", "Mensaje"));
    }

    @Test
    void rejectsEmptyPrompt() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EmailLlmClient emailLlm = mock(EmailLlmClient.class);

        final var useCase = new GenerateEmailDraftUseCase(conferences, emailLlm);
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute("event", ""));
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute("event", "  "));
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute("event", null));
    }

    @Test
    void rejectsWhenConferenceNotFound() {
        final ConferenceRepository conferences = mock(ConferenceRepository.class);
        final EmailLlmClient emailLlm = mock(EmailLlmClient.class);

        when(emailLlm.isEnabled()).thenReturn(true);
        when(conferences.findByUuid("unknown")).thenReturn(Optional.empty());

        final var useCase = new GenerateEmailDraftUseCase(conferences, emailLlm);
        assertThrows(IllegalArgumentException.class, () ->
                useCase.execute("unknown", "Mensaje"));
    }
}
