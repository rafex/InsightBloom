package dev.rafex.insightbloom.users.domain.model;

/**
 * Mecanismo de acceso permanente de la cuenta -- distinto de {@link OtpChannel}, que es
 * solo "por dónde se manda un código puntual" (usado también para verificar registro).
 * Activar OTP_EMAIL reemplaza la contraseña como método de login (ver LoginUseCase);
 * la contraseña sigue guardada para poder volver a PASSWORD desde el perfil.
 */
public enum AuthMethod {
    PASSWORD, OTP_EMAIL
}
