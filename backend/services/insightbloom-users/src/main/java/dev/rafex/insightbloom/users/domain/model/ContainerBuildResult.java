package dev.rafex.insightbloom.users.domain.model;

/**
 * Resultado de {@code SandboxOrchestrator.buildAndRunContainer} -- {@code errorCode}/{@code
 * errorDetail} solo se llenan cuando {@code success} es falso (fallo real de {@code podman build}
 * o {@code podman run} dentro del pod, ya pasada la validación de {@code ContainerfileValidator}).
 */
public record ContainerBuildResult(boolean success, String errorCode, String errorDetail) {
    public static ContainerBuildResult ok() {
        return new ContainerBuildResult(true, null, null);
    }

    public static ContainerBuildResult failure(final String errorCode, final String errorDetail) {
        return new ContainerBuildResult(false, errorCode, errorDetail);
    }
}
