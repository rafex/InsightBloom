package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.survey.domain.model.SurveyDefinition;
import dev.rafex.insightbloom.survey.domain.model.SurveyEngine;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SurveyDefinitionUseCase {
    private static final int MAX_SCHEMA_BYTES = 512 * 1024;
    private static final int MAX_ELEMENTS = 100;
    private static final List<String> ALLOWED_TYPES = List.of(
            "text", "comment", "radiogroup", "checkbox", "dropdown", "rating", "boolean", "ranking");

    private final SurveyDefinitionRepository definitionRepo;
    private final SurveyQuestionRepository questionRepo;
    private final JsonCodec jsonCodec;

    public SurveyDefinitionUseCase(final SurveyDefinitionRepository definitionRepo,
                                   final SurveyQuestionRepository questionRepo,
                                   final JsonCodec jsonCodec) {
        this.definitionRepo = definitionRepo;
        this.questionRepo = questionRepo;
        this.jsonCodec = jsonCodec;
    }

    public Optional<SurveyDefinition> get(final String conferenceUuid, final boolean publishedOnly) {
        final Optional<SurveyDefinition> stored = definitionRepo.findByConference(conferenceUuid);
        if (stored.isPresent() && (stored.get().getEngine() == SurveyEngine.NATIVE
                || !publishedOnly || "PUBLISHED".equals(stored.get().getStatus()))) {
            return stored;
        }
        if (stored.isEmpty() && !questionRepo.findByConference(conferenceUuid, false).isEmpty()) {
            return Optional.of(SurveyDefinition.draft(conferenceUuid, SurveyEngine.NATIVE, "{}"));
        }
        return Optional.empty();
    }

    public SurveyDefinition selectEngine(final String conferenceUuid, final SurveyEngine engine) {
        final Optional<SurveyDefinition> current = definitionRepo.findByConference(conferenceUuid);
        if (current.isPresent()) {
            if (current.get().getEngine() != engine) throw new IllegalStateException("engine_immutable");
            return current.get();
        }
        if (engine == SurveyEngine.SURVEYJS && !questionRepo.findByConference(conferenceUuid, false).isEmpty()) {
            throw new IllegalStateException("native_questions_exist");
        }
        final SurveyDefinition definition = SurveyDefinition.draft(conferenceUuid, engine,
                engine == SurveyEngine.SURVEYJS ? emptySchemaJson() : "{}");
        definitionRepo.save(definition);
        return definition;
    }

    public SurveyDefinition saveDraft(final String conferenceUuid, final Map<String, Object> schema) {
        validate(schema);
        final SurveyDefinition definition = requireSurveyJs(conferenceUuid);
        definition.updateDraft(jsonCodec.toJson(schema));
        definitionRepo.save(definition);
        return definition;
    }

    public SurveyDefinition publish(final String conferenceUuid, final Map<String, Object> schema) {
        validate(schema);
        final SurveyDefinition definition = requireSurveyJs(conferenceUuid);
        definition.publish(jsonCodec.toJson(schema));
        definitionRepo.save(definition);
        return definition;
    }

    public void validate(final Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) throw new IllegalArgumentException("schema_required");
        final String json = jsonCodec.toJson(schema);
        if (json.length() > MAX_SCHEMA_BYTES) throw new IllegalArgumentException("schema_too_large");
        if (!(schema.get("pages") instanceof List<?> pages) || pages.isEmpty()) {
            throw new IllegalArgumentException("schema_pages_required");
        }
        int elements = 0;
        for (Object pageValue : pages) {
            if (!(pageValue instanceof Map<?, ?> page)) throw new IllegalArgumentException("invalid_page");
            final Object pageElements = page.get("elements");
            if (pageElements == null) continue;
            if (!(pageElements instanceof List<?> list)) throw new IllegalArgumentException("invalid_elements");
            for (Object elementValue : list) {
                if (!(elementValue instanceof Map<?, ?> element)) throw new IllegalArgumentException("invalid_element");
                final Object type = element.get("type");
                final Object name = element.get("name");
                if (!(type instanceof String) || !ALLOWED_TYPES.contains(type)) {
                    throw new IllegalArgumentException("unsupported_question_type");
                }
                if (!(name instanceof String) || ((String) name).isBlank()) {
                    throw new IllegalArgumentException("question_name_required");
                }
                elements++;
            }
        }
        if (elements == 0) throw new IllegalArgumentException("schema_questions_required");
        if (elements > MAX_ELEMENTS) throw new IllegalArgumentException("too_many_questions");
    }

    public Map<String, Object> schema(final SurveyDefinition definition) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> schema = jsonCodec.readValue(definition.getSchemaJson(), Map.class);
        return schema;
    }

    private SurveyDefinition requireSurveyJs(final String conferenceUuid) {
        final SurveyDefinition definition = definitionRepo.findByConference(conferenceUuid)
                .orElseThrow(() -> new IllegalStateException("engine_not_selected"));
        if (definition.getEngine() != SurveyEngine.SURVEYJS) {
            throw new IllegalStateException("native_engine_selected");
        }
        return definition;
    }

    private String emptySchemaJson() {
        return "{\"title\":\"Encuesta\",\"pages\":[{\"name\":\"pagina1\",\"elements\":[]}]" + "}";
    }
}
