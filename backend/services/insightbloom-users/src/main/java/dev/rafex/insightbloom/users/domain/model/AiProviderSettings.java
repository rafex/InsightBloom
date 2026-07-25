package dev.rafex.insightbloom.users.domain.model;

/**
 * Configuración independiente de una capacidad de IA.
 *
 * La clave solo existe en memoria dentro de los servicios que llaman al
 * proveedor. Nunca debe incluirse en una vista pública o administrativa.
 */
public class AiProviderSettings {
    private boolean configured;
    private boolean enabled;
    private String baseUrl;
    private String model;
    private String apiKey;
    private String systemPrompt;
    private String guardrails;
    private Double temperature;

    public AiProviderSettings() { }

    public AiProviderSettings(final boolean configured, final boolean enabled, final String baseUrl,
                              final String model, final String apiKey, final String systemPrompt,
                              final String guardrails, final Double temperature) {
        this.configured = configured;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.systemPrompt = systemPrompt;
        this.guardrails = guardrails;
        this.temperature = temperature;
    }

    public static AiProviderSettings defaults(final boolean configured) {
        return new AiProviderSettings(configured, false,
                "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", null, null, null, null);
    }

    public AiProviderSettings copy() {
        return new AiProviderSettings(configured, enabled, baseUrl, model, apiKey, systemPrompt, guardrails, temperature);
    }

    public boolean isConfigured() { return configured; }
    public void setConfigured(final boolean configured) { this.configured = configured; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(final boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(final String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(final String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(final String apiKey) { this.apiKey = apiKey; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(final String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getGuardrails() { return guardrails; }
    public void setGuardrails(final String guardrails) { this.guardrails = guardrails; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(final Double temperature) { this.temperature = temperature; }
}
