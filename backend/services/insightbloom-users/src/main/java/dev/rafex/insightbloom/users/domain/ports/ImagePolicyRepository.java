package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;

import java.util.Optional;

public interface ImagePolicyRepository {
    Optional<ImagePolicy> findByConference(String conferenceUuid);

    ImagePolicy save(ImagePolicy policy);

    void deleteByConference(String conferenceUuid);
}
