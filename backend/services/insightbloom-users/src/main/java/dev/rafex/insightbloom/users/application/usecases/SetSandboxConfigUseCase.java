package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.model.Conference;
import java.net.URI;
import dev.rafex.insightbloom.users.domain.ports.ConferenceRepository;

public class SetSandboxConfigUseCase {
    /**
     * Heap minimo aceptado (-Xmx en MB): pedido explicito del usuario ("no dejar JVMs libres,
     * deben ser mucho mas chicas, son para cursos"). Un heap absurdamente chico (ej. 16Mi) ni
     * arranca jdt.ls -- 64Mi es el piso realista para que la JVM levante sin explotar con
     * OutOfMemoryError en el arranque mismo.
     */
    private static final int MIN_JVM_HEAP_MB = 64;
    /**
     * Techo de asientos por Pod compartido "neovim" (varios alumnos, un usuario Linux cada uno
     * dentro del mismo contenedor). Pedido explicito del usuario, con el LimitRange actual del
     * namespace de sandboxes (2048Mi/1000m por Pod, ver DECISIONS.md) como referencia -- pasar de
     * 10 exigiria revisar ese LimitRange a mano, no es un simple ajuste de config de conferencia.
     */
    private static final int MIN_SEATS_PER_POD = 1;
    private static final int MAX_SEATS_PER_POD = 10;

    private final ConferenceRepository conferenceRepository;
    private final int maxPoolSizePerEvent;
    private final int maxJvmHeapMbDebian;
    private final int maxJvmHeapMbNeovim;

    public SetSandboxConfigUseCase(ConferenceRepository conferenceRepository, int maxPoolSizePerEvent,
                                    int maxJvmHeapMbDebian, int maxJvmHeapMbNeovim) {
        this.conferenceRepository = conferenceRepository;
        this.maxPoolSizePerEvent = maxPoolSizePerEvent;
        this.maxJvmHeapMbDebian = maxJvmHeapMbDebian;
        this.maxJvmHeapMbNeovim = maxJvmHeapMbNeovim;
    }

    public Conference execute(
        String conferenceUuid,
        String sandboxVariant,
        Integer sandboxPoolSize,
        String sandboxRemoteGitUrl,
        Integer sandboxJvmHeapMb,
        Integer sandboxSeatsPerPod,
        Integer sandboxCliPoolSize,
        Integer sandboxCliLazyVimPoolSize,
        String materialSourceUrl, String materialRef, String bootstrapKind,
        String bootstrapSource, String bootstrapValue
    ) {
        var conf = conferenceRepository.findByUuid(conferenceUuid)
            .orElseThrow(() -> new IllegalArgumentException("conference_not_found"));

        if (sandboxPoolSize != null && sandboxPoolSize <= 0) {
            throw new IllegalArgumentException("pool_size_must_be_positive");
        }
        if (sandboxPoolSize != null && sandboxPoolSize > maxPoolSizePerEvent) {
            throw new IllegalArgumentException("pool_size_exceeds_platform_max");
        }
        if (sandboxCliPoolSize != null && sandboxCliPoolSize <= 0) {
            throw new IllegalArgumentException("cli_pool_size_must_be_positive");
        }
        if (sandboxCliPoolSize != null && sandboxCliPoolSize > maxPoolSizePerEvent) {
            throw new IllegalArgumentException("cli_pool_size_exceeds_platform_max");
        }
        if (sandboxCliLazyVimPoolSize != null && sandboxCliLazyVimPoolSize <= 0) {
            throw new IllegalArgumentException("cli_lazyvim_pool_size_must_be_positive");
        }
        if (sandboxCliLazyVimPoolSize != null && sandboxCliLazyVimPoolSize > maxPoolSizePerEvent) {
            throw new IllegalArgumentException("cli_lazyvim_pool_size_exceeds_platform_max");
        }
        if (sandboxJvmHeapMb != null) {
            // Techo real = el limite de memoria del contenedor de sandbox (SANDBOX_DEBIAN_
            // MEMORY_LIMIT/SANDBOX_NEOVIM_MEMORY_LIMIT en el chart de gitops, ver
            // UsersApplication) menos margen para el resto del proceso (code-server/extension
            // host, o ttyd+nvim) -- nunca puede pisar ese numero, o el contenedor entero se cae
            // por OOM apenas la JVM toca su -Xmx (el heap no es toda la memoria de la JVM: hay
            // que dejarle espacio a metaspace/stacks/off-heap tambien). Pools Web/CLI
            // independientes (2026-07): ya no hay "la" variante de la conferencia para elegir un
            // techo -- ambas imagenes pueden terminar corriendo el mismo valor de heap, asi que
            // se usa el MENOR de los dos techos (nunca excede el limite de memoria de NINGUNO de
            // los dos contenedores posibles).
            final int maxHeapMb = Math.min(maxJvmHeapMbDebian, maxJvmHeapMbNeovim);
            if (sandboxJvmHeapMb < MIN_JVM_HEAP_MB) {
                throw new IllegalArgumentException("jvm_heap_too_small");
            }
            if (sandboxJvmHeapMb > maxHeapMb) {
                throw new IllegalArgumentException("jvm_heap_exceeds_container_limit");
            }
        }
        if (sandboxSeatsPerPod != null
                && (sandboxSeatsPerPod < MIN_SEATS_PER_POD || sandboxSeatsPerPod > MAX_SEATS_PER_POD)) {
            throw new IllegalArgumentException("seats_per_pod_out_of_range");
        }
        validateMaterialBootstrap(materialSourceUrl, materialRef, bootstrapKind, bootstrapSource, bootstrapValue);

        conf.setSandboxVariant(sandboxVariant);
        conf.setSandboxPoolSize(sandboxPoolSize);
        conf.setSandboxRemoteGitUrl(sandboxRemoteGitUrl);
        conf.setSandboxJvmHeapMb(sandboxJvmHeapMb);
        conf.setSandboxSeatsPerPod(sandboxSeatsPerPod);
        conf.setSandboxCliPoolSize(sandboxCliPoolSize);
        conf.setSandboxCliLazyVimPoolSize(sandboxCliLazyVimPoolSize);
        conf.setSandboxMaterialSourceUrl(blankToNull(materialSourceUrl));
        conf.setSandboxMaterialRef(blankToNull(materialRef));
        conf.setSandboxBootstrapKind(blankToNull(bootstrapKind));
        conf.setSandboxBootstrapSource(blankToNull(bootstrapSource));
        conf.setSandboxBootstrapValue(blankToNull(bootstrapValue));

        conferenceRepository.save(conf);
        return conf;
    }

