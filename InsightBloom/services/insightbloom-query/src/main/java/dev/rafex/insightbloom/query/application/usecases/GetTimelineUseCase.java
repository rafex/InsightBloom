package dev.rafex.insightbloom.query.application.usecases;
import dev.rafex.insightbloom.query.domain.model.WordTimeline;
import dev.rafex.insightbloom.query.domain.ports.WordTimelineRepository;
import java.util.Comparator;
import java.util.List;
public class GetTimelineUseCase {
    private final WordTimelineRepository repo;
    public GetTimelineUseCase(WordTimelineRepository repo) { this.repo = repo; }
    public List<WordTimeline> execute(String conferenceUuid, String wordNormalized) {
        return repo.findVisibleByConferenceAndWord(conferenceUuid, wordNormalized).stream()
            .sorted(Comparator.comparing(WordTimeline::getReceivedAt))
            .toList();
    }
}
