package dev.rafex.insightbloom.users.domain.services;

import java.util.List;

/**
 * Catálogo de referencia de variables que un admin puede mencionar en los prompts de IA
 * (Prompt base, Guardarails, u objetivo/instrucciones del Tutor por evento) para que el
 * modelo sepa qué contexto real tiene disponible al responder.
 *
 * A diferencia de {@link CertificateTemplateCatalog#variables()}, esto NO es un motor de
 * sustitución de plantillas: es documentación para el admin. La sustitución real de estos
 * valores en el prompt (nombre del asistente, presentación, etc.) depende de cada caso de uso
 * (ej. MentorChatUseCase ya arma el objetivo/presentación/pregunta como CONTEXTO DEL EVENTO
 * en el mensaje "user", no vía estos tokens) y debe implementarse cuando haga falta.
 *
 * Deliberadamente NO se incluye correo, uuid ni ningún otro identificador sensible del
 * participante: el prompt viaja completo a un proveedor de LLM externo (Groq/OpenAI-compatible),
 * y esa llamada sale de la plataforma. Minimizar qué PII entra al prompt reduce la superficie de
 * fuga si el proveedor externo registra o entrena con esas peticiones.
 */
public final class AiPromptCatalog {
    private AiPromptCatalog() {}

    public record Variable(String key, String label, String example) {}

    public static List<Variable> variables() {
        return List.of(
                new Variable("participant.displayName", "Nombre visible del asistente", "Ana Pérez"),
                new Variable("participant.firstName", "Nombre del asistente", "Ana"),
                new Variable("event.name", "Nombre del evento", "Taller de ejemplo"),
                new Variable("event.venue", "Lugar del evento", "Auditorio principal"),
                new Variable("event.date", "Fecha del evento", "22/07/2026"),
                new Variable("presentation.title", "Título de la presentación", "Introducción a Kubernetes"),
                new Variable("presentation.content", "Contenido de la presentación (markdown)",
                        "# Agenda\n1. Contenedores\n2. Pods..."),
                new Variable("platform.name", "Nombre de la plataforma", "InsightBloom")
        );
    }
}
