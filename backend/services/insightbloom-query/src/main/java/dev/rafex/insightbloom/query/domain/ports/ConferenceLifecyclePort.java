package dev.rafex.insightbloom.query.domain.ports;

/**
 * AUD-06: las nubes publicas (doubts/topics/timeline) no tenian ninguna comprobacion de que la
 * conferencia consultada siga activa/no expirada -- cualquiera con un UUID podia leer datos de un
 * evento desactivado o vencido. Este puerto consulta insightbloom-users (fuente de verdad del
 * ciclo de vida de la conferencia) antes de servir esos datos.
 */
public interface ConferenceLifecyclePort {
    /** true si la conferencia existe, su status es ACTIVE y no esta expirada. */
    boolean isActive(String conferenceUuid);
}
