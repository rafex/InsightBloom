package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.adapters.outbound.llm.EmailLlmClient;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

public class GenerateEmailDraftUseCase {
    private final ConferenceRepository conferenceRepository;
    private final EmailLlmClient emailLlmClient;

    public GenerateEmailDraftUseCase(final ConferenceRepository conferenceRepository,
                                      final EmailLlmClient emailLlmClient) {
        this.conferenceRepository = conferenceRepository;
        this.emailLlmClient = emailLlmClient;
    }

    public String execute(final String conferenceUuid, final String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("prompt_required");
        }
        final var conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        if (!emailLlmClient.isEnabled(conferenceUuid)) {
            throw new IllegalStateException("email_ai_not_configured");
        }

        final String prompt = "Evento: " + conference.getName() + "\n"
                + "Descripcion: " + (conference.getDescription() != null ? conference.getDescription() : "Sin descripcion") + "\n\n"
                + "El organizador quiere comunicar: " + userPrompt;

        return emailLlmClient.complete(conferenceUuid, prompt);
    }
}