    /** Compatibilidad con callers/tests del contrato anterior; LazyVim queda deshabilitado. */
    public Conference execute(
        String conferenceUuid, String sandboxVariant, Integer sandboxPoolSize, String sandboxRemoteGitUrl,
        Integer sandboxJvmHeapMb, Integer sandboxSeatsPerPod, Integer sandboxCliPoolSize,
        Integer sandboxCliLazyVimPoolSize
    ) {
        return execute(conferenceUuid, sandboxVariant, sandboxPoolSize, sandboxRemoteGitUrl,
                sandboxJvmHeapMb, sandboxSeatsPerPod, sandboxCliPoolSize, sandboxCliLazyVimPoolSize,
                null, null, null, null, null);
    }

    /** Compatibilidad con callers/tests del contrato anterior; LazyVim queda deshabilitado. */
    public Conference execute(
        String conferenceUuid,
        String sandboxVariant,
        Integer sandboxPoolSize,
        String sandboxRemoteGitUrl,
        Integer sandboxJvmHeapMb,
        Integer sandboxSeatsPerPod,
        Integer sandboxCliPoolSize
    ) {
        return execute(conferenceUuid, sandboxVariant, sandboxPoolSize, sandboxRemoteGitUrl,
                sandboxJvmHeapMb, sandboxSeatsPerPod, sandboxCliPoolSize, null,
                null, null, null, null, null);
    }

    private static String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateMaterialBootstrap(final String url, final String ref, final String kind,
                                                   final String source, final String value) {
        final boolean any = java.util.stream.Stream.of(url, ref, kind, source, value)
                .anyMatch(v -> v != null && !v.isBlank());
        if (!any) return;
        if (url == null || ref == null || kind == null || source == null || value == null) {
            throw new IllegalArgumentException("material_bootstrap_incomplete");
        }
        try {
            final URI uri = URI.create(url.trim());
            if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\\.git)?")) {
                throw new IllegalArgumentException("material_source_must_be_public_github");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("material_source_must_be_public_github");
        }
        if (!ref.trim().matches("[A-Za-z0-9._/-]{1,128}")) throw new IllegalArgumentException("material_ref_invalid");
        if (!"shell".equals(kind) && !"python".equals(kind)) throw new IllegalArgumentException("bootstrap_kind_invalid");
        if (!"inline".equals(source) && !"material".equals(source)) throw new IllegalArgumentException("bootstrap_source_invalid");
        if (value.length() > 65536 || ("material".equals(source) && (value.startsWith("/") || value.contains("..")))) {
            throw new IllegalArgumentException("bootstrap_value_invalid");
        }
    }
}
