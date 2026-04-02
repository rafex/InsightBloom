package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.Token;
import java.util.Optional;

public interface TokenRepository {
    void save(Token token);
    Optional<Token> findByValue(String tokenValue);
}
