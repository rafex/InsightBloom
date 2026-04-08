package dev.rafex.insightbloom.stats.application.usecases;
import dev.rafex.insightbloom.stats.domain.model.WordStats;
import dev.rafex.insightbloom.stats.domain.ports.WordStatsRepository;
import java.util.List;
public class GetStatsUseCase {
    private final WordStatsRepository repo;
    public GetStatsUseCase(WordStatsRepository repo) { this.repo = repo; }
    public List<WordStats> overview(String conferenceUuid) {
        return repo.findByConference(conferenceUuid);
    }
    public List<WordStats> relevance(String conferenceUuid, String type) {
        return repo.findByConference(conferenceUuid).stream()
            .filter(s -> type == null || type.isBlank() || s.getMessageType().name().equalsIgnoreCase(type))
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .toList();
    }
}
