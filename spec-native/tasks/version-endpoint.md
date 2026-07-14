# Plan: Endpoint `/version` en todos los servicios + frontend

## Objetivo

Cada servicio expone `GET /version` retornando:
```json
{"service":"insightbloom-users","version":"0.1.0","gitSha":"3288a5d","buildTime":"2026-07-14T14:30:00Z"}
```

Frontend muestra la versión como pill discreto en el header. No rompe nada. Endpoint público sin auth, como `/health`.

---

## Servicios a modificar (11 total)

| # | Servicio | Entry point | Cambio |
|---|----------|-------------|--------|
| 1-6 | users, ingest, moderation, query, stats, survey | `*Application.java` | +1 línea (`routes.add`) |
| 7 | tools-gateway | `AuthGateHandler.java` | +12 líneas (handler inline) |
| 8 | presentations | (verificar entry) | +1 línea |
| 9-10 | chat, telegram | `main.py` | +8 líneas c/u |
| 11 | web (frontend) | `AppHeader.vue` | +10 líneas |

---

## Paso 1 — Maven: `git-commit-id-maven-plugin`

**Archivo:** `pom.xml`

```xml
<plugin>
    <groupId>io.github.git-commit-id</groupId>
    <artifactId>git-commit-id-maven-plugin</artifactId>
    <version>9.0.1</version>
    <executions>
        <execution>
            <id>get-the-git-infos</id>
            <goals><goal>revision</goal></goals>
            <phase>initialize</phase>
        </execution>
    </executions>
    <configuration>
        <generateGitPropertiesFile>true</generateGitPropertiesFile>
        <generateGitPropertiesFilename>${project.build.outputDirectory}/git.properties</generateGitPropertiesFilename>
        <includeOnlyProperties>
            <includeOnlyProperty>git.commit.id</includeOnlyProperty>
            <includeOnlyProperty>git.commit.id.abbrev</includeOnlyProperty>
            <includeOnlyProperty>git.build.time</includeOnlyProperty>
        </includeOnlyProperties>
        <commitIdGenerationMode>full</commitIdGenerationMode>
    </configuration>
</plugin>
```

---

## Paso 2 — Archivo nuevo: `VersionHandler.java`

**Archivo:** `backend/common/src/main/java/dev/rafex/insightbloom/common/http/VersionHandler.java`

```java
package dev.rafex.insightbloom.common.http;

import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public class VersionHandler extends BaseResourceHandler {

    private final String serviceName;
    private final Map<String, String> cachedResponse;

    public VersionHandler(final String serviceName) {
        this.serviceName = serviceName;
        this.cachedResponse = buildResponse();
    }

    @Override
    public boolean handle(final JettyHttpExchange jx) throws Exception {
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
```

---

## Paso 3 — Registrar en cada Application Java (7 servicios)

```java
routes.add("/version", new VersionHandler("insightbloom-users"));
```

| Archivo | serviceName |
|---------|-------------|
| `UsersApplication.java` (L240) | `insightbloom-users` |
| `IngestApplication.java` | `insightbloom-ingest` |
| `ModerationApplication.java` | `insightbloom-moderation` |
| `QueryApplication.java` | `insightbloom-query` |
| `StatsApplication.java` | `insightbloom-stats` |
| `SurveyApplication.java` | `insightbloom-survey` |
| `PresentationsApplication.java` (verificar) | `insightbloom-presentations` |

---

## Paso 4 — Tools-gateway: handler inline en `AuthGateHandler.java` (L87)

```java
if ("/version".equals(request.getHttpURI().getPath())) {
    final Properties gp = new Properties();
    try (var is = getClass().getClassLoader().getResourceAsStream("git.properties")) {
        if (is != null) gp.load(is);
    }
    final String json = "{\"service\":\"tools-gateway\""
        + ",\"version\":\"" + System.getenv().getOrDefault("APP_VERSION", "dev") + "\""
        + ",\"gitSha\":\"" + gp.getProperty("git.commit.id.abbrev", "unknown") + "\""
        + ",\"buildTime\":\"" + gp.getProperty("git.build.time", "unknown") + "\"}";
    writeSimpleResponse(request, response, callback, 200, json);
    return true;
}
```

---

## Paso 5 — Python: `/version` en chat y telegram

**`chat/main.py`** y **`telegram/main.py`** (después de `/health`):
```python
import os

@app.get("/version")
async def version():
    return {"service": "chat", "version": os.getenv("APP_VERSION", "dev"),
            "gitSha": os.getenv("GIT_SHA", "unknown")}
```

---

## Paso 6 — Dockerfiles

**`container/backend/java/Dockerfile`** (después de `ARG VERSION`):
```dockerfile
ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}
```

**`container/frontend/Dockerfile`** (antes de `RUN npm run build`):
```dockerfile
ARG VITE_APP_VERSION=dev
ARG VITE_GIT_SHA=unknown
ENV VITE_APP_VERSION=${VITE_APP_VERSION}
ENV VITE_GIT_SHA=${VITE_GIT_SHA}
```

---

## Paso 7 — Workflow CI: build-args

En `publish_container.yml`, cada `docker/build-push-action`:

**Java (8 jobs):** `APP_VERSION=${{ steps.image_tag.outputs.value }}` + `GIT_SHA=${{ github.sha }}`

**Frontend:** `VITE_APP_VERSION=...` + `VITE_GIT_SHA=...`

**Python (2 jobs):** `APP_VERSION=...` + `GIT_SHA=...`

---

## Paso 8 — Frontend: pill en `AppHeader.vue`

```pug
.version-tag(v-if="version") v{{ version }}{{ gitSha ? ' · ' + gitSha : '' }}
```

```ts
const version = import.meta.env.VITE_APP_VERSION || 'dev'
const gitSha = import.meta.env.VITE_GIT_SHA?.slice(0, 7) || ''
```

```css
.version-tag {
  font-size: 0.7rem; color: #a5b4fc;
  background: rgba(255,255,255,0.08); padding: 2px 8px;
  border-radius: 9999px; margin-left: auto;
  white-space: nowrap; opacity: 0.7;
}
```

---

## Impacto: ~15 archivos, ~160 líneas

## Verificación: `mvn clean package -DskipTests && find backend -name "git.properties"` + `curl http://localhost:8081/version`
