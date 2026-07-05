package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.OtpChannel;
import dev.rafex.insightbloom.users.domain.model.Token;
import dev.rafex.insightbloom.users.domain.model.TokenKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.model.UserRole;
import dev.rafex.insightbloom.users.domain.ports.OtpCodeRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.TokenService;

public class VerifyOtpUseCase {
    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    public VerifyOtpUseCase(final OtpCodeRepository otpCodeRepository, final UserRepository userRepository,
                             final TokenService tokenService) {
        this.otpCodeRepository = otpCodeRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public record Request(String identifier, String code) {}
    public record Result(String token, String userUuid, String role, String expiresAt) {}

    public Result execute(final Request req) {
        final var otpCode = otpCodeRepository.findLatestActive(req.identifier())
                .orElseThrow(() -> new IllegalArgumentException("otp_not_found"));
        if (!otpCode.isValid(req.code())) {
            throw new IllegalArgumentException("otp_invalid_or_expired");
        }
        otpCodeRepository.markConsumed(otpCode.getUuid());

        final User user = (otpCode.getChannel() == OtpChannel.EMAIL
                ? userRepository.findByEmail(req.identifier())
                : userRepository.findByPhone(req.identifier()))
                .orElseThrow(() -> new IllegalArgumentException("user_not_found"));

        if (otpCode.getChannel() == OtpChannel.EMAIL) user.markEmailVerified();
        else user.markPhoneVerified();
        userRepository.save(user);

        final Token token = tokenService.issueUserToken(user.getUuid(), TokenKind.USER);
        return new Result(token.getTokenValue(), user.getUuid(), UserRole.toCsv(user.getRoles()),
                token.getExpiresAt().toString());
    }
}
