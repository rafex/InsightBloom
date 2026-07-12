package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

public class SetSandboxConfigUseCase {
    private final ConferenceRepository conferenceRepository;
    private final int maxPoolSizePerEvent;

    public SetSandboxConfigUseCase(ConferenceRepository conferenceRepository, int maxPoolSizePerEvent) {
        this.conferenceRepository = conferenceRepository;
        this.maxPoolSizePerEvent = maxPoolSizePerEvent;
    }

    public Conference execute(
        String conferenceUuid,
        String sandboxVariant,
        Integer sandboxPoolSize,
        String sandboxExtraPackages,
        String sandboxRemoteGitUrl
    ) {
        var conf = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        if (sandboxPoolSize != null && sandboxPoolSize <= 0) {
            throw new IllegalArgumentException("pool_size_must_be_positive");
        }
        if (sandboxPoolSize != null && sandboxPoolSize > maxPoolSizePerEvent) {
            throw new IllegalArgumentException("pool_size_exceeds_platform_max");
        }

        conf.setSandboxVariant(sandboxVariant);
        conf.setSandboxPoolSize(sandboxPoolSize);
        conf.setSandboxExtraPackages(sandboxExtraPackages);
        conf.setSandboxRemoteGitUrl(sandboxRemoteGitUrl);

        conferenceRepository.save(conf);
        return conf;
    }
}
