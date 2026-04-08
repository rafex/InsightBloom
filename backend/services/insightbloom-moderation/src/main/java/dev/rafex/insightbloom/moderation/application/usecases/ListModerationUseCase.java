package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.model.ModerationMessage;
import dev.rafex.insightbloom.moderation.domain.model.ModerationWord;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationMessageRepository;
import dev.rafex.insightbloom.moderation.domain.ports.ModerationWordRepository;
import java.util.List;
public class ListModerationUseCase {
    private final ModerationWordRepository wordRepo;
    private final ModerationMessageRepository messageRepo;
    public ListModerationUseCase(ModerationWordRepository wordRepo, ModerationMessageRepository messageRepo) {
        this.wordRepo = wordRepo; this.messageRepo = messageRepo;
    }
    public record PagedResult<T>(List<T> items, long total, int page, int pageSize) {}
    public PagedResult<ModerationWord> listWords(String conferenceUuid, int page, int pageSize) {
        List<ModerationWord> items = wordRepo.findByConference(conferenceUuid, page, pageSize);
        long total = wordRepo.countByConference(conferenceUuid);
        return new PagedResult<>(items, total, page, pageSize);
    }
    public PagedResult<ModerationWord> listWords(String conferenceUuid, String status, int page, int pageSize) {
        if (status == null || status.isBlank()) return listWords(conferenceUuid, page, pageSize);
        List<ModerationWord> items = wordRepo.findByConferenceAndStatus(conferenceUuid, status, page, pageSize);
        long total = wordRepo.countByConferenceAndStatus(conferenceUuid, status);
        return new PagedResult<>(items, total, page, pageSize);
    }
    public PagedResult<ModerationMessage> listMessages(String conferenceUuid, int page, int pageSize) {
        List<ModerationMessage> items = messageRepo.findByConference(conferenceUuid, page, pageSize);
        long total = messageRepo.countByConference(conferenceUuid);
        return new PagedResult<>(items, total, page, pageSize);
    }
    public PagedResult<ModerationMessage> listMessages(String conferenceUuid, String status, int page, int pageSize) {
        if (status == null || status.isBlank()) return listMessages(conferenceUuid, page, pageSize);
        List<ModerationMessage> items = messageRepo.findByConferenceAndStatus(conferenceUuid, status, page, pageSize);
        long total = messageRepo.countByConferenceAndStatus(conferenceUuid, status);
        return new PagedResult<>(items, total, page, pageSize);
    }
}
