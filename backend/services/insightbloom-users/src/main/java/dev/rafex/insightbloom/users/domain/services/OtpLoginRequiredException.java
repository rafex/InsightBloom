package dev.rafex.insightbloom.users.domain.services;

/**
 * Lanzada por {@link dev.rafex.insightbloom.users.application.usecases.LoginUseCase} cuando la
 * cuenta tiene activo {@code AuthMethod.OTP_EMAIL} -- la contrasena ya no sirve para entrar (ver
 * SetAuthMethodUseCase). El handler HTTP la mapea a un codigo distinguible del generico
 * "credenciales invalidas" para que el frontend pueda invitar a usar la otra pestana de login.
 */
public class OtpLoginRequiredException extends RuntimeException {
    public OtpLoginRequiredException() {
        super("otp_login_required");
    }
}
