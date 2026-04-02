package dev.rafex.insightbloom.ingest.domain.services;
import dev.rafex.insightbloom.ingest.domain.model.DetailIntent;
import dev.rafex.insightbloom.ingest.domain.model.WordIntent;
public class IntentClassificationService {
    public WordIntent classifyWord(String word, String detail) {
        if (word == null && detail == null) return WordIntent.INTERES;
        String combined = ((word != null ? word : "") + " " + (detail != null ? detail : "")).toLowerCase();
        if (combined.contains("?") || combined.contains("como") || combined.contains("que es") || combined.contains("por que")) {
            return WordIntent.PREGUNTA;
        }
        if (combined.contains("duda") || combined.contains("no entiendo") || combined.contains("no entiendo")) {
            return WordIntent.DUDA;
        }
        if (combined.contains("idea") || combined.contains("propuesta") || combined.contains("podria")) {
            return WordIntent.IDEA;
        }
        return WordIntent.INTERES;
    }
    public DetailIntent classifyDetail(String detail) {
        if (detail == null || detail.isBlank()) return DetailIntent.PREGUNTA;
        String d = detail.toLowerCase();
        if (d.contains("?") || d.contains("como") || d.contains("que")) return DetailIntent.PREGUNTA;
        if (d.contains("problema") || d.contains("preocupa") || d.contains("malo")) return DetailIntent.PREOCUPACION;
        if (d.contains("critica") || d.contains("mal") || d.contains("incorrecto")) return DetailIntent.CRITICA;
        if (d.contains("propuesta") || d.contains("podria") || d.contains("deberia")) return DetailIntent.PROPUESTA;
        return DetailIntent.PREGUNTA;
    }
}
