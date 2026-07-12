package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.ports.LlmPort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class GenerateSeatLayoutUseCaseTest {

    @Test
    void execute_llmDisabled_throwsLlmNotConfigured() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(false);
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        final var ex = assertThrows(IllegalStateException.class, () -> useCase.execute("8 filas de 10"));
        assertEquals("llm_not_configured", ex.getMessage());
    }

    @Test
    void execute_blankDescription_throwsBadRequest() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(true);
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        assertThrows(IllegalArgumentException.class, () -> useCase.execute("   "));
    }

    @Test
    void execute_validJson_parsesSeats() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(true);
        Mockito.when(llm.complete(Mockito.anyString(), Mockito.anyString())).thenReturn(
                "{\"seats\": [{\"label\": \"A1\", \"x\": 0.1, \"y\": 0.2}, "
                        + "{\"label\": \"A2\", \"x\": 0.3, \"y\": 0.2}]}");
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        final var seats = useCase.execute("2 filas de 1 asiento");

        assertEquals(2, seats.size());
        assertEquals("A1", seats.get(0).label());
        assertEquals(0.1, seats.get(0).x());
        assertNull(seats.get(0).uuid());
    }

    @Test
    void execute_textAroundJson_extractsObject() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(true);
        Mockito.when(llm.complete(Mockito.anyString(), Mockito.anyString())).thenReturn(
                "Aqui esta el layout:\n{\"seats\": [{\"label\": \"A1\", \"x\": 0.5, \"y\": 0.5}]}\nListo.");
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        final var seats = useCase.execute("un asiento al centro");

        assertEquals(1, seats.size());
    }

    @Test
    void execute_coordinateOutOfRange_throws() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(true);
        Mockito.when(llm.complete(Mockito.anyString(), Mockito.anyString())).thenReturn(
                "{\"seats\": [{\"label\": \"A1\", \"x\": 1.5, \"y\": 0.2}]}");
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        assertThrows(RuntimeException.class, () -> useCase.execute("asiento fuera de rango"));
    }

    @Test
    void execute_missingSeatsArray_throws() {
        final LlmPort llm = Mockito.mock(LlmPort.class);
        Mockito.when(llm.isEnabled()).thenReturn(true);
        Mockito.when(llm.complete(Mockito.anyString(), Mockito.anyString())).thenReturn("{\"foo\": \"bar\"}");
        final var useCase = new GenerateSeatLayoutUseCase(llm, JacksonJsonCodec.defaultCodec());

        assertThrows(RuntimeException.class, () -> useCase.execute("descripcion cualquiera"));
    }
}
