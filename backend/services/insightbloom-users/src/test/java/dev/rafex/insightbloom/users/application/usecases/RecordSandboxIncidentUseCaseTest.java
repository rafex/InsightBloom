package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.SandboxIncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class RecordSandboxIncidentUseCaseTest {
    private SandboxIncidentRepository repoMock;
    private RecordSandboxIncidentUseCase useCase;

    @BeforeEach
    void setup() {
        repoMock = Mockito.mock(SandboxIncidentRepository.class);
        useCase = new RecordSandboxIncidentUseCase(repoMock);
    }

    @Test
    void testRecordsValidIncident() {
        var result = useCase.execute("conf-1", "sandbox-conf1-0", 2, "user-1", "cpu_abuse", "cpu=1000m/100m");

        assertEquals("conf-1", result.getConferenceUuid());
        assertEquals("sandbox-conf1-0", result.getPodName());
        assertEquals(2, result.getSeatIndex());
        assertEquals("user-1", result.getUserUuid());
        assertEquals("cpu_abuse", result.getType());
        Mockito.verify(repoMock).save(Mockito.any());
    }

    @Test
    void testMissingConferenceUuidRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute(null, "sandbox-conf1-0", 0, "user-1", "cpu_abuse", "detail"));
        assertEquals("conference_uuid_required", ex.getMessage());
        Mockito.verifyNoInteractions(repoMock);
    }

    @Test
    void testMissingTypeRejected() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> useCase.execute("conf-1", "sandbox-conf1-0", 0, "user-1", "", "detail"));
        assertEquals("type_required", ex.getMessage());
        Mockito.verifyNoInteractions(repoMock);
    }
}
