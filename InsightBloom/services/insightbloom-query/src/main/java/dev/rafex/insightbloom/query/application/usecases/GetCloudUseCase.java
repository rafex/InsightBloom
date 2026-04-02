package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.model.CloudWord;
import dev.rafex.insightbloom.query.domain.model.MessageType;
import dev.rafex.insightbloom.query.domain.ports.CloudWordRepository;
import java.util.Comparator;
import java.util.List;
public class GetCloudUseCase {
    private final CloudWordRepository repo;
    public GetCloudUseCase(CloudWordRepository repo) { this.repo = repo; }
    public List<CloudWord> execute(String conferenceUuid, MessageType type) {
        return repo.findVisibleByConferenceAndType(conferenceUuid, type).stream()
            .sorted(Comparator.comparingDouble(CloudWord::getRelevanceScore).reversed()
                .thenComparing(Comparator.comparingLong(CloudWord::getMessageCount).reversed())
                .thenComparing(CloudWord::getFirstSeenAt)
                .thenComparing(CloudWord::getWordCanonical))
            .toList();
    }
}
