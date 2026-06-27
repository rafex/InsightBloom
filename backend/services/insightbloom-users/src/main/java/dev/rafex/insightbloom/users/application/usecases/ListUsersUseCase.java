package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.List;

public class ListUsersUseCase {
    private final UserRepository userRepository;

    public ListUsersUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Result(List<User> items, int page, int pageSize, long total) {}

    public Result execute(final int page, final int pageSize) {
        final int safePage = Math.max(page, 1);
        final int safePageSize = Math.min(Math.max(pageSize, 1), 200);
        final List<User> items = userRepository.findAll(safePage, safePageSize);
        final long total = userRepository.countAll();
        return new Result(items, safePage, safePageSize, total);
    }
}
