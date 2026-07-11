package dev.rafex.insightbloom.users.domain.ports;

public interface EtherpadPort {
    /**
     * Crea el pad si no existe todavia; no hace nada si ya existe (idempotente). El primer
     * asistente que abre "Notas" lo crea, los siguientes reutilizan el mismo pad.
     */
    void ensurePadExists(String padId);

    /** Borra un pad, usado por el job de limpieza de datos vencidos (ver DEC-0020). Silencioso si no existe. */
    void deletePad(String padId);
}
