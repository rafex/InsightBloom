package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.VenueSeat;

import java.util.List;
import java.util.Optional;

public interface VenueSeatRepository {
    void save(VenueSeat seat);
    void delete(String uuid);
    Optional<VenueSeat> findByUuid(String uuid);
    List<VenueSeat> findByConference(String conferenceUuid);
    void deleteByConference(String conferenceUuid);
}
