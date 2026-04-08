package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.model.*;
import dev.rafex.insightbloom.query.domain.ports.CloudWordRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GetCloudUseCaseTest {
    @Test
    void execute_returnsSortedByRelevance() {
        CloudWordRepository repo = Mockito.mock(CloudWordRepository.class);
        CloudWord high = new CloudWord("uuid1","conf",MessageType.DOUBT,"ia","IA",10.0,5,Instant.now().minusSeconds(100),Instant.now(),true,Instant.now());
        CloudWord low = new CloudWord("uuid2","conf",MessageType.DOUBT,"robot","robot",2.0,1,Instant.now().minusSeconds(50),Instant.now(),true,Instant.now());
        Mockito.when(repo.findVisibleByConferenceAndType("conf",MessageType.DOUBT)).thenReturn(List.of(low, high));
        GetCloudUseCase uc = new GetCloudUseCase(repo);
        List<CloudWord> result = uc.execute("conf", MessageType.DOUBT);
        assertEquals("ia", result.get(0).getWordNormalized());
        assertEquals("robot", result.get(1).getWordNormalized());
    }
}
