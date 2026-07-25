package dev.rafex.insightbloom.users.domain.services;

import java.util.List;

/**
 * Catálogo de referencia de variables de contexto disponibles para los prompts de IA (Prompt
 * base, Guardarails, u objetivo/instrucciones del Tutor/Encuesta por evento), para que el admin
 * sepa qué información real puede aprovechar al redactarlos.
 *
 * A diferencia de {@link CertificateTemplateCatalog#variables()}, esto NO es un motor de
 * sustitución de plantillas basado en tokens {@code {{...}}}: cada caso de uso arma su propio
 * bloque de contexto en el prompt (ej. MentorChatUseCase agrega "Nombre del evento: ..." al
 * mensaje "user" cuando corresponde). {@link Variable#autoIncludedIn()} indica en qué
 * capacidades ese dato YA viaja automáticamente en cada llamada al modelo -- el admin no
 * necesita (ni puede) escribir el token para que aparezca, solo saber que está disponible.
 * Vacío significa que el dato existe en la plataforma pero todavía no se conecta a ningún prompt.
 *
 * Deliberadamente NO se incluye correo, uuid ni ningún otro identificador sensible del
 * participante: el prompt viaja completo a un proveedor de LLM externo (Groq/OpenAI-compatible),
 * y esa llamada sale de la plataforma. Minimizar qué PII entra al prompt reduce la superficie de
 * fuga si el proveedor externo registra o entrena con esas peticiones.
 */
public final class AiPromptCatalog {
    private AiPromptCatalog() {}

    public record Variable(String key, String label, String example, String autoIncludedIn) {}

    public static List<Variable> variables() {
        return List.of(
                new Variable("event.name", "Nombre del evento", "Taller de ejemplo", "tutor, encuestas"),
                new Variable("event.date", "Fecha del evento", "22/07/2026", "tutor"),
                new Variable("event.venue", "Lugar del evento", "Auditorio principal", ""),
                new Variable("event.type", "Tipo de evento", "conference, workshop", ""),
                new Variable("presentation.content", "Contenido visible de la presentación (markdown)",
                        "# Agenda\n1. Contenedores\n2. Pods...", "tutor (si el organizador activa \"Leer la presentación\"), encuestas"),
                new Variable("survey.existingQuestions", "Preguntas ya creadas en el cuestionario (para no repetirlas)",
                        "- ¿Qué es un Pod?", "encuestas"),
                new Variable("survey.extraContext", "Contexto adicional que el organizador escribió a mano",
                        "Enfatizar seguridad y buenas prácticas", "encuestas"),
                new Variable("mentor.objective", "Objetivo pedagógico del taller (configurado por evento)",
                        "Que el asistente entienda Deployments y Services", "tutor"),
                new Variable("mentor.facilitatorGuide", "Instrucciones adicionales del facilitador (por evento)",
                        "Pedir primero qué intentaron antes de dar pistas", "tutor"),
                new Variable("participant.displayName", "Nombre visible del asistente", "Ana Pérez", ""),
                new Variable("platform.name", "Nombre de la plataforma", "InsightBloom", "")
        );
    }
}
