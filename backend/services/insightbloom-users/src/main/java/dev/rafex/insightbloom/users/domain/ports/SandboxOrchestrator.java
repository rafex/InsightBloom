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
    void createSandbox(String podName, String conferenceUuid, String variant, String extraPackages,
                        String remoteGitUrl, boolean internetEnabled);

    /** Borra Pod + Service; no falla si ya no existen (ej. purga repetida). */
    void deleteSandbox(String podName);

    /** "Pending", "Running", "Failed", "Succeeded", "Unknown", o null si el Pod no existe. */
    String getPhase(String podName);

    /** Fase 3c: crea (si no existe) la NetworkPolicy que permite egress a internet para todos
     *  los sandboxes de un evento — idempotente. */
    void allowInternetEgress(String conferenceLabel);

    /** Fase 3c: borra esa NetworkPolicy (vuelve al default-deny egress del namespace) — no falla
     *  si ya no existe. */
    void denyInternetEgress(String conferenceLabel);
}
