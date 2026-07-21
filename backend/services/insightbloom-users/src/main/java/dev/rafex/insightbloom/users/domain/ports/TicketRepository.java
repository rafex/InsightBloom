package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Ticket;
import java.util.List;
import java.util.Optional;

public interface TicketRepository {
    void insert(Ticket ticket);
    Optional<Ticket> findByUuid(String uuid);
    Optional<Ticket> findByCode(String conferenceUuid, String ticketCode);
    Optional<Ticket> findByConferenceAndUser(String conferenceUuid, String userUuid);
    List<Ticket> findByConference(String conferenceUuid);
    boolean claim(String uuid, String userUuid, String claimedAt);
    boolean checkIn(String uuid, String checkedInAt);
    boolean revoke(String uuid);
    void expireByConference(String conferenceUuid, String expiredAt);
}
