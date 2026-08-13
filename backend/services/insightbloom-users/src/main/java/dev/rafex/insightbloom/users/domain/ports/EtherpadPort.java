package dev.rafex.insightbloom.users.domain.ports;

public interface EtherpadPort {
    record PadContent(String text, String html) {}

    /**
     * Crea el pad si no existe todavia; no hace nada si ya existe (idempotente). El primer
     * asistente que abre "Notas" lo crea, los siguientes reutilizan el mismo pad.
     */
    void ensurePadExists(String padId);

    /** Lee el contenido actual para exportarlo sin exponer la API key al navegador. */
    PadContent readPad(String padId);

    /** Borra un pad, usado por el job de limpieza de datos vencidos (ver DEC-0020). Silencioso si no existe. */
    void deletePad(String padId);

    /** Borra el pad grupal y todos los pads privados derivados del evento. */
    void deletePadsForConference(String conferenceUuid);

    /**
     * ID de lectura de un pad (getReadOnlyID de la API de Etherpad). Etherpad no soporta un
     * parámetro "readonly" sobre /p/{padId}: la única forma real de solo-lectura es navegar a
     * /p/{readOnlyId}, una URL distinta. Se usa en modo MODERATOR_ONLY para que los asistentes
     * naveguen a esa URL en vez del pad editable real.
     */
    String getReadOnlyId(String padId);
}
