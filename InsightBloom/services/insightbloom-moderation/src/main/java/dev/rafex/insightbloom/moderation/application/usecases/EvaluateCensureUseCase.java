package dev.rafex.insightbloom.moderation.application.usecases;
import dev.rafex.insightbloom.moderation.domain.services.AutoCensureService;
public class EvaluateCensureUseCase {
    private final AutoCensureService autoCensureService;
    public EvaluateCensureUseCase(AutoCensureService autoCensureService) {
        this.autoCensureService = autoCensureService;
    }
    public record EvaluateRequest(String word, String detail) {}
    public record EvaluateResult(boolean wordBlocked, boolean detailBlocked) {}
    public EvaluateResult execute(EvaluateRequest req) {
        var result = autoCensureService.evaluate(req.word(), req.detail());
        return new EvaluateResult(result.wordBlocked(), result.detailBlocked());
    }
}
