package dev.rafex.insightbloom.users.application.usecases;

/**
 * Fallo recuperable durante una operación administrativa sobre un sandbox.
 *
 * <p>El detalle original se conserva exclusivamente como causa para los logs; el código es
 * seguro para exponerlo al cliente y permite distinguir infraestructura de persistencia.</p>
 */
public final class SandboxResetException extends RuntimeException {
    public enum Kind {
        ORCHESTRATION,
        PERSISTENCE
    }

    private final Kind kind;

    public SandboxResetException(final Kind kind, final Throwable cause) {
        super(kind == Kind.ORCHESTRATION ? "sandbox_orchestration_failed" : "sandbox_persistence_failed", cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
