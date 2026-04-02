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
}
