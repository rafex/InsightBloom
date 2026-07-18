package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.DownloadEventRepository;

import java.util.Set;

public class RecordDownloadUseCase {
    private static final Set<String> VALID_KINDS = Set.of("certificate", "presentation");

    private final DownloadEventRepository downloadEventRepository;

    public RecordDownloadUseCase(final DownloadEventRepository downloadEventRepository) {
        this.downloadEventRepository = downloadEventRepository;
    }

    public void execute(final String conferenceUuid, final String kind) {
        execute(conferenceUuid, kind, null);
    }

    public void execute(final String conferenceUuid, final String kind, final String userUuid) {
        if (conferenceUuid == null || conferenceUuid.isBlank() || !VALID_KINDS.contains(kind)) return;
        downloadEventRepository.record(conferenceUuid, kind, userUuid);
    }
}
