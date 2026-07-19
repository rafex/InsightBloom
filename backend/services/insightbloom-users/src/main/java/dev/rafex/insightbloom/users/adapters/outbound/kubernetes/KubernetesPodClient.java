package dev.rafex.insightbloom.users.adapters.outbound.kubernetes;

import dev.rafex.ether.json.JsonCodec;
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
    /**
     * Heap por defecto (-Xmx, en MB) cuando la conferencia no configuro uno propio desde el
     * Dashboard -- pedido explicito del usuario: JVMs chicas, pensadas para cursos, no "libres".
     * 256Mi alcanza para jdt.ls + un programa chico de curso; sigue siendo mucho menor que el
     * limite de memoria del contenedor (ver ContainerResources en UsersApplication), dejando
     * margen real para code-server/extension-host o ttyd+nvim.
     */
    private static final int DEFAULT_JVM_HEAP_MB = 256;
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

    public KubernetesPodClient(final JsonCodec jsonCodec, final String namespace,
                                final String debianImage, final String neovimImage,
                                final ContainerResources debianResources, final ContainerResources neovimResources,
                                final int port, final int uid, final int gid, final int fsGroup,
                                final String gatewayNamespace, final String gatewayPodComponentLabel,
                                final String usersPodComponentLabel, final String incidentReportKey) {
        this.jsonCodec = jsonCodec;
        this.namespace = namespace;
        this.debianImage = debianImage;
        this.neovimImage = neovimImage;
        this.debianResources = debianResources;
        this.neovimResources = neovimResources;
        this.port = port;
        this.gatewayNamespace = gatewayNamespace;
        this.gatewayPodComponentLabel = gatewayPodComponentLabel;
        this.usersPodComponentLabel = usersPodComponentLabel;
        this.incidentReportKey = incidentReportKey;
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
                               final String extraPackages, final String remoteGitUrl, final boolean internetEnabled,
                               final Integer jvmHeapMb, final Integer seatsPerPod) {
        requireEnabled();
        final boolean terminalMode = IDE_MODE_TERMINAL_NVIM.equals(variant);
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
                buildPodBody(podName, conferenceUuid, variant, extraPackages, remoteGitUrl, jvmHeapMb, effectiveSeats));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/pods", podJson, "pod " + podName);
        final String serviceJson = jsonCodec.toJson(buildServiceBody(podName, effectiveSeats, terminalMode));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/services", serviceJson, "service " + serviceName(podName));
        if (internetEnabled) {
            allowInternetEgress(Sandbox.conferenceLabel(conferenceUuid));
        }
    }

    private static final String INGRESS_POLICY_NAME = "sandbox-ingress-gateway-only";
    private static final String INCIDENT_EGRESS_POLICY_NAME = "sandbox-egress-incident-report";
    /** Puerto interno de insightbloom-users, mismo que ya usa el gateway (ver
     *  GATEWAY_SANDBOX_RESOLVE_URL en GatewayApplication.java). */
    private static final int USERS_INTERNAL_PORT = 8081;

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
        postIgnoringConflict("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                jsonCodec.toJson(policy), "networkpolicy " + INCIDENT_EGRESS_POLICY_NAME);
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
                                        "ports", List.of(Map.of("protocol", "TCP", "port", controlPort()))))));
        postIgnoringConflict("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                jsonCodec.toJson(policy), "networkpolicy " + INGRESS_POLICY_NAME);
    }

    @Override
    public void allowInternetEgress(final String conferenceLabel) {
        requireEnabled();
        final String policyJson = jsonCodec.toJson(buildEgressAllowBody(conferenceLabel));
        postIgnoringConflict("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                policyJson, "networkpolicy " + egressPolicyName(conferenceLabel));
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
    public void denyInternetEgress(final String conferenceLabel) {
        requireEnabled();
        deleteIgnoring404("/apis/networking.k8s.io/v1/namespaces/" + namespace
                + "/networkpolicies/" + egressPolicyName(conferenceLabel));
    }

    private static String egressPolicyName(final String conferenceLabel) {
        return "sandbox-egress-" + conferenceLabel;
    }

    private Map<String, Object> buildEgressAllowBody(final String conferenceLabel) {
        return Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "NetworkPolicy",
                "metadata", Map.of("name", egressPolicyName(conferenceLabel), "namespace", namespace),
                "spec", Map.of(
                        "podSelector", Map.of("matchLabels", Map.of("sandbox-conference", conferenceLabel)),
                        "policyTypes", List.of("Egress"),
                        // Egress abierto: el default-deny egress del namespace (sandbox-networkpolicy.yaml)
                        // ya bloquea todo lo demas; esta policy solo re-abre para los pods de este evento.
                        "egress", List.of(Map.of())));
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
     * Puerto de control del seat-agent (Fase B) -- {@code port - 1}, fuera del rango de puertos
     * de asiento ({@code port..port+MAX_SEATS_PER_POD-1}). Solo escucha ahi el Pod cuando corre
     * en modo multi-asiento; en modo single-seat no hay agente ni puerto de control.
     */
    private int controlPort() {
        return port - 1;
    }

    private Map<String, Object> buildPodBody(final String podName, final String conferenceUuid, final String variant,
                                              final String extraPackages, final String remoteGitUrl,
                                              final Integer jvmHeapMb, final int effectiveSeats) {
        final Map<String, Object> labels = Map.of(
                "app.kubernetes.io/part-of", "insightbloom",
                "app.kubernetes.io/component", "sandbox",
                "sandbox-pod", podName,
                "sandbox-variant", variant,
                "sandbox-conference", Sandbox.conferenceLabel(conferenceUuid));

        final boolean terminalMode = IDE_MODE_TERMINAL_NVIM.equals(variant);

        final List<Map<String, Object>> runtimeEnv = new ArrayList<>();
        if (extraPackages != null && !extraPackages.isBlank()) {
            runtimeEnv.add(Map.of("name", "EXTRA_PACKAGES", "value", extraPackages));
        }
        if (remoteGitUrl != null && !remoteGitUrl.isBlank()) {
            runtimeEnv.add(Map.of("name", "REMOTE_GIT_URL", "value", remoteGitUrl));
        }
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
            runtimeEnv.add(Map.of("name", "CONFERENCE_UUID", "value", conferenceUuid));
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

        final Map<String, Object> sandboxContainer = new LinkedHashMap<>();
        sandboxContainer.put("name", "sandbox");
        sandboxContainer.put("image", terminalMode ? neovimImage : debianImage);
        sandboxContainer.put("imagePullPolicy", "Always");
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
            // --ping-interval 15 (default de ttyd es 5s, se deja explicito por legibilidad):
            // auditoria de seguridad 2026-07-17 -- detecta conexiones muertas mas rapido, para
            // no dejar una sesion de terminal "colgada" alcanzable mas tiempo del necesario si
            // el navegador del alumno se cierra sin un cierre limpio de la conexion WS.
            sandboxContainer.put("args", List.of(
                    "ttyd", "-p", String.valueOf(port), "-W", "--ping-interval", "15",
                    "bash", "-lc", "cd /home/coder/workspace && exec nvim ."));
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
        spec.put("restartPolicy", "Never");
        spec.put("securityContext", podSecurityContext);
        spec.put("containers", containers);
        spec.put("volumes", List.of(
                Map.of("name", "workspace", "emptyDir", Map.of()),
                Map.of("name", "database", "emptyDir", Map.of())));

        return Map.of(
                "apiVersion", "v1",
                "kind", "Pod",
                "metadata", Map.of("name", podName, "namespace", namespace, "labels", labels),
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
            ctx.put("capabilities", Map.of("drop", List.of("ALL")));
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

    private Map<String, Object> buildServiceBody(final String podName, final int effectiveSeats,
                                                  final boolean includeControlPort) {
        final List<Map<String, Object>> servicePorts = new ArrayList<>();
        for (int i = 0; i < effectiveSeats; i++) {
            servicePorts.add(Map.of("name", "seat-" + i, "port", port + i, "targetPort", port + i, "protocol", "TCP"));
        }
        // Modo terminal-nvim: el seat-agent escucha en controlPort() (provisionSeat lo llama vía
        // el Service, no la IP del Pod) -- sin este puerto el Service no tiene endpoint para
        // 8079 y la llamada de asignación de asiento falla con conexión rechazada (bug real
        // detectado en producción 2026-07-19: el Service solo tenía los puertos de asiento).
        if (includeControlPort) {
            servicePorts.add(Map.of("name", "control", "port", controlPort(), "targetPort", controlPort(), "protocol", "TCP"));
        }
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
