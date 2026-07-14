package dev.rafex.insightbloom.users.adapters.outbound.kubernetes;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import dev.rafex.insightbloom.users.domain.ports.SandboxOrchestrator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
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
 * Fase 4: el Pod tiene dos contenedores — {@code ide} (code-server, imagen Debian fija) y
 * {@code runtime} (toolchain por variante, imagen Alpine). La terminal integrada de code-server
 * se conecta al contenedor {@code runtime} vía un servidor socat en {@code 127.0.0.1:7681}
 * (loopback intra-Pod, nunca expuesto via Service) — ver {@code code-ide-settings.json}. El
 * {@code Service} del Pod sigue enrutando solo al puerto del contenedor {@code ide}.
 *
 * El spec de Pod replica el hardening documentado (pero nunca renderizado) en
 * {@code infra/helm/charts/insightbloom/templates/sandbox-pool.yaml}: non-root, sin capabilities,
 * seccomp RuntimeDefault, sin montar el token del ServiceAccount dentro del propio sandbox (en
 * ninguno de los dos contenedores).
 *
 * Fase 3 (ver plan de implementacion): RBAC (Role+RoleBinding en el namespace
 * insightbloom-sandboxes) es lo que autoriza a insightbloom-users a crear/borrar estos recursos —
 * sin esa RoleBinding el API server responde 403 y {@link #createSandbox} lo propaga como excepcion.
 */
public class KubernetesPodClient implements SandboxOrchestrator {
    private static final Logger LOGGER = Logger.getLogger(KubernetesPodClient.class.getName());
    private static final Path TOKEN_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/token");
    private static final Path CA_PATH = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
    /** Puerto del servidor socat del contenedor runtime — solo loopback intra-Pod, nunca via Service. */
    private static final int RUNTIME_PORT = 7681;

    /** Límites de recursos de un contenedor del Pod de sandbox. */
    public record ContainerResources(String cpuRequest, String memoryRequest, String cpuLimit, String memoryLimit) {
    }

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;
    private final JsonCodec jsonCodec;
    private final String namespace;
    private final String serverImage;
    private final String runtimeImageBase;
    private final ContainerResources ideResources;
    private final ContainerResources runtimeResources;
    private final int port;
    private final int uid;
    private final int gid;
    private final int fsGroup;

    public KubernetesPodClient(final JsonCodec jsonCodec, final String namespace,
                                final String serverImage, final String runtimeImageBase,
                                final ContainerResources ideResources, final ContainerResources runtimeResources,
                                final int port, final int uid, final int gid, final int fsGroup) {
        this.jsonCodec = jsonCodec;
        this.namespace = namespace;
        this.serverImage = serverImage;
        this.runtimeImageBase = runtimeImageBase;
        this.ideResources = ideResources;
        this.runtimeResources = runtimeResources;
        this.port = port;
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
                               final String extraPackages, final String remoteGitUrl, final boolean internetEnabled) {
        requireEnabled();
        final String podJson = jsonCodec.toJson(buildPodBody(podName, conferenceUuid, variant, extraPackages, remoteGitUrl));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/pods", podJson, "pod " + podName);
        final String serviceJson = jsonCodec.toJson(buildServiceBody(podName));
        postIgnoringConflict("/api/v1/namespaces/" + namespace + "/services", serviceJson, "service " + serviceName(podName));
        if (internetEnabled) {
            allowInternetEgress(Sandbox.conferenceLabel(conferenceUuid));
        }
    }

    @Override
    public void allowInternetEgress(final String conferenceLabel) {
        requireEnabled();
        final String policyJson = jsonCodec.toJson(buildEgressAllowBody(conferenceLabel));
        postIgnoringConflict("/apis/networking.k8s.io/v1/namespaces/" + namespace + "/networkpolicies",
                policyJson, "networkpolicy " + egressPolicyName(conferenceLabel));
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

    static String serviceName(final String podName) {
        return podName + "-svc";
    }

    private Map<String, Object> buildPodBody(final String podName, final String conferenceUuid, final String variant,
                                              final String extraPackages, final String remoteGitUrl) {
        final Map<String, Object> labels = Map.of(
                "app.kubernetes.io/part-of", "insightbloom",
                "app.kubernetes.io/component", "sandbox",
                "sandbox-pod", podName,
                "sandbox-variant", variant,
                "sandbox-conference", Sandbox.conferenceLabel(conferenceUuid));

        final List<Map<String, Object>> runtimeEnv = new ArrayList<>();
        if (extraPackages != null && !extraPackages.isBlank()) {
            runtimeEnv.add(Map.of("name", "EXTRA_PACKAGES", "value", extraPackages));
        }
        if (remoteGitUrl != null && !remoteGitUrl.isBlank()) {
            runtimeEnv.add(Map.of("name", "REMOTE_GIT_URL", "value", remoteGitUrl));
        }

        final List<Map<String, Object>> volumeMounts = List.of(
                Map.of("name", "workspace", "mountPath", "/home/coder/workspace"),
                Map.of("name", "database", "mountPath", "/home/coder/db"));

        final Map<String, Object> ideContainer = new LinkedHashMap<>();
        ideContainer.put("name", "ide");
        ideContainer.put("image", serverImage);
        ideContainer.put("imagePullPolicy", "Always");
        ideContainer.put("ports", List.of(Map.of("name", "http", "containerPort", port, "protocol", "TCP")));
        ideContainer.put("securityContext", containerSecurityContext());
        ideContainer.put("resources", resourcesBody(ideResources));
        ideContainer.put("volumeMounts", volumeMounts);
        ideContainer.put("readinessProbe", tcpProbe(port, 5, 10, 2));
        ideContainer.put("livenessProbe", tcpProbe(port, 10, 30, 3));
        ideContainer.put("startupProbe", tcpProbe(port, 5, 10, 30));

        // El contenedor runtime no expone su puerto vía Service ni Ingress: solo alcanzable por
        // loopback intra-Pod desde 'ide' (terminal integrada de code-server -> socat).
        final Map<String, Object> runtimeContainer = new LinkedHashMap<>();
        runtimeContainer.put("name", "runtime");
        runtimeContainer.put("image", runtimeImageBase + ":" + variant);
        runtimeContainer.put("imagePullPolicy", "Always");
        if (!runtimeEnv.isEmpty()) runtimeContainer.put("env", runtimeEnv);
        runtimeContainer.put("securityContext", containerSecurityContext());
        runtimeContainer.put("resources", resourcesBody(runtimeResources));
        runtimeContainer.put("volumeMounts", volumeMounts);
        runtimeContainer.put("readinessProbe", tcpProbe(RUNTIME_PORT, 5, 10, 2));
        runtimeContainer.put("livenessProbe", tcpProbe(RUNTIME_PORT, 10, 30, 3));
        runtimeContainer.put("startupProbe", tcpProbe(RUNTIME_PORT, 5, 10, 30));

        final Map<String, Object> podSecurityContext = new LinkedHashMap<>();
        podSecurityContext.put("runAsNonRoot", true);
        podSecurityContext.put("runAsUser", uid);
        podSecurityContext.put("runAsGroup", gid);
        podSecurityContext.put("fsGroup", fsGroup);
        podSecurityContext.put("seccompProfile", Map.of("type", "RuntimeDefault"));
        // capabilities.drop pertenece a securityContext de CONTENEDOR, no de Pod (la API lo
        // ignora en silencio a nivel Pod) — se declara correctamente en containerSecurityContext()
        // para cada uno de los dos contenedores.

        final Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("serviceAccountName", "default");
        spec.put("automountServiceAccountToken", false);
        spec.put("restartPolicy", "Never");
        spec.put("securityContext", podSecurityContext);
        spec.put("containers", List.of(ideContainer, runtimeContainer));
        spec.put("volumes", List.of(
                Map.of("name", "workspace", "emptyDir", Map.of()),
                Map.of("name", "database", "emptyDir", Map.of())));

        return Map.of(
                "apiVersion", "v1",
                "kind", "Pod",
                "metadata", Map.of("name", podName, "namespace", namespace, "labels", labels),
                "spec", spec);
    }

    private Map<String, Object> containerSecurityContext() {
        final Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("allowPrivilegeEscalation", false);
        ctx.put("runAsNonRoot", true);
        ctx.put("runAsUser", uid);
        ctx.put("readOnlyRootFilesystem", false);
        ctx.put("capabilities", Map.of("drop", List.of("ALL")));
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

    private Map<String, Object> buildServiceBody(final String podName) {
        return Map.of(
                "apiVersion", "v1",
                "kind", "Service",
                "metadata", Map.of("name", serviceName(podName), "namespace", namespace),
                "spec", Map.of(
                        "selector", Map.of("sandbox-pod", podName),
                        "ports", List.of(Map.of("port", port, "targetPort", port, "protocol", "TCP"))));
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
