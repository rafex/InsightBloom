package dev.rafex.insightbloom.users.adapters.outbound.llm;

public final class EmailLlmClient {
    private static final String SYSTEM_PROMPT = "Sos un asistente de redacción profesional para comunicaciones de eventos. "
            + "Genera un borrador conciso, profesional y apto para email. Responde únicamente con HTML semántico "
            + "usando p, strong, em, ul, ol, li, h3 y br. No uses html, head ni body.";
    private final LlmGatewayClient gateway;

    public EmailLlmClient(final LlmGatewayClient gateway) { this.gateway = gateway; }

    public boolean isEnabled() { return false; }
    public boolean isEnabled(final String eventId) { return gateway.isConfigured(eventId, "email"); }
    public String complete(final String prompt) { throw new IllegalStateException("llm_event_scope_required"); }
    public String complete(final String eventId, final String prompt) {
        return gateway.complete(eventId, "email", SYSTEM_PROMPT, prompt, 0.7);
    }
}
