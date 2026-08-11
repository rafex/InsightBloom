package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.CanvasConfig;
import dev.rafex.insightbloom.users.domain.model.OnDemandCuePoint;
import java.util.List;
import java.util.Optional;

public interface ConferenceRepository {
    void save(Conference conference);
    void replaceCanvasConfigs(String conferenceUuid, List<CanvasConfig> configs);
    void replaceOnDemandCuePoints(String conferenceUuid, List<OnDemandCuePoint> cuePoints);
    void delete(String uuid);
    Optional<Conference> findByUuid(String uuid);
    Optional<Conference> findByFriendlyId(String friendlyId);
    Optional<Conference> findByShortCode(String shortCode);
    boolean existsByFriendlyId(String friendlyId);
    List<Conference> findAll();
    List<Conference> findByUser(String userUuid);
    List<Conference> findPendingReminder();

    /** Eventos con fecha/hora de fin conocida cuyo pad de Etherpad aun no se ha purgado (ver DEC-0020). */
    List<Conference> findPendingNotesPurge();

    /** Eventos con fecha/hora de fin conocida cuyo diagrama de drawio aun no se ha purgado (ver DEC-0020). */
    List<Conference> findPendingDiagramPurge();

    /**
     * Incrementa atómicamente reserved_count solo si aún no se alcanzó capacity, evitando
     * sobre-reservar en modo GENERAL sin necesidad de locks de aplicación.
     * @return true si el incremento tuvo éxito (había cupo), false si ya estaba lleno.
     */
    boolean tryIncrementReservedCount(String conferenceUuid);

    /** Libera un cupo (usado al cancelar una reserva en modo GENERAL). */
    void decrementReservedCount(String conferenceUuid);
}
