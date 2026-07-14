package dev.rafex.insightbloom.common.http;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class VersionHandler extends BaseResourceHandler {

    private final String serviceName;
    private final Map<String, String> cachedResponse;

    public VersionHandler(final String serviceName) {
        this.serviceName = serviceName;
        this.cachedResponse = buildResponse();
    }

    @Override
    protected String basePath() {
        return "/version";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", Set.of("GET")));
    }

    @Override
    public boolean get(final HttpExchange x) {
        final var jx = asJetty(x);
        sendOk(jx, 200, cachedResponse);
        return true;
    }

    private Map<String, String> buildResponse() {
        final Map<String, String> info = new LinkedHashMap<>();
        info.put("service", serviceName);
        info.put("version", System.getenv().getOrDefault("APP_VERSION", "dev"));
        final Properties gp = loadGitProperties();
        info.put("gitSha", gp.getProperty("git.commit.id.abbrev", "unknown"));
        info.put("buildTime", gp.getProperty("git.build.time", "unknown"));
        return info;
    }

    private Properties loadGitProperties() {
        final Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) props.load(is);
        } catch (final IOException ignored) { }
        return props;
    }
}
