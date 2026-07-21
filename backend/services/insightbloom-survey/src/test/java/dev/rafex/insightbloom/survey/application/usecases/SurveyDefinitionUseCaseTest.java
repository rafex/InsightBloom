package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.survey.domain.model.SurveyEngine;
import dev.rafex.insightbloom.survey.domain.ports.SurveyDefinitionRepository;
import dev.rafex.insightbloom.survey.domain.ports.SurveyQuestionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurveyDefinitionUseCaseTest {
    @Test
    void fixesTheEngineAndPersistsTheSelection() {
        final SurveyDefinitionRepository definitions = mock(SurveyDefinitionRepository.class);
        final SurveyQuestionRepository questions = mock(SurveyQuestionRepository.class);
        when(definitions.findByConference("c1")).thenReturn(java.util.Optional.empty());
        when(questions.findByConference("c1", false)).thenReturn(List.of());

        final var useCase = new SurveyDefinitionUseCase(definitions, questions, JsonUtils.codec());
        assertEquals(SurveyEngine.SURVEYJS, useCase.selectEngine("c1", SurveyEngine.SURVEYJS).getEngine());
        verify(definitions).save(any());
    }

    @Test
    void rejectsChangingAnAlreadySelectedEngine() {
        final SurveyDefinitionRepository definitions = mock(SurveyDefinitionRepository.class);
        final SurveyQuestionRepository questions = mock(SurveyQuestionRepository.class);
        final var selected = dev.rafex.insightbloom.survey.domain.model.SurveyDefinition.draft(
                "c1", SurveyEngine.SURVEYJS, "{}");
        when(definitions.findByConference("c1")).thenReturn(java.util.Optional.of(selected));

        final var useCase = new SurveyDefinitionUseCase(definitions, questions, JsonUtils.codec());
        assertThrows(IllegalStateException.class, () -> useCase.selectEngine("c1", SurveyEngine.NATIVE));
    }

    @Test
    void validatesOnlySupportedSurveyJsQuestionTypes() {
        final SurveyDefinitionRepository definitions = mock(SurveyDefinitionRepository.class);
        final SurveyQuestionRepository questions = mock(SurveyQuestionRepository.class);
        final var useCase = new SurveyDefinitionUseCase(definitions, questions, JsonUtils.codec());
        final Map<String, Object> invalid = Map.of("pages", List.of(Map.of("elements", List.of(
                Map.of("name", "q1", "type", "html")))));

        assertThrows(IllegalArgumentException.class, () -> useCase.validate(invalid));
    }
}
