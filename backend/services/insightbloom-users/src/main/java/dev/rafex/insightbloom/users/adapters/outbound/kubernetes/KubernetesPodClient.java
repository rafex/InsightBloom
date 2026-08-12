package dev.rafex.insightbloom.users.adapters.outbound.kubernetes;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.users.domain.model.ContainerBuildResult;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileContent;
import dev.rafex.insightbloom.users.domain.model.WorkspaceFileEntry;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Provisiona sandboxes de code-server como Pods reales de Kubernetes, hablando directo al API
 * server del cluster (in-cluster: {@code https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT})
 * con el token del ServiceAccount montado por defecto. Sin dependencia de fabric8/kubernetes-client
 * a proposito — mismo patron hand-rolled con {@link HttpClient} que ya usa {@code GroqLlmClient}
 * en insightbloom-survey; solo se necesitan 5 verbos REST (crear/borrar/leer Pod, crear/borrar
 * Service), no vale la pena la dependencia pesada de un SDK generado.
 *
 * Cambio de paradigma 2026-07-17: el Pod tiene un UNICO contenedor, autocontenido (Java+Node+
 * Python+editor todo en la misma imagen) — reemplaza el split de Fase 4 ({@code ide} + {@code
 * runtime} bridge via socat en loopback) que existio entre 2026-07-12 y 07-17. Ese split
 * asumia que code-server no necesitaba el toolchain instalado, pero la extension redhat.java
 * igual requeria un JDK LOCAL en el contenedor {@code ide} para compilar/analizar (el bridge
 * via JDWP/debugpy solo sirve para *adjuntarse* a un proceso ya corriendo, no para el language
 * server) — separar solo agregaba superficie (una NetworkPolicy de loopback, un socat, JDWP/
 * debugpy potencialmente expuestos entre Pods) sin el beneficio que se buscaba. Ver DECISIONS.md
 * DEC-0023 para el historial completo.
 *
 * El campo {@code variant} (heredado de las 3 imagenes por-variante java/python/web que ya no
 * existen) se reutiliza como "modo de IDE": {@value #IDE_MODE_TERMINAL_NVIM} selecciona la
 * imagen Alpine (vim/neovim/lazygit, servida por {@code ttyd}); cualquier otro valor (incluidos
 * los historicos {@code python}/{@code java}/{@code web}, o {@code null}) selecciona la imagen
 * Debian (code-server).
 *
 * El spec de Pod replica el hardening documentado (pero nunca renderizado) en
 * {@code infra/helm/charts/insightbloom/templates/sandbox-pool.yaml}: non-root, sin capabilities,
 * seccomp RuntimeDefault, sin montar el token del ServiceAccount dentro del propio sandbox.
 *
 * Fase 3 (ver plan de implementacion): RBAC (Role+RoleBinding en el namespace
 * insightbloom-sandboxes) es lo que autoriza a insightbloom-users a crear/borrar estos recursos —
 * sin esa RoleBinding el API server responde 403 y {@link #createSandbox} lo propaga como excepcion.
 */
public class KubernetesPodClient implements SandboxOrchestrator {
    private static final Logger LOGGER = Logger.getLogger(KubernetesPodClient.class.getName());
    private static final Path TOKEN_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Path CA_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
    /** Ver javadoc de la clase: valor de "variant" que activa el modo de IDE alternativo (ttyd+nvim). */
    public static final String IDE_MODE_TERMINAL_NVIM = "terminal-nvim";
    public static final String IDE_MODE_TERMINAL_NVIM_LAZYVIM = "terminal-nvim-lazyvim";
    /**
     * Heap por defecto (-Xmx, en MB) cuando la conferencia no configuro uno propio desde el
     * Dashboard -- pedido explicito del usuario: JVMs chicas, pensadas para cursos, no "libres".
     * 70Mi es el valor conservador por defecto; el organizador puede aumentarlo desde la
     * configuración del evento si el lenguaje o el ejercicio lo necesitan. Sigue siendo mucho menor que el
     * limite de memoria del contenedor (ver ContainerResources en UsersApplication), dejando
     * margen real para code-server/extension-host o ttyd+nvim.
     */
    private static final int DEFAULT_JVM_HEAP_MB = 70;
    /**
     * Default de asientos por Pod compartido (modo terminal-nvim) cuando la conferencia no
     * configuro uno propio -- mismo default que AssignSandboxUseCase.DEFAULT_SEATS_PER_POD,
     * duplicado a proposito (evita un acoplamiento entre modulos por una constante; si difieren
     * algun dia, el cap real de cuantos alumnos aceptar sigue siendo AssignSandboxUseCase, esto
     * solo decide cuantos puertos declarar en el Pod la primera vez que se crea).
     */
    private static final int DEFAULT_SEATS_PER_POD = 4;
    /**
     * Techo de asientos por Pod compartido -- mismo valor que
     * SetSandboxConfigUseCase.MAX_SEATS_PER_POD (validacion de organizador). Se usa aca para
     * fijar el RANGO de puertos que la NetworkPolicy de Ingress declara (ver
     * {@link #ensureIngressPolicy()}): esa policy es UNA sola para todo el namespace
     * (podSelector vacio, aplica a todos los Pods de sandbox) y {@link #postIgnoringConflict}
     * nunca actualiza un recurso que ya existe (solo crea si falta) -- si el rango de puertos
     * dependiera del seatsPerPod de CADA Pod individual, el primer Pod compartido que se cree
     * fijaria el rango para siempre y los siguientes con mas asientos quedarian bloqueados. Mas
     * simple y seguro declarar siempre el rango maximo posible de una vez.
     */
    private static final int MAX_SEATS_PER_POD = 10;

    /**
     * Fase 4b (MVP): cuántas publicaciones de contenedor concurrentes admite el pod Podman
     * COMPARTIDO (ver {@link #ensureRuntimePodmanPod}) -- mismo valor que {@link
     * #MAX_SEATS_PER_POD} a propósito (mismo criterio de "declarar el rango máximo posible de
     * puertos una sola vez", ver comentario de esa constante). Escalar más allá de esto es una
     * fase futura (pool de varios pods), no un ajuste de esta constante.
     */
    private static final int MAX_PODMAN_PUBLICATIONS = 10;
    /** Ver javadoc de la clase / SandboxOrchestrator.ensureRuntimePodmanPod. */
    public static final String RUNTIME_VARIANT_PODMAN = "runtime-podman";

    /**
     * Límites de recursos del (unico) contenedor del Pod de sandbox. Un set por imagen (ver
     * {@link #debianResources}/{@link #neovimResources}), no uno solo compartido: las dos
     * imagenes tienen perfiles de consumo muy distintos -- la imagen Debian corre el extension
     * host de VS Code Web (proceso Node) + code-server + jdt.ls (una JVM completa) cuando se
     * abre un .java, mientras que la imagen Alpine solo corre {@code ttyd}+{@code nvim} (un
     * proceso liviano, sin el overhead del extension host ni del servidor de code-server).
     */
    public record ContainerResources(String cpuRequest, String memoryRequest, String cpuLimit, String memoryLimit) {
    }

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;
    private final JsonCodec jsonCodec;
    private final String namespace;
    private final String debianImage;
    private final String neovimImage;
    private final String neovimLazyVimImage;
    private final String imagePullPolicy;
    private final String priorityClassName;
    private final ContainerResources debianResources;
    private final ContainerResources neovimResources;
    private final int port;
    private final int uid;
    private final int gid;
    private final int fsGroup;
    private final String gatewayNamespace;
    private final String gatewayPodComponentLabel;
    private final String usersPodComponentLabel;
    private final String incidentReportKey;
    private final String egressProxyHost;
    private final int egressProxyPort;
    private final int appBasePort;
    private final String podmanImage;
    private final ContainerResources podmanResources;
    private final int podmanAppBasePort;
    private final String podmanStorageSizeLimit;

    public KubernetesPodClient(final JsonCodec jsonCodec, final String namespace,
                                final String debianImage, final String neovimImage, final String neovimLazyVimImage,
                                final String imagePullPolicy, final String priorityClassName,
                                final ContainerResources debianResources, final ContainerResources neovimResources,
                                final int port, final int uid, final int gid, final int fsGroup,
                                final String gatewayNamespace, final String gatewayPodComponentLabel,
                                final String usersPodComponentLabel, final String incidentReportKey,
                                final String egressProxyHost, final int egressProxyPort, final int appBasePort,
                                final String podmanImage, final ContainerResources podmanResources,
                                final int podmanAppBasePort, final String podmanStorageSizeLimit) {
        this.jsonCodec = jsonCodec;
        this.namespace = namespace;
        this.debianImage = debianImage;
        this.neovimImage = neovimImage;
        this.neovimLazyVimImage = neovimLazyVimImage;
        this.imagePullPolicy = imagePullPolicy;
        this.priorityClassName = priorityClassName;
        this.debianResources = debianResources;
        this.neovimResources = neovimResources;
        this.port = port;
        this.gatewayNamespace = gatewayNamespace;
        this.gatewayPodComponentLabel = gatewayPodComponentLabel;
        this.usersPodComponentLabel = usersPodComponentLabel;
        this.incidentReportKey = incidentReportKey;
        this.egressProxyHost = egressProxyHost;
        this.egressProxyPort = egressProxyPort;
        this.appBasePort = appBasePort;
        this.podmanImage = podmanImage;
        this.podmanResources = podmanResources;
        this.podmanAppBasePort = podmanAppBasePort;
        this.podmanStorageSizeLimit = podmanStorageSizeLimit;
        this.uid = uid;
        this.gid = gid;
        this.fsGroup = fsGroup;
        this.token = readTokenOrNull();
        this.httpClient = buildHttpClient();
        final String host = System.getenv("KUBERNETES_SERVICE_HOST");
        final String svcPort = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT", "443");
        this.apiBaseUrl = host != null ? "https://" + host + ":" + svcPort : null;
    }

    /** false fuera de un cluster (dev local, unit tests) — los use cases deben degradar con claridad. */
    public boolean isEnabled() {
        return token != null && apiBaseUrl != null;
    }

    @Override
    public void createSandbox(final String podName, final String conferenceUuid, final String variant,
                               final String remoteGitUrl, final boolean internetEnabled,
                               final Integer jvmHeapMb, final Integer seatsPerPod) {
        requireEnabled();
        final boolean terminalMode = isTerminalVariant(variant);
        // seatsPerPod solo importa en modo terminal-nvim -- en cualquier otro modo (o
        // seatsPerPod nulo/<=1) sigue siendo exactamente 1 puerto, el comportamiento de siempre.
        final int effectiveSeats = terminalMode
                ? Math.max(1, seatsPerPod != null ? seatsPerPod : DEFAULT_SEATS_PER_POD)
                : 1;
        ensureIngressPolicy();
        if (terminalMode && effectiveSeats > 1) {
            ensureIncidentReportEgressPolicy();
        }
        final String podJson = jsonCodec.toJson(
                buildPodBody(podName, conferenceUuid, variant, remoteGitUrl,
                        jvmHeapMb, effectiveSeats));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/pods", podJson, "pod " + podName);
        final String serviceJson = jsonCodec.toJson(buildServiceBody(podName, effectiveSeats));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/services", serviceJson, "service " + serviceName(podName));
        // La política es parte del estado del evento, no del estado del Pod. Siempre
        // reconciliamos ambos caminos para que un Pod reutilizado no conserve una
        // NetworkPolicy permisiva de una configuración anterior.
        final String conferenceLabel = Sandbox.conferenceLabel(conferenceUuid);
        if (internetEnabled) {
            allowInternetEgress(conferenceLabel);
        } else {
            denyInternetEgress(conferenceLabel);
        }
    }

    private static final String INGRESS_POLICY_NAME = "sandbox-ingress-gateway-only";
    private static final String INCIDENT_EGRESS_POLICY_NAME = "sandbox-egress-incident-report";
    /** Puerto interno de insightbloom-users, mismo que ya usa el gateway (ver
     *  GATEWAY_SANDBOX_RESOLVE_URL en GatewayApplication.java). */
    private static final int USERS_INTERNAL_PORT = 8081;
    /** Puerto interno de insightbloom-survey para el tutor IA del CLI. */
    private static final int SURVEY_INTERNAL_PORT = 8086;

    /**
     * Fase C (DEC-0025): el watchdog del seat-agent necesita reportar incidentes a
     * insightbloom-users (POST /internal/sandbox-incidents) SIN depender de que la conferencia
     * tenga internet habilitado (el caso mas comun es internetEnabled=false) -- esta policy es
     * independiente de {@link #allowInternetEgress}, restringida a un solo destino (namespace +
     * label + puerto de insightbloom-users), no "internet abierto". Solo se llama para Pods
     * multi-asiento (el unico caso con seat-agent/watchdog); idempotente igual que
     * {@link #ensureIngressPolicy}.
     */
    private void ensureIncidentReportEgressPolicy() {
        final Map<String, Object> policy = Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "NetworkPolicy",
                "metadata", Map.of("name", INCIDENT_EGRESS_POLICY_NAME, "namespace", namespace),
                "spec", Map.of(
                        "podSelector", Map.of(),
                        "policyTypes", List.of("Egress"),
                        "egress", List.of(Map.of(
                                "to", List.of(Map.of(
                                        "namespaceSelector", Map.of(
                                                "matchLabels", Map.of("kubernetes.io/metadata.name", gatewayNamespace)),
                                        "podSelector", Map.of(
                                                "matchLabels", Map.of("app.kubernetes.io/component", usersPodComponentLabel)))),
                                "ports", List.of(Map.of("protocol", "TCP", "port", USERS_INTERNAL_PORT))))));
        upsertNetworkPolicy("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                jsonCodec.toJson(policy), "networkpolicy " + INCIDENT_EGRESS_POLICY_NAME, INCIDENT_EGRESS_POLICY_NAME);
    }

    /**
     * Postmortem 2026-07-17 (auditoria de seguridad): confirmado en vivo que cualquier Pod de
     * {@code insightbloom-sandboxes} podia alcanzar el Service de CUALQUIER OTRO Pod del mismo
     * namespace sin autenticacion (curl directo, 200 OK) -- la NetworkPolicy de egress existente
     * permite trafico saliente entre pods del mismo namespace, y no habia ninguna NetworkPolicy
     * de tipo Ingress que restringiera el trafico ENTRANTE. Como {@code code-server} corre con
     * {@code --auth none} y {@code ttyd} sin credencial (la autenticacion real vive solo en
     * {@code insightbloom-tools-gateway}, en el borde), esto permitia a cualquier alumno acceder
     * al IDE completo de otro alumno con solo saber/adivinar el nombre del Service.
     *
     * Esta policy restringe el trafico ENTRANTE de todos los Pods de sandbox a unicamente el
     * originado por el Pod del gateway (namespace + label), sobre los puertos publicos del
     * sandbox ({@link #port}{@code ..}{@link #port}{@code +MAX_SEATS_PER_POD-1}, para cubrir
     * Pods compartidos multi-asiento -- ver comentario de {@link #MAX_SEATS_PER_POD}) --
     * bloquea el acceso Pod-a-Pod entre sandboxes sin afectar el flujo legitimo (gateway ->
     * Service del sandbox). Idempotente (se llama en cada {@link #createSandbox}, sin costo si
     * ya existe) y no depende de {@code internetEnabled}: aplica siempre.
     *
     * Fase B (2026-07): segunda regla de Ingress, distinta de la del gateway -- el puerto de
     * CONTROL del seat-agent ({@link #controlPort()}) solo debe ser alcanzable por
     * {@code insightbloom-users} (namespace+label propios, no los del gateway). El gateway nunca
     * llama al seat-agent directamente; es insightbloom-users quien lo hace al asignar un
     * asiento (ver KubernetesPodClient.provisionSeat).
     */
    private void ensureIngressPolicy() {
        final List<Map<String, Object>> seatPorts = new ArrayList<>();
        for (int i = 0; i < MAX_SEATS_PER_POD; i++) {
            seatPorts.add(Map.of("protocol", "TCP", "port", port + i));
        }
        // Publicacion de backends/API REST vivos del alumno (2026-07): banda de puertos paralela
        // a la de ttyd/code-server, mismo criterio (rango fijo MAX_SEATS_PER_POD, nunca el
        // seatsPerPod real de CADA Pod -- ver comentario de MAX_SEATS_PER_POD). Solo el gateway
        // puede alcanzarla (mismo "from" que seatPorts, es el mismo servicio quien resuelve tanto
        // IDE Web/CLI como app-preview, ver AppPreviewGateHandler).
        for (int i = 0; i < MAX_SEATS_PER_POD; i++) {
            seatPorts.add(Map.of("protocol", "TCP", "port", appBasePort + i));
        }
        // Publicaciones de contenedores (Fase 4b, MVP): mismo criterio que el rango de
        // "seat-N"/"app-N" de arriba, ahora para el pod Podman compartido (ver
        // ensureRuntimePodmanPod). Se declara siempre, aunque el pod Podman todavía no exista --
        // esta policy es una sola para todo el namespace, idempotente, sin costo de declarar un
        // rango que nadie usa todavía.
        for (int i = 0; i < MAX_PODMAN_PUBLICATIONS; i++) {
            seatPorts.add(Map.of("protocol", "TCP", "port", podmanAppBasePort + i));
        }
        final Map<String, Object> policy = Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "NetworkPolicy",
                "metadata", Map.of("name", INGRESS_POLICY_NAME, "namespace", namespace),
                "spec", Map.of(
                        "podSelector", Map.of(),
                        "policyTypes", List.of("Ingress"),
                        "ingress", List.of(
                                Map.of(
                                        "from", List.of(Map.of(
                                                "namespaceSelector", Map.of(
                                                        "matchLabels", Map.of("kubernetes.io/metadata.name", gatewayNamespace)),
                                                "podSelector", Map.of(
                                                        "matchLabels", Map.of("app.kubernetes.io/component", gatewayPodComponentLabel)))),
                                        "ports", seatPorts),
                                Map.of(
                                        "from", List.of(Map.of(
                                                "namespaceSelector", Map.of(
                                                        "matchLabels", Map.of("kubernetes.io/metadata.name", gatewayNamespace)),
                                                "podSelector", Map.of(
                                                        "matchLabels", Map.of("app.kubernetes.io/component", usersPodComponentLabel)))),
                                        // controlPort(): seat-agent/sandbox-file-agent (Pods de siempre). podmanControlPort():
                                        // agente del pod Podman compartido (Fase 4b, ver ensureRuntimePodmanPod) -- mismo
                                        // criterio, solo insightbloom-users llama a /build directamente, nunca el gateway.
                                        "ports", List.of(
                                                Map.of("protocol", "TCP", "port", controlPort()),
                                                Map.of("protocol", "TCP", "port", podmanControlPort()))))));
        upsertNetworkPolicy("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                jsonCodec.toJson(policy), "networkpolicy " + INGRESS_POLICY_NAME, INGRESS_POLICY_NAME);
    }

    @Override
    public void allowInternetEgress(final String conferenceLabel) {
        requireEnabled();
        final String policyJson = jsonCodec.toJson(buildEgressAllowBody(conferenceLabel));
        upsertNetworkPolicy("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                policyJson, "networkpolicy " + egressPolicyName(conferenceLabel), egressPolicyName(conferenceLabel));
    }

    /** Reintentos de {@link #provisionSeat}: el Pod puede tardar unos segundos en agendarse y
     *  arrancar el seat-agent justo despues de {@link #createSandbox} -- sin esto, el primer
     *  alumno de un Pod nuevo fallaria casi siempre por pura carrera de arranque. */
    private static final int PROVISION_SEAT_MAX_ATTEMPTS = 6;
    private static final long PROVISION_SEAT_RETRY_DELAY_MILLIS = 2000;

    @Override
    public void provisionSeat(final String podName, final int seatIndex, final String userUuid) {
        requireEnabled();
        final String url = "http://" + podName + "-svc." + namespace + ".svc.cluster.local:"
                + controlPort() + "/seats/" + seatIndex;
        final String body = jsonCodec.toJson(Map.of("userUuid", userUuid));
        IllegalStateException lastFailure = null;
        for (int attempt = 1; attempt <= PROVISION_SEAT_MAX_ATTEMPTS; attempt++) {
            try {
                final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                final HttpResponse<String> response = send(request);
                if (response.statusCode() < 300) {
                    return;
                }
                lastFailure = new IllegalStateException("seat_provisioning_failed: " + podName + "/" + seatIndex
                        + " -> " + response.statusCode() + " " + response.body());
            } catch (final IllegalStateException e) {
                // send() envuelve IOException aca -- esperado mientras el Pod/agente todavia no
                // esta listo, no un error real hasta agotar los reintentos.
                lastFailure = e;
            }
            if (attempt < PROVISION_SEAT_MAX_ATTEMPTS) {
                try {
                    Thread.sleep(PROVISION_SEAT_RETRY_DELAY_MILLIS);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw lastFailure;
                }
            }
        }
        throw lastFailure;
    }

    @Override
    public boolean ensureSeatReady(final String podName, final int seatIndex, final String userUuid) {
        if (!isEnabled()) return false;
        final String url = "http://" + podName + "-svc." + namespace + ".svc.cluster.local:"
                + controlPort() + "/seats/" + seatIndex;
        final String body = jsonCodec.toJson(Map.of("userUuid", userUuid));
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            final int status = send(request).statusCode();
            // Los Pods CLI de un solo asiento levantan sandbox-file-agent en el puerto de
            // control, no sandbox-agent.py. El endpoint /seats no existe allí (404), pero el
            // workspace ya está disponible en /workspace/0/zip; tratarlo como listo evita que
            // la publicación estática falle con workspace_not_found.
            return status < 300 || status == 404;
        } catch (final IllegalStateException e) {
            // send() envuelve IOException aca -- esperado mientras el Pod/seat-agent todavia no
            // esta listo (cold start en curso), no un error real: el proximo poll reintenta.
            return false;
        }
    }

    private String podControlUrl(final String podName) {
        return "http://" + podName + "-svc." + namespace + ".svc.cluster.local:" + controlPort();
    }

    private static String urlEncode(final String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    @Override
    public List<WorkspaceFileEntry> listWorkspaceFiles(final String podName, final int seatIndex, final String path) {
        requireEnabled();
        final String url = podControlUrl(podName) + "/files/" + seatIndex + "?path=" + urlEncode(path);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("workspace_files_list_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        final var entriesNode = jsonCodec.at(node, "/entries");
        final List<WorkspaceFileEntry> entries = new ArrayList<>();
        if (entriesNode.isArray()) {
            for (final var e : entriesNode) {
                entries.add(new WorkspaceFileEntry(
                        e.path("path").asText(),
                        e.path("isDirectory").asBoolean(),
                        e.path("mtime").asDouble(),
                        e.path("sizeBytes").asLong()));
            }
        }
        return entries;
    }

    @Override
    public WorkspaceFileContent readWorkspaceFile(final String podName, final int seatIndex, final String path) {
        requireEnabled();
        final String url = podControlUrl(podName) + "/file/" + seatIndex + "?path=" + urlEncode(path);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("file_not_found");
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("workspace_file_read_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        return new WorkspaceFileContent(jsonCodec.at(node, "/content").asText(), jsonCodec.at(node, "/mtime").asDouble());
    }

    @Override
    public double writeWorkspaceFile(final String podName, final int seatIndex, final String path,
                                      final String content, final Double expectedMtime) {
        requireEnabled();
        final StringBuilder url = new StringBuilder(podControlUrl(podName))
                .append("/file/").append(seatIndex).append("?path=").append(urlEncode(path));
        if (expectedMtime != null) {
            url.append("&mtime=").append(expectedMtime);
        }
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/plain; charset=utf-8")
                .PUT(HttpRequest.BodyPublishers.ofString(content, StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 409) {
            throw new IllegalArgumentException("file_conflict");
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("workspace_file_write_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        return jsonCodec.at(node, "/mtime").asDouble();
    }

    @Override
    public byte[] downloadWorkspaceZip(final String podName, final int seatIndex) {
        requireEnabled();
        final String url = podControlUrl(podName) + "/workspace/" + seatIndex + "/zip";
        // Alineado con el limite real del subprocess dentro del pod (sandbox_file_api.py:
        // build_workspace_zip_as_user, timeout=90) -- con 30s este cliente cortaba antes de que el
        // agente terminara de comprimir un workspace grande-pero-bajo-el-limite-de-50MB con muchos
        // archivos chicos (compresion CPU-bound, single-threaded), y el usuario veia un 500 generico
        // (bug reportado 2026-08-12). Ahora se llama siempre desde StartWorkspaceZipJobUseCase, en un
        // hilo de background sin el limite del request HTTP original -- 90s ya no bloquea al usuario.
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(90)).GET().build();
        final HttpResponse<byte[]> response = sendBytes(request);
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("workspace_not_found");
        }
        if (response.statusCode() == 413) {
            throw new IllegalArgumentException("workspace_too_large");
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("workspace_zip_failed: " + response.statusCode());
        }
        return response.body();
    }

    @Override
    public void denyInternetEgress(final String conferenceLabel) {
        requireEnabled();
        deleteIgnoring404("/apis/networking.k8s.io/v1/namespaces/" + namespace
                + "/networkpolicies/" + egressPolicyName(conferenceLabel));
    }

    private static String egressPolicyName(final String conferenceLabel) {
        return "sandbox-egress-" + conferenceLabel;
    }

    @Override
    public java.util.Optional<String> findConferenceUuidByPodIp(final String podIp) {
        if (!isEnabled() || podIp == null || podIp.isBlank()) return java.util.Optional.empty();
        final String url = "/api/v1/namespaces/" + namespace + "/pods?fieldSelector="
                + urlEncode("status.podIP=" + podIp);
        final HttpRequest request = authedRequest(url).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() >= 300) return java.util.Optional.empty();
        final var node = jsonCodec.readTree(response.body());
        final var items = node.path("items");
        if (!items.isArray() || items.isEmpty()) return java.util.Optional.empty();
        final var uuid = items.get(0).path("metadata").path("annotations").path("insightbloom.io/conference-uuid");
        return uuid.isMissingNode() || uuid.isNull() || uuid.asText().isBlank()
                ? java.util.Optional.empty() : java.util.Optional.of(uuid.asText());
    }

    private static final String PODMAN_EGRESS_POLICY_NAME = "sandbox-runtime-podman-egress";
    private static final String PODMAN_COMPONENT_LABEL = "sandbox-runtime-podman";

    /**
     * Fase 4b (MVP): a diferencia de los sandboxes por-evento (egress deny-all salvo el proxy
     * interno con allowlist de dominios, ver {@link #allowInternetEgress}), el pod Podman
     * compartido necesita salida DIRECTA a internet -- {@code podman build} corre pasos
     * arbitrarios del Containerfile ya validado (instalar paquetes via apt/pip/npm, etc.), no solo
     * descargar la imagen base. Simplificación deliberada del MVP: sin el allowlist de dominios
     * del egress-proxy para este pod en particular -- si se necesita restringir más adelante, se
     * puede sumar el mismo egress-proxy que ya usan los sandboxes de evento.
     */
    private void ensurePodmanEgressPolicy() {
        final Map<String, Object> policy = Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "NetworkPolicy",
                "metadata", Map.of("name", PODMAN_EGRESS_POLICY_NAME, "namespace", namespace),
                "spec", Map.of(
                        "podSelector", Map.of("matchLabels", Map.of("app.kubernetes.io/component", PODMAN_COMPONENT_LABEL)),
                        "policyTypes", List.of("Egress"),
                        // Regla vacía ({} sin "to"/"ports") == permitir TODO el egress para los
                        // pods seleccionados -- semántica estándar de NetworkPolicy.
                        "egress", List.of(Map.of())));
        upsertNetworkPolicy("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                jsonCodec.toJson(policy), "networkpolicy " + PODMAN_EGRESS_POLICY_NAME, PODMAN_EGRESS_POLICY_NAME);
    }

    /** Puerto de control del agente del pod Podman compartido -- fuera del rango de publicación
     *  ({@code podmanAppBasePort..podmanAppBasePort+MAX_PODMAN_PUBLICATIONS-1}), mismo criterio
     *  que {@link #controlPort()}. */
    private int podmanControlPort() {
        return podmanAppBasePort - 1;
    }

    private String podmanControlUrl(final String podName) {
        return "http://" + podName + "-svc." + namespace + ".svc.cluster.local:" + podmanControlPort();
    }

    @Override
    public void ensureRuntimePodmanPod(final String podName) {
        requireEnabled();
        ensureIngressPolicy();
        ensurePodmanEgressPolicy();
        final String podJson = jsonCodec.toJson(buildPodmanPodBody(podName));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/pods", podJson, "pod " + podName);
        final String serviceJson = jsonCodec.toJson(buildPodmanServiceBody(podName));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/services", serviceJson, "service " + serviceName(podName));
    }

    @Override
    public ContainerBuildResult buildAndRunContainer(final String podName, final String containerfileContent,
                                                       final int hostPort, final int containerPort) {
        requireEnabled();
        final String url = podmanControlUrl(podName) + "/build";
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("containerfile", containerfileContent);
        body.put("hostPort", hostPort);
        body.put("containerPort", containerPort);
        // podman build corre pasos arbitrarios del Containerfile (apt/pip/npm install, etc.) --
        // sincrónico a propósito para el MVP (mismo enfoque simple que el resto de Fase 4b), pero
        // con timeout generoso; si en la práctica los builds tardan más que esto, conviene el
        // mismo patrón async ya usado para el zip de workspace (StartWorkspaceZipJobUseCase).
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(170))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonCodec.toJson(body), StandardCharsets.UTF_8))
                .build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() >= 300) {
            final var node = jsonCodec.readTree(response.body());
            return ContainerBuildResult.failure(
                    node.path("error").asText("container_build_failed"),
                    node.path("detail").asText(response.body()));
        }
        return ContainerBuildResult.ok();
    }

    /**
     * Pod spec dedicado, deliberadamente SEPARADO de {@link #buildPodBody} (que ya maneja tres
     * variantes con lógica de asientos/terminal entrelazada) -- mezclar un cuarto modo ahí
     * arriesgaba romper sutilmente los sandboxes de siempre. Ver "Cambio de mecanismo de
     * aislamiento" en el plan de Fase 4b para el detalle completo de este diseño.
     */
    private Map<String, Object> buildPodmanPodBody(final String podName) {
        final Map<String, Object> labels = Map.of(
                "app.kubernetes.io/part-of", "insightbloom",
                "app.kubernetes.io/component", PODMAN_COMPONENT_LABEL,
                "sandbox-pod", podName);

        final List<Map<String, Object>> ports = new ArrayList<>();
        for (int i = 0; i < MAX_PODMAN_PUBLICATIONS; i++) {
            ports.add(Map.of("name", "pub-" + i, "containerPort", podmanAppBasePort + i, "protocol", "TCP"));
        }

        final Map<String, Object> container = new LinkedHashMap<>();
        container.put("name", "podman-runtime");
        container.put("image", podmanImage);
        container.put("imagePullPolicy", imagePullPolicy);
        container.put("ports", ports);
        container.put("env", List.of(Map.of("name", "PODMAN_APP_BASE_PORT", "value", String.valueOf(podmanAppBasePort))));
        container.put("securityContext", podmanContainerSecurityContext());
        container.put("resources", resourcesBody(podmanResources));
        container.put("volumeMounts", List.of(Map.of("name", "containers-storage", "mountPath", "/var/lib/containers")));
        container.put("args", List.of(
                "python3", "/usr/local/bin/podman-agent.py",
                "--control-port", String.valueOf(podmanControlPort()),
                "--app-base-port", String.valueOf(podmanAppBasePort),
                "--max-publications", String.valueOf(MAX_PODMAN_PUBLICATIONS)));
        container.put("readinessProbe", tcpProbe(podmanControlPort(), 5, 10, 3));
        container.put("livenessProbe", tcpProbe(podmanControlPort(), 10, 30, 3));
        // startupProbe con más margen que el resto de las variantes: la imagen Podman es pesada
        // (toolchain completo) y el primer arranque puede tardar más que un sandbox de código.
        container.put("startupProbe", tcpProbe(podmanControlPort(), 5, 10, 60));

        final Map<String, Object> podSecurityContext = new LinkedHashMap<>();
        // Root nominal (uid 0) DENTRO del pod -- seguro porque hostUsers:false (ver spec.hostUsers
        // más abajo) ya remapea TODO el rango de UID del pod, incluido el 0, a un rango sin
        // privilegios reales en el nodo. Sin ese remapeo esto sería inaceptable.
        podSecurityContext.put("runAsNonRoot", false);
        podSecurityContext.put("runAsUser", 0);
        podSecurityContext.put("runAsGroup", 0);
        podSecurityContext.put("fsGroup", 0);
        podSecurityContext.put("seccompProfile", Map.of("type", "RuntimeDefault"));

        final Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("serviceAccountName", "default");
        spec.put("automountServiceAccountToken", false);
        spec.put("restartPolicy", "Always");
        // Kubernetes 1.36 (GA): user namespaces nativos -- root dentro de este pod queda mapeado a
        // un UID sin privilegios en el host, sin necesidad de un RuntimeClass especial (sysbox-runc
        // fue evaluado y descartado, ver plan de Fase 4b). Es lo que hace seguro correr Podman
        // "rootful-dentro-del-pod" arriba.
        spec.put("hostUsers", false);
        if (priorityClassName != null && !priorityClassName.isBlank()) {
            spec.put("priorityClassName", priorityClassName);
        }
        spec.put("securityContext", podSecurityContext);
        spec.put("containers", List.of(container));
        spec.put("volumes", List.of(
                Map.of("name", "containers-storage", "emptyDir", Map.of("sizeLimit", podmanStorageSizeLimit))));

        return Map.of(
                "apiVersion", "v1",
                "kind", "Pod",
                "metadata", Map.of("name", podName, "namespace", namespace, "labels", labels),
                "spec", spec);
    }

    /**
     * Capabilities mínimas para que Podman rootless funcione DENTRO del pod (un segundo nivel de
     * aislamiento, propio de Podman, independiente del {@code hostUsers:false} del pod) --
     * análogo al caso {@code multiSeat} de {@link #containerSecurityContext}, pero con una
     * diferencia deliberada: {@code allowPrivilegeEscalation: true}. Podman rootless usa
     * {@code newuidmap}/{@code newgidmap} (binarios setuid-root, ver
     * infra/docker/Dockerfile.runtime-podman) para crear el mapeo de sub-UID de SUS PROPIOS
     * contenedores anidados -- con {@code allowPrivilegeEscalation: false} el kernel fija
     * {@code no_new_privs}, que bloquea CUALQUIER binario setuid (exactamente el mismo motivo por
     * el que el resto del código de esta clase evita sudo, ver DEC-0025). Aceptable acá porque
     * {@code hostUsers:false} ya garantiza que "root" en este pod no es root real en el nodo --
     * sin ese remapeo, este ajuste sería inaceptable.
     */
    private Map<String, Object> podmanContainerSecurityContext() {
        final Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("allowPrivilegeEscalation", true);
        ctx.put("readOnlyRootFilesystem", false);
        ctx.put("runAsNonRoot", false);
        ctx.put("runAsUser", 0);
        ctx.put("capabilities", Map.of("drop", List.of("ALL"),
                "add", List.of("SETUID", "SETGID", "CHOWN", "FOWNER", "SYS_CHROOT")));
        return ctx;
    }

    private Map<String, Object> buildPodmanServiceBody(final String podName) {
        final List<Map<String, Object>> servicePorts = new ArrayList<>();
        for (int i = 0; i < MAX_PODMAN_PUBLICATIONS; i++) {
            servicePorts.add(Map.of("name", "pub-" + i, "port", podmanAppBasePort + i,
                    "targetPort", podmanAppBasePort + i, "protocol", "TCP"));
        }
        servicePorts.add(Map.of("name", "control", "port", podmanControlPort(),
                "targetPort", podmanControlPort(), "protocol", "TCP"));
        return Map.of(
                "apiVersion", "v1",
                "kind", "Service",
                "metadata", Map.of("name", serviceName(podName), "namespace", namespace),
                "spec", Map.of(
                        "selector", Map.of("sandbox-pod", podName),
                        "ports", servicePorts));
    }

    private Map<String, Object> buildEgressAllowBody(final String conferenceLabel) {
        return Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "NetworkPolicy",
                "metadata", Map.of("name", egressPolicyName(conferenceLabel), "namespace", namespace),
                "spec", Map.of(
                        "podSelector", Map.of("matchLabels", Map.of("sandbox-conference", conferenceLabel)),
                        "policyTypes", List.of("Egress"),
                        // La salida nunca es directa. Solo se permite el Service de la proxy
                        // interna; la proxy aplica EGRESS_PROXY_ALLOWED_HOSTS y
                        // EGRESS_PROXY_BLOCKED_HOSTS antes de abrir el socket externo.
                        "egress", List.of(Map.of(
                                "to", List.of(Map.of(
                                        "namespaceSelector", Map.of("matchLabels", Map.of(
                                                "kubernetes.io/metadata.name", gatewayNamespace)),
                                        "podSelector", Map.of("matchLabels", Map.of(
                                                "app.kubernetes.io/component", "egress-proxy")))),
                                "ports", List.of(Map.of("protocol", "TCP", "port", egressProxyPort))))));
    }

    @Override
    public void deleteSandbox(final String podName) {
        requireEnabled();
        deleteIgnoring404("/api/v1/namespaces/" + namespace + "/pods/" + podName);
        deleteIgnoring404("/api/v1/namespaces/" + namespace + "/services/" + serviceName(podName));
    }

    @Override
    public String getPhase(final String podName) {
        requireEnabled();
        final HttpRequest request = authedRequest("/api/v1/namespaces/" + namespace + "/pods/" + podName).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) return null;
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("kubernetes_get_pod_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        final var phase = jsonCodec.at(node, "/status/phase");
        return phase.isMissingNode() || phase.isNull() ? "Unknown" : phase.asText();
    }

    @Override
    public boolean isImageCurrent(final String podName, final String variant) {
        requireEnabled();
        final HttpRequest request = authedRequest("/api/v1/namespaces/" + namespace + "/pods/" + podName)
                .GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) return false;
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("kubernetes_get_pod_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        final var containers = node.path("spec").path("containers");
        if (!containers.isArray() || containers.isEmpty()) return false;
        final String actualImage = containers.get(0).path("image").asText("");
        final String expectedImage = isLazyVimVariant(variant)
                ? neovimLazyVimImage : (isTerminalVariant(variant) ? neovimImage : debianImage);
        return expectedImage.equals(actualImage);
    }

    @Override
    public RuntimeStatus getRuntimeStatus(final String podName) {
        requireEnabled();
        final HttpRequest request = authedRequest("/api/v1/namespaces/" + namespace + "/pods/" + podName).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) return new RuntimeStatus(null, false, "NotFound", 0);
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("kubernetes_get_pod_failed: " + response.statusCode() + " " + response.body());
        }
        final var node = jsonCodec.readTree(response.body());
        final var phaseNode = jsonCodec.at(node, "/status/phase");
        final String phase = phaseNode.isMissingNode() || phaseNode.isNull() ? "Unknown" : phaseNode.asText();
        boolean ready = false;
        final var conditions = jsonCodec.at(node, "/status/conditions");
        if (conditions.isArray()) {
            for (final var condition : conditions) {
                if ("Ready".equals(condition.path("type").asText())) {
                    ready = "True".equals(condition.path("status").asText());
                    break;
                }
            }
        }
        String reason = node.path("status").path("reason").asText(null);
        int restartCount = 0;
        final var statuses = node.path("status").path("containerStatuses");
        if (statuses.isArray()) {
            for (final var status : statuses) {
                restartCount += status.path("restartCount").asInt(0);
                if (reason == null || reason.isBlank()) {
                    final String waitingReason = status.path("state").path("waiting").path("reason").asText("");
                    final String terminatedReason = status.path("state").path("terminated").path("reason").asText("");
                    reason = !waitingReason.isBlank() ? waitingReason
                            : (!terminatedReason.isBlank() ? terminatedReason : reason);
                }
            }
        }
        return new RuntimeStatus(phase, ready, reason, restartCount);
    }

    @Override
    public boolean isReady(final String podName) {
        requireEnabled();
        final HttpRequest request = authedRequest("/api/v1/namespaces/" + namespace + "/pods/" + podName).GET().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) return false;
        final var node = jsonCodec.readTree(response.body());
        final var conditions = jsonCodec.at(node, "/status/conditions");
        if (conditions.isMissingNode() || !conditions.isArray()) return false;
        for (final var condition : conditions) {
            if ("Ready".equals(condition.path("type").asText())) {
                return "True".equals(condition.path("status").asText());
            }
        }
        return false;
    }

    static String serviceName(final String podName) {
        return podName + "-svc";
    }

    /**
     * Puerto de control -- {@code port - 1}, fuera del rango de puertos de asiento
     * ({@code port..port+MAX_SEATS_PER_POD-1}). Escucha ahí el seat-agent (Fase B, modo
     * multi-asiento -- asignación de asientos) y el sandbox-file-agent (Fase 4.1, TODAS las
     * variantes incluyendo single-seat Web -- listado/lectura/escritura de archivos para el
     * moderador). No asumir que solo aplica a terminal-nvim.
     */
    private int controlPort() {
        return port - 1;
    }

    private static boolean isTerminalVariant(final String variant) {
        return IDE_MODE_TERMINAL_NVIM.equals(variant) || IDE_MODE_TERMINAL_NVIM_LAZYVIM.equals(variant)
                || Sandbox.isCliVariant(variant);
    }

    private static boolean isLazyVimVariant(final String variant) {
        return IDE_MODE_TERMINAL_NVIM_LAZYVIM.equals(variant) || Sandbox.VARIANT_CLI_LAZYVIM.equals(variant);
    }

    private String imageForTerminalVariant(final String variant) {
        return isLazyVimVariant(variant) ? neovimLazyVimImage : neovimImage;
    }

    private Map<String, Object> buildPodBody(final String podName, final String conferenceUuid, final String variant,
                                              final String remoteGitUrl,
                                              final Integer jvmHeapMb,
                                              final int effectiveSeats) {
        final Map<String, Object> labels = Map.of(
                "app.kubernetes.io/part-of", "insightbloom",
                "app.kubernetes.io/component", "sandbox",
                "sandbox-pod", podName,
                "sandbox-variant", variant,
                "sandbox-conference", Sandbox.conferenceLabel(conferenceUuid));

        final boolean terminalMode = isTerminalVariant(variant);

        final List<Map<String, Object>> runtimeEnv = new ArrayList<>();
        // El CLI de publicación usa este valor para identificar el evento sin
        // obligar al alumno a copiar un UUID desde la URL. Es metadato de
        // enrutamiento, no una credencial; por eso está disponible en todos
        // los sandboxes, incluidos Web y CLI de un solo asiento.
        runtimeEnv.add(Map.of("name", "CONFERENCE_UUID", "value", conferenceUuid));
        // Publicacion de backends/API REST vivos (2026-07): el alumno corre su server en este
        // puerto para que insightbloom-tools-gateway pueda proxearlo publicamente (ver
        // PublishAppPreviewUseCase). Pods de un solo asiento reciben APP_PORT directo, listo para
        // usar; Pods compartidos (terminal-nvim multi-asiento) solo reciben la base -- cada
        // asiento calcula el suyo (APP_BASE_PORT + SEAT_INDEX) en su propio entorno de shell, ver
        // sandbox-agent.py:_spawn_seat (mismo patron que ya usa SEAT_INDEX para los puertos de
        // debug Java/Python).
        runtimeEnv.add(Map.of("name", "APP_BASE_PORT", "value", String.valueOf(appBasePort)));
        if (effectiveSeats <= 1) {
            runtimeEnv.add(Map.of("name", "APP_PORT", "value", String.valueOf(appBasePort)));
        }
        // Publicación estática y tutor IA: el CLI debe alcanzar únicamente el API interno de
        // usuarios sin activar salida a internet ni obligar al alumno a copiar la URL pública.
        // La NetworkPolicy del chart permite este destino explícito; no contiene credenciales.
        final String usersApiBase = "http://insightbloom-users." + gatewayNamespace
                + ".svc.cluster.local:" + USERS_INTERNAL_PORT + "/api/v1";
        final String surveyApiBase = "http://insightbloom-survey." + gatewayNamespace
                + ".svc.cluster.local:" + SURVEY_INTERNAL_PORT + "/api/v1";
        runtimeEnv.add(Map.of("name", "INSIGHTBLOOM_API_BASE_URL", "value", usersApiBase));
        runtimeEnv.add(Map.of("name", "INSIGHTBLOOM_USERS_API", "value", usersApiBase));
        runtimeEnv.add(Map.of("name", "INSIGHTBLOOM_SURVEY_API", "value", surveyApiBase));
        if (remoteGitUrl != null && !remoteGitUrl.isBlank()) {
            runtimeEnv.add(Map.of("name", "REMOTE_GIT_URL", "value", remoteGitUrl));
        }
        // El proxy se declara siempre en ambos modos. La NetworkPolicy es la compuerta real:
        // con internetEnabled=false el Pod no puede alcanzar este Service; al habilitarlo,
        // los Pods existentes empiezan a usar la allowlist sin recrearse ni perder el workspace.
        // Esto evita que Web y CLI dependan de cuándo fueron creados.
        final String proxyUrl = "http://" + egressProxyHost + ":" + egressProxyPort;
        final String noProxy = "localhost,127.0.0.1,.svc,.svc.cluster.local," + gatewayNamespace;
        // Uppercase y lowercase: git/curl usan uppercase; algunas herramientas de
        // Node/Python solo consultan la variante lowercase.
        runtimeEnv.add(Map.of("name", "HTTP_PROXY", "value", proxyUrl));
        runtimeEnv.add(Map.of("name", "HTTPS_PROXY", "value", proxyUrl));
        runtimeEnv.add(Map.of("name", "http_proxy", "value", proxyUrl));
        runtimeEnv.add(Map.of("name", "https_proxy", "value", proxyUrl));
        runtimeEnv.add(Map.of("name", "NO_PROXY", "value", noProxy));
        runtimeEnv.add(Map.of("name", "no_proxy", "value", noProxy));
        // JDK_JAVA_OPTIONS (JEP 328, JDK 9+): lo lee CUALQUIER invocacion del launcher java/javac
        // dentro del contenedor -- jdt.ls (Language Server de Java), un "java MiClase" que corra
        // el alumno desde la terminal, o la JVM que forkea Maven -- sin tocar 3 configuraciones
        // por separado. A diferencia de JAVA_TOOL_OPTIONS/_JAVA_OPTIONS, JDK_JAVA_OPTIONS no
        // imprime un aviso "Picked up ..." en cada arranque de JVM (ruido feo en la terminal del
        // alumno). Pedido explicito del usuario: JVMs chicas por defecto (no "libres" tomando lo
        // que el auto-sizing por cgroups les permita, que puede ser casi todo el limite del
        // contenedor) -- default conservador si la conferencia no configuro un valor propio desde
        // el Dashboard (ver SetSandboxConfigUseCase, clampeado contra el limite del contenedor).
        final int heapMb = jvmHeapMb != null ? jvmHeapMb : DEFAULT_JVM_HEAP_MB;
        runtimeEnv.add(Map.of("name", "JDK_JAVA_OPTIONS", "value", "-Xmx" + heapMb + "m"));

        final boolean multiSeat = terminalMode && effectiveSeats > 1;
        if (multiSeat) {
            // Downward API: el agente lee su PROPIO limite de recursos del Pod (no un numero
            // duplicado a mano en otro lugar) para calcular el presupuesto "justo" por asiento
            // en el watchdog (ver sandbox-agent.py:_fair_share_budget). "sandbox" es el nombre
            // fijo del (unico) contenedor, ver mas abajo.
            runtimeEnv.add(Map.of("name", "POD_CPU_LIMIT_MILLICORES", "valueFrom", Map.of(
                    "resourceFieldRef", Map.of("containerName", "sandbox", "resource", "limits.cpu", "divisor", "1m"))));
            runtimeEnv.add(Map.of("name", "POD_MEMORY_LIMIT_MIB", "valueFrom", Map.of(
                    "resourceFieldRef", Map.of("containerName", "sandbox", "resource", "limits.memory", "divisor", "1Mi"))));
            runtimeEnv.add(Map.of("name", "SANDBOX_POD_NAME", "value", podName));
            // insightbloom-users vive en el mismo namespace que el gateway (gatewayNamespace,
            // "insightbloom") -- mismo patron de FQDN que ya usa el propio gateway para
            // resolverlo (GATEWAY_SANDBOX_RESOLVE_URL: "http://insightbloom-users:8081/...").
            // SANDBOX_INCIDENT_REPORT_KEY es un secreto de BAJO privilegio a proposito (no el
            // INTERNAL_API_KEY de plataforma) -- vive dentro de un contenedor que un alumno
            // puede leer via "env", lo peor que permite es escribir incidentes falsos, no
            // suplantar llamadas internas del resto de la plataforma (ver DEC-0025).
            runtimeEnv.add(Map.of("name", "SANDBOX_INCIDENT_REPORT_URL", "value",
                    "http://insightbloom-users." + gatewayNamespace + ".svc.cluster.local:8081/internal/sandbox-incidents"));
            runtimeEnv.add(Map.of("name", "SANDBOX_INCIDENT_REPORT_KEY", "value", incidentReportKey));
        }
        // Pods multi-asiento: cada asiento tiene su propio usuario Linux real, creado en
        // runtime por el seat-agent con home "/home/{userUuid}" (ver sandbox-agent.py) -- el
        // volumen "workspace" se monta en "/home" entero para que todos los home de asiento
        // persistan en el mismo emptyDir. El volumen "database" dedicado (flujo de descarga de
        // SQLite de un sandbox de 1 asiento, ver GenerateWorkspaceDownloadUrlUseCase) no se
        // monta aca -- gap conocido, documentado en
        // DEC-0025: cada asiento puede seguir usando SQLite dentro de su propio workspace, pero
        // el flujo de descarga dedicado no distingue asientos todavia.
        final List<Map<String, Object>> volumeMounts = multiSeat
                ? List.of(Map.of("name", "workspace", "mountPath", "/home"))
                : List.of(
                        Map.of("name", "workspace", "mountPath", "/home/coder/workspace"),
                        Map.of("name", "database", "mountPath", "/home/coder/db"));

        // Un puerto por asiento (siempre 1 salvo terminal-nvim con effectiveSeats > 1) -- ver
        // KubernetesPodClient.MAX_SEATS_PER_POD para el porque la NetworkPolicy declara un rango
        // fijo en vez de esto mismo. La Fase B (sandbox-agent, seat provisioning) es la que
        // efectivamente hace escuchar algo en los puertos mas alla del primero; hasta entonces
        // este Pod los declara pero solo el primero tiene un proceso real detras.
        final List<Map<String, Object>> containerPorts = new ArrayList<>();
        for (int i = 0; i < effectiveSeats; i++) {
            containerPorts.add(Map.of("name", "seat-" + i, "containerPort", port + i, "protocol", "TCP"));
        }
        for (int i = 0; i < effectiveSeats; i++) {
            containerPorts.add(Map.of("name", "app-" + i, "containerPort", appBasePort + i, "protocol", "TCP"));
        }

        final Map<String, Object> sandboxContainer = new LinkedHashMap<>();
        sandboxContainer.put("name", "sandbox");
        sandboxContainer.put("image", terminalMode ? imageForTerminalVariant(variant) : debianImage);
        // GitOps inyecta "Never" junto con tags inmutables ya precargados. Fuera de GitOps el
        // fallback es IfNotPresent + ghcr.io/...:latest, por lo que la imagen local se intenta
        // primero y GHCR solo se consulta si no existe en el nodo.
        sandboxContainer.put("imagePullPolicy", imagePullPolicy);
        sandboxContainer.put("ports", containerPorts);
        if (!runtimeEnv.isEmpty()) sandboxContainer.put("env", runtimeEnv);
        sandboxContainer.put("securityContext", containerSecurityContext(multiSeat));
        sandboxContainer.put("resources", resourcesBody(terminalMode ? neovimResources : debianResources));
        sandboxContainer.put("volumeMounts", volumeMounts);
        if (multiSeat) {
            // Sandbox-agent (ver Dockerfile.code-ide-neovim): un proceso raiz-con-capabilities-
            // minimas (ver containerSecurityContext(true)) que arranca un ttyd por asiento bajo
            // demanda (KubernetesPodClient.provisionSeat), dropeando privilegios al uid real de
            // cada asiento antes de exec -- ningun ttyd/nvim/proceso del alumno corre como root,
            // solo este agente lo hace, y solo mientras espera pedidos de aprovisionamiento.
            sandboxContainer.put("args", List.of(
                    "python3", "/usr/local/bin/sandbox-agent.py",
                    "--base-port", String.valueOf(port), "--max-seats", String.valueOf(effectiveSeats)));
        } else if (terminalMode) {
            // Kubernetes reemplaza el CMD de la imagen cuando fija args. Por eso el modo
            // single-seat debe repetir aqui la inicializacion que normalmente vive en el CMD:
            // sembrar el workspace y arrancar sandbox-file-agent.py. Sin el agente, el terminal
            // funciona pero la moderacion/editor recibe 404 en /files/{seatIndex}.
            final String singleSeatCommand = String.join("; ",
                    "/usr/local/bin/seed-remote-git.sh /home/coder/workspace",
                    "/usr/local/bin/seed-node-types.sh /home/coder/workspace",
                    "/usr/local/bin/seed-ide-docs.sh /home/coder/workspace",
                    "python3 /usr/local/bin/sandbox-file-agent.py --control-port " + controlPort() + " &",
                    "exec ttyd -p " + port + " -W --ping-interval 15 bash -lc 'cd /home/coder/workspace && exec nvim .'"
            );
            // --ping-interval 15 (default de ttyd es 5s, se deja explicito por legibilidad):
            // auditoria de seguridad 2026-07-17 -- detecta conexiones muertas mas rapido, para
            // no dejar una sesion de terminal "colgada" alcanzable mas tiempo del necesario si
            // el navegador del alumno se cierra sin un cierre limpio de la conexion WS.
            sandboxContainer.put("args", List.of(
                    "sh", "-c", singleSeatCommand));
        }
        // El control port (seat-agent) tambien debe responder para que el Pod se considere listo
        // -- corre en el mismo puerto base que el asiento 0 en modo single-seat, pero en modo
        // multi-asiento el agente escucha el puerto de control real (basePort - 1, ver
        // sandbox-agent.py) mientras que basePort mismo recien responde cuando se aprovisiona el
        // primer asiento -- las probes de multi-asiento chequean el puerto de CONTROL, no el de
        // un asiento que puede no existir todavia.
        final int healthProbePort = multiSeat ? (port - 1) : port;
        sandboxContainer.put("readinessProbe", tcpProbe(healthProbePort, 5, 10, 2));
        sandboxContainer.put("livenessProbe", tcpProbe(healthProbePort, 10, 30, 3));
        sandboxContainer.put("startupProbe", tcpProbe(healthProbePort, 5, 10, 30));
        final List<Map<String, Object>> containers = List.of(sandboxContainer);

        final Map<String, Object> podSecurityContext = new LinkedHashMap<>();
        if (multiSeat) {
            // El seat-agent necesita setuid/setgid (dropear privilegios al uid real de cada
            // asiento antes de exec ttyd) y kill (Fase C: matar el arbol de procesos de un
            // asiento que abusa recursos) -- ninguno de los dos es posible sin correr como uid 0.
            // Se compensa manteniendo TODAS las demas capabilities dropeadas (ver
            // containerSecurityContext(true)) -- root nominal, pero sin mas permiso real que el
            // estrictamente necesario para administrar los asientos. Los procesos de CADA
            // asiento (ttyd/nvim/lo que corra el alumno) dropean a su propio uid no-root antes
            // de exec -- nunca corren como root, solo el agente en si.
            podSecurityContext.put("runAsNonRoot", false);
            podSecurityContext.put("runAsUser", 0);
            podSecurityContext.put("runAsGroup", 0);
        } else {
            podSecurityContext.put("runAsNonRoot", true);
            podSecurityContext.put("runAsUser", uid);
            podSecurityContext.put("runAsGroup", gid);
        }
        podSecurityContext.put("fsGroup", fsGroup);
        podSecurityContext.put("seccompProfile", Map.of("type", "RuntimeDefault"));
        // capabilities.drop pertenece a securityContext de CONTENEDOR, no de Pod (la API lo
        // ignora en silencio a nivel Pod) — se declara correctamente en containerSecurityContext()
        // para cada contenedor.

        final Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("serviceAccountName", "default");
        spec.put("automountServiceAccountToken", false);
        spec.put("restartPolicy", "Always");
        if (priorityClassName != null && !priorityClassName.isBlank()) {
            spec.put("priorityClassName", priorityClassName);
        }
        spec.put("securityContext", podSecurityContext);
        spec.put("containers", containers);
        spec.put("volumes", List.of(
                Map.of("name", "workspace", "emptyDir", Map.of()),
                Map.of("name", "database", "emptyDir", Map.of())));

        // Annotation con el UUID COMPLETO (a diferencia de la label "sandbox-conference", que
        // trunca a 8 caracteres para caber en el limite de labels de k8s) -- la usa
        // findConferenceUuidByPodIp para resolver politica de egress por evento sin tener que
        // escanear todas las conferencias buscando cual matchea el label truncado.
        final Map<String, Object> annotations = Map.of("insightbloom.io/conference-uuid", conferenceUuid);

        return Map.of(
                "apiVersion", "v1",
                "kind", "Pod",
                "metadata", Map.of("name", podName, "namespace", namespace, "labels", labels,
                        "annotations", annotations),
                "spec", spec);
    }

    /**
     * @param multiSeat true solo para el contenedor "sandbox" de un Pod neovim multi-asiento --
     *                  el seat-agent (ver sandbox-agent.py) necesita crear la cuenta Linux REAL
     *                  de cada alumno en runtime (adduser -- CHOWN/FOWNER para poder asignarle
     *                  su home) y despues arrancar su ttyd bajo ese uid (SETUID/SETGID) o matar
     *                  su arbol de procesos si abusa recursos (KILL, Fase C). Deliberadamente
     *                  NO se usa sudo para esto (exigiria allowPrivilegeEscalation:true, que
     *                  reabriria CUALQUIER binario setuid/setgid de la imagen, no solo este caso
     *                  puntual -- ver DEC-0025). Se compensa dropeando TODAS las demas
     *                  capabilities -- root nominal, pero sin mas permiso real que estas 5
     *                  syscalls especificas. Cualquier otro Pod (debian, o neovim de un solo
     *                  asiento) sigue exactamente igual que siempre: sin capabilities, sin poder
     *                  escalar privilegios, usuario fijo no-root.
     */
    private Map<String, Object> containerSecurityContext(final boolean multiSeat) {
        final Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("allowPrivilegeEscalation", false);
        ctx.put("readOnlyRootFilesystem", false);
        if (multiSeat) {
            ctx.put("runAsNonRoot", false);
            ctx.put("runAsUser", 0);
            ctx.put("capabilities", Map.of("drop", List.of("ALL"),
                    "add", List.of("SETUID", "SETGID", "KILL", "CHOWN", "FOWNER")));
        } else {
            ctx.put("runAsNonRoot", true);
            ctx.put("runAsUser", uid);
            // NET_RAW: la imagen debian/code-server trae iputils-ping (ver
            // Dockerfile.code-ide-debian) para que el alumno pueda diagnosticar red desde el
            // IDE. El binario no es setuid-root -- sin esta capability, ping falla con
            // "Operation not permitted" aunque el ejecutable exista. No amplia el egress
            // real: la NetworkPolicy del evento (ver allowInternetEgress/buildEgressAllowBody)
            // sigue siendo
            // deny-all-excepto-egress-proxy en TCP, así que ICMP saliente a internet igual
            // queda bloqueado por el CNI -- ping solo deja de fallar con un error de permisos
            // confuso, no habilita tráfico nuevo.
            ctx.put("capabilities", Map.of("drop", List.of("ALL"), "add", List.of("NET_RAW")));
        }
        return ctx;
    }

    private static Map<String, Object> resourcesBody(final ContainerResources resources) {
        return Map.of(
                "requests", Map.of("cpu", resources.cpuRequest(), "memory", resources.memoryRequest()),
                "limits", Map.of("cpu", resources.cpuLimit(), "memory", resources.memoryLimit()));
    }

    private Map<String, Object> tcpProbe(final int targetPort, final int initialDelay, final int period,
                                          final int failureThreshold) {
        return Map.of(
                "tcpSocket", Map.of("port", targetPort),
                "initialDelaySeconds", initialDelay,
                "periodSeconds", period,
                "failureThreshold", failureThreshold);
    }

    private Map<String, Object> buildServiceBody(final String podName, final int effectiveSeats) {
        final List<Map<String, Object>> servicePorts = new ArrayList<>();
        for (int i = 0; i < effectiveSeats; i++) {
            servicePorts.add(Map.of("name", "seat-" + i, "port", port + i, "targetPort", port + i, "protocol", "TCP"));
        }
        // Puerto del backend/API REST que el alumno publica (2026-07) -- un puerto por asiento,
        // mismo criterio que "seat-N" (ver ResolveAppPreviewTargetUseCase, que arma el target
        // hacia este Service igual que ResolveSandboxTargetUseCase).
        for (int i = 0; i < effectiveSeats; i++) {
            servicePorts.add(Map.of("name", "app-" + i, "port", appBasePort + i, "targetPort", appBasePort + i,
                    "protocol", "TCP"));
        }
        // Puerto de control (agente en el Pod, ver code-ide-entrypoint.sh): tanto el seat-agent
        // (terminal-nvim, asignación de asientos) como el sandbox-file-agent (AMBAS variantes,
        // Web incluido -- Fase 4.1) escuchan en controlPort() dentro del Pod. Sin este puerto en
        // el Service, la llamada via DNS del Service se rechaza al instante (bug real detectado
        // en producción 2026-07-19: el Service solo tenía los puertos de asiento). Se agrega
        // siempre -- no es exclusivo de terminal-nvim como se pensó en el primer fix.
        servicePorts.add(Map.of("name", "control", "port", controlPort(), "targetPort", controlPort(), "protocol", "TCP"));
        return Map.of(
                "apiVersion", "v1",
                "kind", "Service",
                "metadata", Map.of("name", serviceName(podName), "namespace", namespace),
                "spec", Map.of(
                        "selector", Map.of("sandbox-pod", podName),
                        "ports", servicePorts));
    }

    private void postIgnoringConflict(final String path, final String body, final String description) {
        final HttpRequest request = authedRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() == 409) {
            LOGGER.info(() -> "kubernetes: " + description + " ya existe, se reusa");
            return;
        }
        if (response.statusCode() >= 300) {
            throw new IllegalStateException("kubernetes_create_failed: " + description + " -> "
                    + response.statusCode() + " " + response.body());
        }
    }

    /**
     * Crea o reconcilia una NetworkPolicy dinámica.
     *
     * Un POST que ignora 409 deja intacta la especificación vieja. Eso es peligroso
     * para la política de salida: si el organizador desactiva Internet, un sandbox
     * podía seguir usando una regla anterior. Kubernetes acepta un merge-patch del
     * recurso existente; al reemplazar {@code spec} hacemos la operación idempotente
     * y mantenemos el selector/alcance definido por el evento.
     */
    private void upsertNetworkPolicy(final String path, final String body, final String description,
                                     final String resourceName) {
        final HttpRequest create = authedRequest(path)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> created = send(create);
        if (created.statusCode() < 300) {
            return;
        }
        if (created.statusCode() != 409) {
            throw new IllegalStateException("kubernetes_create_failed: " + description + " -> "
                    + created.statusCode() + " " + created.body());
        }

        final HttpRequest patch = HttpRequest.newBuilder(URI.create(apiBaseUrl + path + "/" + resourceName))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/merge-patch+json")
                .header("Accept", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .build();
        final HttpResponse<String> updated = send(patch);
        if (updated.statusCode() >= 300) {
            throw new IllegalStateException("kubernetes_update_failed: " + description + " -> "
                    + updated.statusCode() + " " + updated.body());
        }
    }

    private void deleteIgnoring404(final String path) {
        final HttpRequest request = authedRequest(path).DELETE().build();
        final HttpResponse<String> response = send(request);
        if (response.statusCode() != 404 && response.statusCode() >= 300) {
            LOGGER.log(Level.WARNING, () -> "kubernetes: fallo al borrar " + path + " -> "
                    + response.statusCode() + " " + response.body());
        }
    }

    private HttpRequest.Builder authedRequest(final String path) {
        return HttpRequest.newBuilder(URI.create(apiBaseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private HttpResponse<String> send(final HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final IOException | InterruptedException e) {
            throw new IllegalStateException("kubernetes_request_failed: " + request.uri(), e);
        }
    }

    private HttpResponse<byte[]> sendBytes(final HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (final IOException | InterruptedException e) {
            throw new IllegalStateException("kubernetes_request_failed: " + request.uri(), e);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("kubernetes_not_configured");
        }
    }

    private static String readTokenOrNull() {
        try {
            return Files.readString(TOKEN_PATH, StandardCharsets.UTF_8).trim();
        } catch (final IOException e) {
            return null;
        }
    }

    private HttpClient buildHttpClient() {
        final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        try {
            if (Files.exists(CA_PATH)) {
                builder.sslContext(buildTrustingSslContext(Files.readAllBytes(CA_PATH)));
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "no se pudo cargar el CA cert del ServiceAccount, se usa el trust store por defecto", e);
        }
        return builder.build();
    }

    private static SSLContext buildTrustingSslContext(final byte[] caCertBytes) throws Exception {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        final var certificate = certificateFactory.generateCertificate(new ByteArrayInputStream(caCertBytes));
        final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("kubernetes-ca", certificate);
        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}
