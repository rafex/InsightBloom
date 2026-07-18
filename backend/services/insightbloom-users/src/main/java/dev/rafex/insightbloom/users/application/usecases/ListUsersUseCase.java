package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.model.UserStatus;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;

import java.util.List;

public class ListUsersUseCase {
    private final UserRepository userRepository;

    public ListUsersUseCase(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Result(List<User> items, int page, int pageSize, long total) {}

    public Result execute(final int page, final int pageSize, final UserStatus status, final UserRole role,
                           final String sort) {
        final int safePage = Math.max(page, 1);
        final int safePageSize = Math.min(Math.max(pageSize, 1), 200);
        final List<User> items = userRepository.findAll(safePage, safePageSize, status, role, sort);
        final long total = userRepository.countAll(status, role);
        return new Result(items, safePage, safePageSize, total);
    }
}
