package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.users.domain.ports.LlmPort;

import java.util.List;
import java.util.Map;

/**
 * Propone un layout de asientos (label/x/y relativos 0.0-1.0) a partir de una descripcion en
 * texto libre (medidas, distancias, referencias, figuras geometricas). El resultado SIEMPRE es
 * una propuesta: el organizador la revisa/edita en el editor manual antes de guardarla via
 * DefineVenueSeatsUseCase, nunca se persiste directamente desde aqui.
 */
public class GenerateSeatLayoutUseCase {

    private static final int MAX_SEATS = 500;

    private static final String SYSTEM_PROMPT = """
            Eres un asistente que ayuda a un organizador de eventos a distribuir asientos en un
            recinto a partir de una descripcion en texto: medidas del lugar, distancias entre
            asientos o filas, referencias (por ejemplo "escenario al frente", "pasillo central",
            "entrada a la derecha") y figuras geometricas (filas rectas, semicirculo, herradura,
            circulo, etc). Convierte esa descripcion en una distribucion de asientos.
            Responde UNICAMENTE con un objeto JSON valido (sin texto adicional, sin markdown), con
            esta forma exacta:
            {"seats": [{"label": "A1", "x": 0.12, "y": 0.34}, ...]}
            Reglas:
            - "x" e "y" son coordenadas RELATIVAS entre 0.0 y 1.0 (0,0 = esquina superior
              izquierda del recinto, 1,1 = esquina inferior derecha), nunca fuera de ese rango.
            - "label" identifica el asiento de forma legible para el organizador (ej. fila+numero).
            - Distribuye los asientos respetando la forma, filas/columnas, distancias y
              referencias descritas, manteniendo separacion proporcional entre ellos.
            - No agregues comentarios, explicaciones ni texto fuera del JSON.
            """;

    private final LlmPort llm;
    private final JsonCodec jsonCodec;

    public GenerateSeatLayoutUseCase(final LlmPort llm, final JsonCodec jsonCodec) {
        this.llm = llm;
        this.jsonCodec = jsonCodec;
    }

    public List<DefineVenueSeatsUseCase.SeatInput> execute(final String description) {
        if (!llm.isEnabled()) {
            throw new IllegalStateException("llm_not_configured");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description_required");
        }
        final String raw = llm.complete(SYSTEM_PROMPT, description);
        final String jsonObject = extractJsonObject(raw);

        final Map<String, Object> parsed = jsonCodec.readValue(jsonObject, Map.class);
        final Object rawSeats = parsed.get("seats");
        if (!(rawSeats instanceof List<?> list)) {
            throw new RuntimeException("llm_invalid_response: missing seats array");
        }
        if (list.size() > MAX_SEATS) {
            throw new RuntimeException("llm_invalid_response: too many seats (" + list.size() + ")");
        }

        return list.stream().map(item -> {
            if (!(item instanceof Map<?, ?> m)) {
                throw new RuntimeException("llm_invalid_response: invalid seat entry");
            }
            final String label = String.valueOf(m.get("label"));
            final double x = toDouble(m.get("x"));
            final double y = toDouble(m.get("y"));
            if (x < 0.0 || x > 1.0 || y < 0.0 || y > 1.0) {
                throw new RuntimeException("llm_invalid_response: coordinate out of range for seat " + label);
            }
            return new DefineVenueSeatsUseCase.SeatInput(null, label, x, y);
        }).toList();
    }

    private static double toDouble(final Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new RuntimeException("llm_invalid_response: non-numeric coordinate");
    }

    private String extractJsonObject(final String raw) {
        final int start = raw.indexOf('{');
        final int end = raw.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) {
            throw new RuntimeException("llm_invalid_response: " + raw);
        }
        return raw.substring(start, end + 1);
    }
}
