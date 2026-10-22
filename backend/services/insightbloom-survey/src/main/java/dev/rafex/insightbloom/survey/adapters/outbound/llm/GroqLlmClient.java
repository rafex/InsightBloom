package dev.rafex.insightbloom.survey.adapters.outbound.llm;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.ports.LlmPort;

/** Legacy class name retained while all model calls are routed through event-scoped credentials. */
public final class GroqLlmClient implements LlmPort {
    private final EventLlmGatewayClient gateway;
    private final String capability;

    public GroqLlmClient(final String usersBaseUrl, final String internalApiKey, final JsonCodec jsonCodec,
                         final String capability) {
        this.gateway = new EventLlmGatewayClient(jsonCodec);
        this.capability = capability;
    }

    @Override public boolean isEnabled() { return false; }

    @Override public boolean isEnabled(final String eventId, final String requestedCapability) {
        return capability.equals(requestedCapability) && gateway.isConfigured(eventId, capability);
    }

    @Override public String complete(final String systemPrompt, final String userPrompt) {
        throw new IllegalStateException("llm_event_scope_required");
    }

    @Override public String complete(final String eventId, final String requestedCapability,
                                     final String systemPrompt, final String userPrompt) {
        if (!capability.equals(requestedCapability)) throw new IllegalStateException("llm_capability_forbidden");
        return gateway.complete(eventId, capability, systemPrompt, userPrompt);
    }
}
