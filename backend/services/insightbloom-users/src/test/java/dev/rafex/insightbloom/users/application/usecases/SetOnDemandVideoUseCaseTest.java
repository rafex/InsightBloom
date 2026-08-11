package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetOnDemandVideoUseCaseTest {

    private ConferenceRepository repoWithOwnedConference(final Conference conference) {
        final ConferenceRepository repository = mock(ConferenceRepository.class);
        when(repository.findByUuid(conference.getUuid())).thenReturn(Optional.of(conference));
        return repository;
    }

    @Test
    void savesProviderUrlAndCuePointsForEventOwner() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);
        final var cuePoints = List.of(
                new SetOnDemandVideoUseCase.CuePointInput(270, "Abrí la encuesta", "survey"));

        final var result = new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "https://www.youtube.com/watch?v=abc123", cuePoints);

        assertEquals("YOUTUBE", result.getOnDemandVideoProvider());
        assertEquals("https://www.youtube.com/watch?v=abc123", result.getOnDemandVideoUrl());
        assertEquals(1, result.getOnDemandCuePoints().size());
        assertEquals(270, result.getOnDemandCuePoints().get(0).atSeconds());
        verify(repository).save(conference);
        verify(repository).replaceOnDemandCuePoints(eq(conference.getUuid()), any());
    }

    @Test
    void clearingProviderAlsoClearsUrl() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        conference.setOnDemandVideoProvider("YOUTUBE");
        conference.setOnDemandVideoUrl("https://www.youtube.com/watch?v=abc123");
        final ConferenceRepository repository = repoWithOwnedConference(conference);

        final var result = new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", null, null, null);

        assertEquals(null, result.getOnDemandVideoProvider());
        assertEquals(null, result.getOnDemandVideoUrl());
    }

    @Test
    void rejectsInvalidProvider() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);

        final var ex = assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "VIMEO", "https://vimeo.com/123", null));
        assertEquals("provider_invalid", ex.getMessage());
    }

    @Test
    void rejectsBlankUrlWhenProviderSet() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);

        final var ex = assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "  ", null));
        assertEquals("url_invalid", ex.getMessage());
    }

    @Test
    void rejectsNonHttpsUrl() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);

        final var ex = assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "http://www.youtube.com/watch?v=abc123", null));
        assertEquals("url_invalid", ex.getMessage());
    }

    @Test
    void rejectsCuePointWithNegativeTimestamp() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);
        final var cuePoints = List.of(new SetOnDemandVideoUseCase.CuePointInput(-5, "Label", "survey"));

        final var ex = assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "https://www.youtube.com/watch?v=abc123", cuePoints));
        assertEquals("cue_point_at_seconds_invalid", ex.getMessage());
    }

    @Test
    void rejectsCuePointWithBlankLabelOrToolPath() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);
        final var blankLabel = List.of(new SetOnDemandVideoUseCase.CuePointInput(10, "  ", "survey"));
        final var blankToolPath = List.of(new SetOnDemandVideoUseCase.CuePointInput(10, "Label", ""));

        assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "https://www.youtube.com/watch?v=abc123", blankLabel));
        assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "owner", "YOUTUBE", "https://www.youtube.com/watch?v=abc123", blankToolPath));
    }

    @Test
    void rejectsWhenRequestingUserIsNotOwner() {
        final Conference conference = new Conference("evento", "Evento", "owner");
        final ConferenceRepository repository = repoWithOwnedConference(conference);

        final var ex = assertThrows(IllegalArgumentException.class, () -> new SetOnDemandVideoUseCase(repository)
                .execute(conference.getUuid(), "someone-else", "YOUTUBE", "https://www.youtube.com/watch?v=abc123", null));
        assertEquals("conference_not_found", ex.getMessage());
    }
}
