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
 * El spec de Pod replica exactamente lo documentado (pero nunca renderizado) en
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

    private final HttpClient httpClient;
    private final String apiBaseUrl;
    private final String token;
    private final JsonCodec jsonCodec;
    private final String namespace;
    private final String imageBase;
    private final String cpuRequest;
    private final String memoryRequest;
    private final String cpuLimit;
    private final String memoryLimit;
    private final int port;
    private final int uid;
    private final int gid;
    private final int fsGroup;

    public KubernetesPodClient(final JsonCodec jsonCodec, final String namespace, final String imageBase,
                                final String cpuRequest, final String memoryRequest,
                                final String cpuLimit, final String memoryLimit,
                                final int port, final int uid, final int gid, final int fsGroup) {
        this.jsonCodec = jsonCodec;
        this.namespace = namespace;
        this.imageBase = imageBase;
        this.cpuRequest = cpuRequest;
        this.memoryRequest = memoryRequest;
        this.cpuLimit = cpuLimit;
        this.memoryLimit = memoryLimit;
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

        final List<Map<String, Object>> env = new ArrayList<>();
        if (extraPackages != null && !extraPackages.isBlank()) {
            env.add(Map.of("name", "EXTRA_PACKAGES", "value", extraPackages));
        }
        if (remoteGitUrl != null && !remoteGitUrl.isBlank()) {
            env.add(Map.of("name", "REMOTE_GIT_URL", "value", remoteGitUrl));
        }

        final Map<String, Object> containerSecurityContext = new LinkedHashMap<>();
        containerSecurityContext.put("allowPrivilegeEscalation", false);
        containerSecurityContext.put("runAsNonRoot", true);
        containerSecurityContext.put("runAsUser", uid);
        containerSecurityContext.put("readOnlyRootFilesystem", false);
        containerSecurityContext.put("capabilities", Map.of("drop", List.of("ALL")));

        final Map<String, Object> container = new LinkedHashMap<>();
        container.put("name", "code-server");
        container.put("image", imageBase + ":" + variant);
        container.put("imagePullPolicy", "Always");
        container.put("ports", List.of(Map.of("name", "http", "containerPort", port, "protocol", "TCP")));
        if (!env.isEmpty()) container.put("env", env);
        container.put("securityContext", containerSecurityContext);
        container.put("resources", Map.of(
                "requests", Map.of("cpu", cpuRequest, "memory", memoryRequest),
                "limits", Map.of("cpu", cpuLimit, "memory", memoryLimit)));
        container.put("volumeMounts", List.of(
                Map.of("name", "workspace", "mountPath", "/home/coder/workspace"),
                Map.of("name", "database", "mountPath", "/home/coder/db")));
        container.put("readinessProbe", tcpProbe(5, 10, 2));
        container.put("livenessProbe", tcpProbe(10, 30, 3));
        container.put("startupProbe", tcpProbe(5, 10, 30));

        final Map<String, Object> podSecurityContext = new LinkedHashMap<>();
        podSecurityContext.put("runAsNonRoot", true);
        podSecurityContext.put("runAsUser", uid);
        podSecurityContext.put("runAsGroup", gid);
        podSecurityContext.put("fsGroup", fsGroup);
        podSecurityContext.put("seccompProfile", Map.of("type", "RuntimeDefault"));
        podSecurityContext.put("capabilities", Map.of("drop", List.of("ALL")));

        final Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("serviceAccountName", "default");
        spec.put("automountServiceAccountToken", false);
        spec.put("restartPolicy", "Never");
        spec.put("securityContext", podSecurityContext);
        spec.put("containers", List.of(container));
        spec.put("volumes", List.of(
                Map.of("name", "workspace", "emptyDir", Map.of()),
                Map.of("name", "database", "emptyDir", Map.of())));

        return Map.of(
                "apiVersion", "v1",
                "kind", "Pod",
                "metadata", Map.of("name", podName, "namespace", namespace, "labels", labels),
                "spec", spec);
    }

    private Map<String, Object> tcpProbe(final int initialDelay, final int period, final int failureThreshold) {
        return Map.of(
                "tcpSocket", Map.of("port", port),
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
