package dev.rafex.insightbloom.users.adapters.outbound.kubernetes;

import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.domain.model.Sandbox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 7 (2026-08): {@code buildPodBody}/{@code buildInitContainer} son privados y
 * {@link KubernetesPodClient} exige un cluster real para sus métodos públicos
 * ({@code requireEnabled()}) -- este test invoca los builders del pod spec vía reflexión,
 * sin depender de Kubernetes, para verificar que el initContainer de bloqueo de egress queda
 * bien formado (capabilities correctas, sin filtrar a los contenedores del alumno).
 */
class KubernetesPodClientInitContainerTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPodBody(final KubernetesPodClient client, final String variant,
                                              final String remoteGitUrl, final int effectiveSeats) throws Exception {
        final Method m = KubernetesPodClient.class.getDeclaredMethod("buildPodBody",
                String.class, String.class, String.class, String.class, Integer.class, int.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(client, "sandbox-test-web-0", "conf-uuid-1234", variant,
                remoteGitUrl, null, effectiveSeats);
    }

    private KubernetesPodClient newClient() {
        return new KubernetesPodClient(JacksonJsonCodec.defaultCodec(), "insightbloom-sandboxes",
                "debian-image:latest", "neovim-image:latest", "neovim-lazyvim-image:latest",
                "IfNotPresent", null,
                new KubernetesPodClient.ContainerResources("500m", "1Gi", "750m", "1536Mi"),
                new KubernetesPodClient.ContainerResources("350m", "768Mi", "500m", "1Gi"),
                8080, 1000, 1000, 1000,
                "insightbloom", "toolsgateway", "users", "incident-key",
                "egress-proxy-host", 3128, 9000,
                "podman-image:latest",
                new KubernetesPodClient.ContainerResources("500m", "1Gi", "1000m", "2Gi"),
                9500, "4Gi", "10.0.0.0/8");
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleSeatWebPodHasInitContainerWithNetAdminOnly() throws Exception {
        final Map<String, Object> pod = buildPodBody(newClient(), "python", "https://github.com/example/repo.git", 1);
        final Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
        final List<Map<String, Object>> initContainers = (List<Map<String, Object>>) spec.get("initContainers");
        assertEquals(1, initContainers.size());
        final Map<String, Object> init = initContainers.get(0);
        assertEquals("egress-lockdown", init.get("name"));

        final Map<String, Object> initSecurity = (Map<String, Object>) init.get("securityContext");
        final Map<String, Object> initCaps = (Map<String, Object>) initSecurity.get("capabilities");
        assertEquals(List.of("NET_ADMIN"), initCaps.get("add"));

        final List<Map<String, Object>> containers = (List<Map<String, Object>>) spec.get("containers");
        final Map<String, Object> mainSecurity = (Map<String, Object>) containers.get(0).get("securityContext");
        final Map<String, Object> mainCaps = (Map<String, Object>) mainSecurity.get("capabilities");
        assertFalse(((List<String>) mainCaps.get("add")).contains("NET_ADMIN"),
                "el contenedor principal (accesible por el alumno) NUNCA debe tener CAP_NET_ADMIN");

        final Object initArgs = init.get("args");
        assertTrue(initArgs.toString().contains("lockdown-egress.sh"));
        assertTrue(initArgs.toString().contains("seed-remote-git.sh"),
                "single-seat: el clonado de REMOTE_GIT_URL debe correr en el initContainer");
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiSeatCliPodSkipsGitCloneInInitContainer() throws Exception {
        final Map<String, Object> pod = buildPodBody(newClient(), "terminal-nvim", "https://github.com/example/repo.git", 4);
        final Map<String, Object> spec = (Map<String, Object>) pod.get("spec");
        final List<Map<String, Object>> initContainers = (List<Map<String, Object>>) spec.get("initContainers");
        final Map<String, Object> init = initContainers.get(0);
        assertFalse(init.get("args").toString().contains("seed-remote-git.sh"),
                "multi-asiento: no hay workspace pod-wide donde clonar, cada asiento tiene su propio home recién en runtime");
        assertTrue(init.get("args").toString().contains("lockdown-egress.sh"));

        final List<Map<String, Object>> mounts = (List<Map<String, Object>>) init.get("volumeMounts");
        assertEquals("/home", mounts.get(0).get("mountPath"));
    }

    @Test
    void conferenceLabelHelperStillWorks() {
        assertEquals(8, Sandbox.conferenceLabel("conf-uuid-1234-abcd-abcdabcdabcd").length());
    }
}
