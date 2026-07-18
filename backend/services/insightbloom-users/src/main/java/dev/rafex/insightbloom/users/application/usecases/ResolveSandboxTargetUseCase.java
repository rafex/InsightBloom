package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.SandboxRepository;

import java.util.Optional;

/**
 * Fase 3b del IDE: dado el {@code ib_token} y la conferencia, resuelve el Service DNS (y puerto)
 * del sandbox activo del usuario — es lo que le permite a insightbloom-toolsgateway (que no
 * conoce usuarios ni sandboxes, solo hace de proxy) rutear cada sesion de code-server a su Pod
 * real sin exponer un target fijo por herramienta como hace con drawio/Etherpad.
 * Llamado solo desde el endpoint interno protegido por X-Internal-Auth — nunca directamente por
 * el navegador del usuario.
 *
 * Pods "neovim" multi-asiento (2026-07): varios alumnos pueden compartir el mismo Pod/Service,
 * cada uno en su propio puerto ({@link Sandbox#seatPort}) -- {@code basePort} es el puerto del
 * asiento 0; el puerto real de cada alumno es {@code basePort + seatIndex} (0 para el caso comun
 * de 1 asiento por Pod, identico al comportamiento de siempre).
 */
public class ResolveSandboxTargetUseCase {
    private final ValidateTokenUseCase validateTokenUseCase;
    private final SandboxRepository sandboxRepository;
    private final String namespace;
    private final int basePort;

    public ResolveSandboxTargetUseCase(final ValidateTokenUseCase validateTokenUseCase,
                                        final SandboxRepository sandboxRepository,
                                        final String namespace, final int basePort) {
        this.validateTokenUseCase = validateTokenUseCase;
        this.sandboxRepository = sandboxRepository;
        this.namespace = namespace;
        this.basePort = basePort;
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
                .map(sandbox -> "http://" + sandbox.podName() + "-svc." + namespace + ".svc.cluster.local:"
                        + sandbox.seatPort(basePort));
    }
}
