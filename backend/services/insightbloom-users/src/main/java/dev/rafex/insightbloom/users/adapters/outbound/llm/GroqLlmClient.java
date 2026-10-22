package dev.rafex.insightbloom.users.adapters.outbound.llm;

import dev.rafex.insightbloom.users.domain.ports.LlmPort;

/** Compatibility name retained for the seat-layout use case; provider calls go only through the
 * event-scoped credential gateway. */
public final class GroqLlmClient implements LlmPort {
    private final LlmGatewayClient gateway;
    private final String capability;

    public GroqLlmClient(final LlmGatewayClient gateway, final String capability) {
        this.gateway = gateway;
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
        return gateway.complete(eventId, capability, systemPrompt, userPrompt, 0.3);
    }
}
