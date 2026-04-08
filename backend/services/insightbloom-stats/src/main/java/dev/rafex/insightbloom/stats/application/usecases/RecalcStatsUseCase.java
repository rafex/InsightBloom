package dev.rafex.insightbloom.stats.application.usecases;
import dev.rafex.insightbloom.stats.domain.model.MessageType;
import dev.rafex.insightbloom.stats.domain.model.WordStats;
import dev.rafex.insightbloom.stats.domain.ports.WordStatsRepository;
import dev.rafex.insightbloom.stats.domain.services.RelevanceService;
public class RecalcStatsUseCase {
    private final WordStatsRepository repo;
    private final RelevanceService relevanceService;
    public RecalcStatsUseCase(WordStatsRepository repo, RelevanceService relevanceService) {
        this.repo = repo; this.relevanceService = relevanceService;
    }
    public record RecalcRequest(
        String conferenceUuid, String wordNormalized, String wordCanonical,
        String messageType, String wordIntent, boolean visible
    ) {}
    public void execute(RecalcRequest req) {
        MessageType type = MessageType.valueOf(req.messageType().toUpperCase());
        WordStats stats = repo.findByConferenceAndWord(req.conferenceUuid(), req.wordNormalized(), type)
            .orElse(new WordStats(req.conferenceUuid(), type, req.wordNormalized(), req.wordCanonical()));
        stats.setWordCanonical(req.wordCanonical());
        stats.setCountTotal(stats.getCountTotal() + 1);
        if (req.visible()) {
            stats.setCountVisible(stats.getCountVisible() + 1);
            double intentW = relevanceService.intentWeight(req.wordIntent());
            // running average: new_avg = (old_avg * (n-1) + new_val) / n
            long n = stats.getCountVisible();
            double newAvg = n == 1 ? intentW : (stats.getScoreIntent() * (n-1) + intentW) / n;
            stats.setScoreIntent(newAvg);
        } else {
            stats.setCountCensored(stats.getCountCensored() + 1);
        }
        stats.recalculate();
        repo.save(stats);
    }
}
