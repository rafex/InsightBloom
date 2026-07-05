package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.DownloadEventRepository;

public class GetDownloadCountsUseCase {
    private final DownloadEventRepository downloadEventRepository;

    public GetDownloadCountsUseCase(final DownloadEventRepository downloadEventRepository) {
        this.downloadEventRepository = downloadEventRepository;
    }

    public record Counts(long certificate, long presentation) {}

    public Counts execute(final String conferenceUuid) {
        return new Counts(
                downloadEventRepository.countByConferenceAndKind(conferenceUuid, "certificate"),
                downloadEventRepository.countByConferenceAndKind(conferenceUuid, "presentation"));
    }
}
