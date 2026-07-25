package dev.rafex.insightbloom.users.application.usecases;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.rafex.insightbloom.users.domain.model.Conference;
import dev.rafex.insightbloom.users.domain.model.Permission;
import dev.rafex.insightbloom.users.domain.model.ToolKind;
import dev.rafex.insightbloom.users.domain.model.User;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;
import dev.rafex.insightbloom.users.domain.ports.JaasUsageRepository;
import dev.rafex.insightbloom.users.domain.ports.UserRepository;
import dev.rafex.insightbloom.users.domain.services.DeviceAccessGuard;
import dev.rafex.insightbloom.users.domain.services.EventPermissionGuard;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Genera el JWT (RS256) que exige JaaS (8x8.vc) para unirse a una sala — a diferencia de
 * `meet.jit.si`, JaaS no permite unirse sin un token firmado, y ese mismo token es lo que le
 * evita el limite de 5 minutos del modo demo embebido de meet.jit.si (ver DEC-0020/TASK-0041,
 * "Jitsi self-hosted" originalmente; JaaS cumple el mismo rol sin correr Prosody/Jicofo/JVB
 * propios). Si no hay credenciales de JaaS configuradas, {@link #execute} devuelve
 * {@link Optional#empty()} y el frontend solo puede usar `meet.jit.si` publico para eventos sin
 * boletos. Los eventos ticketed requieren que el usuario tenga un boleto vigente antes de emitir
 * el JWT.
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
    private final ConferenceRepository conferenceRepository;
    private final DeviceAccessGuard deviceAccessGuard;
    private final TicketUseCase ticketUseCase;
    private final JaasUsageRepository jaasUsageRepository;

    public GenerateJaasTokenUseCase(final String appId, final String apiKeyId,
                                     final String privateKeyBase64,
                                     final EventPermissionGuard eventPermissionGuard,
                                     final UserRepository userRepository,
                                     final ConferenceRepository conferenceRepository,
                                     final DeviceAccessGuard deviceAccessGuard) {
        this(appId, apiKeyId, privateKeyBase64, eventPermissionGuard, userRepository,
                conferenceRepository, deviceAccessGuard, null);
    }

    public GenerateJaasTokenUseCase(final String appId, final String apiKeyId,
                                     final String privateKeyBase64,
                                     final EventPermissionGuard eventPermissionGuard,
                                     final UserRepository userRepository,
                                     final ConferenceRepository conferenceRepository,
                                     final DeviceAccessGuard deviceAccessGuard,
                                     final TicketUseCase ticketUseCase) {
        this(appId, apiKeyId, privateKeyBase64, eventPermissionGuard, userRepository,
                conferenceRepository, deviceAccessGuard, ticketUseCase, null);
    }

    public GenerateJaasTokenUseCase(final String appId, final String apiKeyId,
                                     final String privateKeyBase64,
                                     final EventPermissionGuard eventPermissionGuard,
                                     final UserRepository userRepository,
                                     final ConferenceRepository conferenceRepository,
                                     final DeviceAccessGuard deviceAccessGuard,
                                     final TicketUseCase ticketUseCase,
                                     final JaasUsageRepository jaasUsageRepository) {
        this.appId = appId;
        this.apiKeyId = apiKeyId;
        this.eventPermissionGuard = eventPermissionGuard;
        this.userRepository = userRepository;
        this.conferenceRepository = conferenceRepository;
        this.deviceAccessGuard = deviceAccessGuard;
        this.ticketUseCase = ticketUseCase;
        this.jaasUsageRepository = jaasUsageRepository;
        this.privateKey = parsePrivateKey(privateKeyBase64);
    }

    public record JaasToken(String token, String appId, String roomName, String sessionUuid,
                            List<String> revokedDeviceFingerprints) {
        public JaasToken(final String token, final String appId, final String roomName) {
            this(token, appId, roomName, null, List.of());
        }
    }

    /** Distingue "JaaS no configurado en este despliegue" de "dispositivo bloqueado" -- antes de
     *  este control de acceso ambos casos colapsaban en {@code Optional.empty()}. */
    public sealed interface JaasResult {
        record NotConfigured() implements JaasResult {}
        record Blocked() implements JaasResult {}
        record TicketRequired() implements JaasResult {}
        record ConferenceNotFound() implements JaasResult {}
        record Issued(JaasToken token) implements JaasResult {}
    }

    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && privateKey != null;
    }

    public JaasResult execute(final String conferenceUuid, final String userUuid,
                               final String userLegacyRole, final String deviceFingerprint) {
        if (!isConfigured()) return new JaasResult.NotConfigured();

        // La emisión del JWT es la frontera de seguridad de JaaS. No basta con haber iniciado
        // sesión: en un evento ticketed el usuario debe tener un boleto vigente. hasAccess también
        // crea el boleto operativo contado del creador de eventos antiguos, y mantiene la misma
        // regla para moderadores a los que se les asignó un rol operativo.
        final Conference conference = ticketUseCase == null
                ? null
                : conferenceRepository.findByUuid(conferenceUuid).orElse(null);
        if (ticketUseCase != null) {
            if (conference == null) return new JaasResult.ConferenceNotFound();
            if (!ticketUseCase.hasAccess(conference, userUuid)) {
                return new JaasResult.TicketRequired();
            }
        }

        // deviceFingerprint puede llegar null durante un rollout escalonado (frontend viejo sin
        // el header todavia) -- en ese caso se omite el control de acceso por dispositivo.
        DeviceAccessGuard.Registration registration = null;
        if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
            final Conference deviceConference = conference != null
                    ? conference
                    : conferenceRepository.findByUuid(conferenceUuid).orElse(null);
            if (deviceConference != null) {
                registration = deviceAccessGuard.registerAndCollectRevocations(
                        conferenceUuid, userUuid, ToolKind.VIDEO, deviceFingerprint, deviceConference);
                if (registration.access() instanceof DeviceAccessGuard.DeviceAccessResult.Blocked) {
                    return new JaasResult.Blocked();
                }
            }
        }

        if (jaasUsageRepository != null) {
            jaasUsageRepository.recordUniqueParticipant(
                    YearMonth.now(ZoneOffset.UTC).toString(), userUuid);
        }

        final boolean moderator = eventPermissionGuard.hasPermission(
                conferenceUuid, userUuid, userLegacyRole, Permission.VIDEO_MODERATE);
        final String roomName = "insightbloom-" + conferenceUuid.replace("-", "");
        final Optional<User> user = userRepository.findByUuid(userUuid);

        final long now = Instant.now().getEpochSecond();
        final ObjectNode header = MAPPER.createObjectNode();
        header.put("alg", "RS256");
        header.put("typ", "JWT");
        // JaaS exige que el kid tenga el formato "{appId}/{apiKeyId}" — el kid "suelto" (solo
        // el uuid de la key) hace que el backend de JaaS rechace el token con
        // "Key ID (kid) does not match sub" ya que no puede resolver la clave publica asociada.
        header.put("kid", appId + "/" + apiKeyId);

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
        return new JaasResult.Issued(new JaasToken(
                signed, appId, roomName,
                registration != null ? registration.sessionUuid() : null,
                registration != null ? registration.revokedDeviceFingerprints() : List.of()));
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
