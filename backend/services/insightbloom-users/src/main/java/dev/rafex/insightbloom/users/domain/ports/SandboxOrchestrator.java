package dev.rafex.insightbloom.users.domain.ports;

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
    void createSandbox(String podName, String conferenceUuid, String variant, String extraPackages,
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

    /** Fase 3c: crea (si no existe) la NetworkPolicy que permite egress a internet para todos
     *  los sandboxes de un evento — idempotente. */
    void allowInternetEgress(String conferenceLabel);

    /** Fase 3c: borra esa NetworkPolicy (vuelve al default-deny egress del namespace) — no falla
     *  si ya no existe. */
    void denyInternetEgress(String conferenceLabel);
}
