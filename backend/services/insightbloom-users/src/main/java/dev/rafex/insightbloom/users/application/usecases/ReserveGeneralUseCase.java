package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Reservation;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.ReservationRepository;

/**
 * Reserva de boleto sin asiento, sujeto al aforo del evento -- aplica tanto a modo GENERAL
 * (aforo explícito del organizador) como NONE (aforo por defecto, ver DEC del 2026-07-18: el
 * servidor tiene recursos finitos y todo evento declara un aforo, incluidos los virtuales).
 * Modo SEATED no pasa por acá: el asistente elige un asiento explícito (ver ReserveSeatUseCase).
 */
public class ReserveGeneralUseCase {
    private final ConferenceRepository conferenceRepository;
    private final ReservationRepository reservationRepository;

    public ReserveGeneralUseCase(final ConferenceRepository conferenceRepository,
                                  final ReservationRepository reservationRepository) {
        this.conferenceRepository = conferenceRepository;
        this.reservationRepository = reservationRepository;
    }

    public Reservation execute(final String conferenceUuid, final String userUuid) {
        final Conference conference = conferenceRepository.findByUuid(conferenceUuid)
                .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));
        if ("SEATED".equals(conference.getSeatingMode())) {
            throw new IllegalStateException("not_general_admission");
        }
        final var existing = reservationRepository.findByConferenceAndUser(conferenceUuid, userUuid);
        if (existing.isPresent()) return existing.get();

        final boolean gotCapacity = conferenceRepository.tryIncrementReservedCount(conferenceUuid);
        if (!gotCapacity) {
            throw new IllegalStateException("capacity_exceeded");
        }
        final Reservation reservation = new Reservation(conferenceUuid, userUuid, null);
        reservationRepository.save(reservation);
        return reservation;
    }
}
