package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileEntry;

import java.util.List;
import java.util.Optional;

/**
 * Provisión real del ambiente de un sandbox (Pod + Service de code-server). El nombre del
 * recurso (podName) es el identificador estable usado tanto para crear como para borrar/consultar
 * — ver {@code KubernetesPodClient} para la implementación real contra el API de Kubernetes, y
 * los tests de {@code AssignSandboxUseCase}/{@code PurgeSandboxPoolUseCase} para el fake usado en
 * unit tests.
 */
public interface SandboxOrchestrator {

    /**
     * Idempotente: si el Pod/Service ya existen con ese nombre, no falla.
     * @param conferenceUuid usado para etiquetar el Pod con {@link dev.rafex.insightbloom.users.domain.model.Sandbox#conferenceLabel}
     *                        (Fase 3c: la NetworkPolicy de internet habilitado selecciona por esa label).
     */
    /**
     * @param jvmHeapMb -Xmx (en MB) para las JVMs del sandbox (jdt.ls, java/mvn que corra el
     *                  alumno) via JDK_JAVA_OPTIONS; null usa el default chico de la
     *                  implementacion (pedido explicito: JVMs chicas por defecto, no "libres"
     *                  tomando todo lo que el contenedor les deje via cgroups).
     * @param seatsPerPod cantidad de alumnos que compartiran este Pod (solo relevante en modo
     *                    terminal-nvim; ignorado en cualquier otro modo, un Pod = un alumno
     *                    siempre). Null usa el default de la implementacion. Determina cuantos
     *                    puertos expone el Pod/Service -- llamar siempre con el mismo valor para
     *                    un {@code podName} dado (no cambia dinamicamente el Pod ya creado).
     */
    void createSandbox(String podName, String conferenceUuid, String variant,
                        String remoteGitUrl, boolean internetEnabled, Integer jvmHeapMb, Integer seatsPerPod);

    /**
     * Fase B (2026-07): pide al seat-agent de un Pod neovim multi-asiento ya corriendo que
     * cree (si no existe) el usuario Linux y el {@code ttyd} de un asiento especifico. No hace
     * nada en Pods de un solo asiento (ni existe seat-agent ahi) -- llamar solo cuando
     * {@link AssignSandboxUseCase} se une a un Pod compartido ya existente (no cuando lo crea:
     * en ese caso el asiento 0 se aprovisiona solo, al arrancar el Pod).
     * @param seatIndex asiento dentro del Pod, 0..seatsPerPod-1
     * @param userUuid alumno real asignado a ese asiento (para logging/incidentes, Fase C)
     */
    void provisionSeat(String podName, int seatIndex, String userUuid);

    /**
     * Como {@link #provisionSeat}, pero un UNICO intento rapido en vez de reintentar durante
     * varios segundos, y nunca lanza excepcion -- el llamador NO puede permitirse bloquear la
     * respuesta HTTP mientras un Pod recien creado todavia esta con la imagen bajando/agendandose
     * (puede tardar minutos, no segundos). Pensado para llamarse en cada poll de estado
     * (ver GetSandbox/AssignSandboxUseCase): si el seat-agent todavia no responde, devuelve
     * {@code false} sin mas, y el proximo poll (el frontend ya reintenta solo mientras el status
     * sea PENDING) vuelve a intentarlo -- auto-sana sin intervencion manual.
     * @return true si el asiento quedo confirmado (o ya lo estaba, es idempotente).
     */
    boolean ensureSeatReady(String podName, int seatIndex, String userUuid);

    /** Borra Pod + Service; no falla si ya no existen (ej. purga repetida). */
    void deleteSandbox(String podName);

    /** "Pending", "Running", "Failed", "Succeeded", "Unknown", o null si el Pod no existe. */
    String getPhase(String podName);

    /**
     * A diferencia de {@link #getPhase}: el Pod pasa a fase "Running" en cuanto TODOS sus
     * contenedores arrancaron, sin importar si ya pasaron su readiness probe -- con el Pod de
     * dos contenedores de Fase 4 (ide+runtime) esto deja una ventana real donde el frontend
     * (que antes solo miraba la fase) cargaba el IDE antes de que 'runtime' terminara de
     * levantar, resultando en 502/WebSocket rechazado. Devuelve el condition Ready agregado del
     * Pod (todos los contenedores Ready) — false tambien si el Pod no existe.
     */
    boolean isReady(String podName);

    /**
     * Fase 4 (dashboard de moderador): arbol de archivos del workspace de UN asiento, via el
     * agente HTTP en el puerto de control del Pod (mismo mecanismo que {@link #provisionSeat},
     * ver sandbox-agent.py/sandbox-file-agent.py) -- {@code seatIndex} siempre 0 para Pods de un
     * solo asiento (imagen debian, o neovim sin compartir). {@code path} relativo, "" = raiz del
     * workspace.
     */
    List<WorkspaceFileEntry> listWorkspaceFiles(String podName, int seatIndex, String path);

    /** Contenido de un archivo de texto (rechaza binarios/demasiado grandes, ver sandbox_file_api.py). */
    WorkspaceFileContent readWorkspaceFile(String podName, int seatIndex, String path);

    /**
     * Escribe el contenido de un archivo. Si {@code expectedMtime} no es nulo y ya no coincide
     * con el mtime real (el alumno edito el archivo desde que el moderador lo leyo), lanza
     * {@code IllegalArgumentException("file_conflict")} en vez de pisarlo -- mismo idioma de
     * excepcion por-mensaje que ya usa el resto de este puerto (ver createSandbox/
     * kubernetes_not_configured). {@code expectedMtime} nulo = forzar (sin chequeo), usado por
     * el flujo de "Guardar de todas formas" del organizador.
     *
     * @return el mtime nuevo despues de escribir.
     */
    double writeWorkspaceFile(String podName, int seatIndex, String path, String content, Double expectedMtime);

    /** Zip completo del workspace del alumno (descarga desde el IDE, ver
     *  GenerateWorkspaceDownloadUrlUseCase/DownloadWorkspaceZipUseCase) -- mismo agente de
     *  control que listWorkspaceFiles/readWorkspaceFile, ruta {@code /workspace/{seatIndex}/zip}. */
    byte[] downloadWorkspaceZip(String podName, int seatIndex);

    /** Fase 3c: crea (si no existe) la NetworkPolicy que permite egress a internet para todos
     *  los sandboxes de un evento — idempotente. */
    void allowInternetEgress(String conferenceLabel);

    /** Fase 3c: borra esa NetworkPolicy (vuelve al default-deny egress del namespace) — no falla
     *  si ya no existe. */
    void denyInternetEgress(String conferenceLabel);

    /**
     * Control de egress por dominio (2026-07): el egress-proxy no conoce Kubernetes ni SQLite --
     * le pregunta a insightbloom-users "¿de qué evento es esta IP de origen?" para poder resolver
     * la política aplicable (ver ResolveEgressPolicyUseCase). Busca, en el namespace de
     * sandboxes, el Pod cuyo {@code status.podIP} coincide y lee su annotation
     * {@code insightbloom.io/conference-uuid} (el UUID completo -- la label
     * {@code sandbox-conference} está truncada a 8 caracteres y no sirve para esto).
     * @return vacío si ninguna Pod tiene esa IP ahora mismo (recién borrado, IP reciclada, etc).
     */
    Optional<String> findConferenceUuidByPodIp(String podIp);
}
