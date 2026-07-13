package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.util.Optional;

/**
 * Fase 3b del IDE: dado el {@code ib_token} y la conferencia, resuelve el Service DNS del
 * sandbox activo del usuario — es lo que le permite a insightbloom-toolsgateway (que no conoce
 * usuarios ni sandboxes, solo hace de proxy) rutear cada sesion de code-server a su Pod real sin
 * exponer un target fijo por herramienta como hace con drawio/Etherpad.
 * Llamado solo desde el endpoint interno protegido por X-Internal-Auth — nunca directamente por
 * el navegador del usuario.
 */
public class ResolveSandboxTargetUseCase {
    private final ValidateTokenUseCase validateTokenUseCase;
    private final SandboxRepository sandboxRepository;
    private final String namespace;
    private final int port;

    public ResolveSandboxTargetUseCase(final ValidateTokenUseCase validateTokenUseCase,
                                        final SandboxRepository sandboxRepository,
                                        final String namespace, final int port) {
        this.validateTokenUseCase = validateTokenUseCase;
        this.sandboxRepository = sandboxRepository;
        this.namespace = namespace;
        this.port = port;
    }

    public Optional<String> execute(final String token, final String conferenceUuid) {
        final var validation = validateTokenUseCase.execute(token);
        if (!validation.valid()) {
            return Optional.empty();
        }
        // Mismo formato "http://host:port" que los targets estaticos de routesByHost
        // (drawio/Etherpad) — AuthGateHandler.proxy() y WebSocketProxyCreator ya saben
        // convertir ese esquema a ws:// cuando corresponde, sin una rama especial.
        return sandboxRepository.findByConferenceAndUser(conferenceUuid, validation.subjectUuid())
                .map(sandbox -> "http://" + sandbox.podName() + "-svc." + namespace + ".svc.cluster.local:" + port);
    }
}
