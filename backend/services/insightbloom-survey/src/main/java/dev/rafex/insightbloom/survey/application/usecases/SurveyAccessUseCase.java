package dev.rafex.insightbloom.survey.application.usecases;

import dev.rafex.insightbloom.survey.domain.ports.SurveyAccessRepository;

import java.util.List;

public class SurveyAccessUseCase {
    private final SurveyAccessRepository repository;

    public SurveyAccessUseCase(final SurveyAccessRepository repository) { this.repository = repository; }

    public boolean isReleased(final String conferenceUuid, final String userUuid) {
        return userUuid != null && repository.isReleased(conferenceUuid, userUuid);
    }

    public boolean isReleasedForAll(final String conferenceUuid) {
        return repository.isReleasedForAll(conferenceUuid);
    }

    public void releaseForAll(final String conferenceUuid) {
        repository.releaseForAll(conferenceUuid);
    }

    public void releaseUsers(final String conferenceUuid, final List<String> userUuids) {
        if (userUuids == null || userUuids.isEmpty()) throw new IllegalArgumentException("user_uuids_required");
        final var filtered = userUuids.stream()
                .filter(uuid -> uuid != null && !uuid.isBlank()).distinct().toList();
        if (filtered.isEmpty()) throw new IllegalArgumentException("user_uuids_required");
        repository.releaseUsers(conferenceUuid, filtered);
    }
}
