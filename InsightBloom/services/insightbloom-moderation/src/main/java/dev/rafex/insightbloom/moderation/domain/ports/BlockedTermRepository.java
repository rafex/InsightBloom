package dev.rafex.insightbloom.moderation.domain.ports;
import dev.rafex.insightbloom.moderation.domain.model.BlockedTerm;
import java.util.List;
public interface BlockedTermRepository {
    void save(BlockedTerm term);
    List<BlockedTerm> findAllActive();
    boolean existsByTerm(String termNormalized);
}
