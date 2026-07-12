package dev.rafex.insightbloom.users.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Genera el JWT (RS256) que exige JaaS (8x8.vc) para unirse a una sala — a diferencia de
 * `meet.jit.si`, JaaS no permite unirse sin un token firmado, y ese mismo token es lo que le
 * evita el limite de 5 minutos del modo demo embebido de meet.jit.si (ver DEC-0020/TASK-0041,
 * "Jitsi self-hosted" originalmente; JaaS cumple el mismo rol sin correr Prosody/Jicofo/JVB
 * propios). Si no hay credenciales de JaaS configuradas, {@link #execute} devuelve
 * {@link Optional#empty()} y el frontend recae en `meet.jit.si` publico.
 */
public class GenerateJaasTokenUseCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long EXPIRATION_SECONDS = 2 * 60 * 60; // 2h, suficiente para una charla
    private static final long NOT_BEFORE_SKEW_SECONDS = 10;

    private final String appId;
    private final String apiKeyId;
    private final PrivateKey privateKey;
    private final EventPermissionGuard eventPermissionGuard;
    private final UserRepository userRepository;

    public GenerateJaasTokenUseCase(final String appId, final String apiKeyId,
                                     final String privateKeyBase64,
                                     final EventPermissionGuard eventPermissionGuard,
                                     final UserRepository userRepository) {
        this.appId = appId;
        this.apiKeyId = apiKeyId;
        this.eventPermissionGuard = eventPermissionGuard;
        this.userRepository = userRepository;
        this.privateKey = parsePrivateKey(privateKeyBase64);
    }

    public record JaasToken(String token, String appId, String roomName) {}

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && privateKey != null;
    }

    public Optional<JaasToken> execute(final String conferenceUuid, final String userUuid,
                                        final String userLegacyRole) {
        if (!isConfigured()) return Optional.empty();

        final boolean moderator = eventPermissionGuard.hasPermission(
                conferenceUuid, userUuid, userLegacyRole, Permission.VIDEO_MODERATE);
        final String roomName = "insightbloom-" + conferenceUuid.replace("-", "");
        final Optional<User> user = userRepository.findByUuid(userUuid);

        final long now = Instant.now().getEpochSecond();
        final ObjectNode header = MAPPER.createObjectNode();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        header.put("kid", apiKeyId);

        final ObjectNode userNode = MAPPER.createObjectNode();
        userNode.put("id", userUuid);
        userNode.put("name", user.map(GenerateJaasTokenUseCase::displayName).orElse("Invitado"));
        userNode.put("email", user.map(User::getEmail).orElse(""));
        userNode.put("moderator", moderator ? "true" : "false");

        final ObjectNode featuresNode = MAPPER.createObjectNode();
        featuresNode.put("livestreaming", "false");
        featuresNode.put("recording", "false");
        featuresNode.put("transcription", "false");
        featuresNode.put("outbound-call", "false");

        final ObjectNode contextNode = MAPPER.createObjectNode();
        contextNode.set("user", userNode);
        contextNode.set("features", featuresNode);

        final ObjectNode payload = MAPPER.createObjectNode();
        payload.put("aud", "jitsi");
        payload.put("iss", "chat");
        payload.put("sub", appId);
        payload.put("room", roomName);
        payload.put("exp", now + EXPIRATION_SECONDS);
        payload.put("nbf", now - NOT_BEFORE_SKEW_SECONDS);
        payload.set("context", contextNode);

        final String signed = sign(header, payload);
        return Optional.of(new JaasToken(signed, appId, roomName));
    }

    private String sign(final ObjectNode header, final ObjectNode payload) {
        try {
            final String encodedHeader = base64UrlEncode(MAPPER.writeValueAsBytes(header));
            final String encodedPayload = base64UrlEncode(MAPPER.writeValueAsBytes(payload));
            final String data = encodedHeader + "." + encodedPayload;
            final Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            final String encodedSignature = base64UrlEncode(signature.sign());
            return data + "." + encodedSignature;
        } catch (final Exception e) {
            throw new IllegalStateException("No se pudo firmar el token de JaaS", e);
        }
    }

    private static String displayName(final User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) return user.getDisplayName();
        return user.getUsername() != null ? user.getUsername() : "Invitado";
    }

    private static String base64UrlEncode(final byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static PrivateKey parsePrivateKey(final String privateKeyBase64) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) return null;
        try {
            // El secreto llega en base64 de un base64: primero decodificamos el "sobre" (asi lo
            // subimos a GitHub Secrets para evitar problemas de shell con saltos de linea), lo
            // que da el PEM en texto; luego quitamos las cabeceras PEM y decodificamos el cuerpo.
            final String pem = new String(Base64.getDecoder().decode(privateKeyBase64.trim()), StandardCharsets.UTF_8);
            final String base64Body = pem
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            final byte[] keyBytes = Base64.getDecoder().decode(base64Body);
            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (final Exception e) {
            return null;
        }
    }
}
