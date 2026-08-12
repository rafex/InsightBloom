package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.ImagePolicy;
import dev.rafex.insightbloom.users.domain.ports.ImagePolicyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImagePolicyUseCaseTest {
    @Test
    void getReturnsEmptyDefaultsWhenNoRowExists() {
        final ImagePolicyRepository repository = mock(ImagePolicyRepository.class);
        when(repository.findByConference("conf-1")).thenReturn(Optional.empty());

        final ImagePolicy policy = new ImagePolicyUseCase(repository).get("conf-1");

        assertEquals("conf-1", policy.conferenceUuid());
        assertNull(policy.allowedImages());
        assertNull(policy.blockedImages());
    }

    @Test
    void saveDelegatesToRepository() {
        final ImagePolicyRepository repository = mock(ImagePolicyRepository.class);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        final ImagePolicy saved = new ImagePolicyUseCase(repository).save("conf-1", "python", "alpine");

        assertEquals("python", saved.allowedImages());
        assertEquals("alpine", saved.blockedImages());
        verify(repository).save(saved);
    }

    @Test
    void rejectsMissingConference() {
        final ImagePolicyRepository repository = mock(ImagePolicyRepository.class);
        final var useCase = new ImagePolicyUseCase(repository);
        assertThrows(IllegalArgumentException.class, () -> useCase.save(null, "python", null));
        assertThrows(IllegalArgumentException.class, () -> useCase.save("  ", "python", null));
    }

    @Test
    void rejectsOverlyLongLists() {
        final ImagePolicyRepository repository = mock(ImagePolicyRepository.class);
        final var useCase = new ImagePolicyUseCase(repository);
        final String tooLong = "a".repeat(20_001);
        assertThrows(IllegalArgumentException.class, () -> useCase.save("conf-1", tooLong, null));
        assertThrows(IllegalArgumentException.class, () -> useCase.save("conf-1", null, tooLong));
    }
}
