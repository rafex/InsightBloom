# Auditoría de seguridad de endpoints, secretos y autorización

**Fecha:** 2026-07-25  
**Alcance:** `InsightBloom` y el despliegue activo de `InsightBloom-gitops` en
K3s.  
**Tipo:** revisión de solo lectura; no se modificaron archivos, imágenes,
secretos ni recursos del clúster durante la auditoría.

## Resultado ejecutivo

La plataforma tiene controles importantes funcionando, pero la auditoría no
queda aprobada todavía. Se encontraron exposiciones de datos y riesgos de
aislamiento que deben atenderse antes de considerar cerrado el control de
seguridad.

| Prioridad | Hallazgo | Estado |
|---|---|---|
| Alta | El agregado completo de una conferencia responde sin autenticación | Abierto |
| Alta | Se aceptan tokens de sesión por query string en demasiadas operaciones | Abierto |
| Alta | El aislamiento de homes del CLI multi-asiento no coincide con lo esperado en producción | Abierto |
| Alta | Existe un secreto interno con valor predeterminado de desarrollo | Abierto |
| Media | Las preguntas inactivas pueden devolver respuestas de referencia | Abierto |
| Media | Las nubes públicas no comprueban visibilidad y estado del evento | Abierto |
| Baja | Hay descargas autenticadas mediante token en la URL | Abierto |

## Hallazgos abiertos

### AUD-01 — Agregado completo de conferencia sin autenticación

**Prioridad:** Alta  
**Evidencia dinámica:**

```text
GET /api/users/api/v1/conferences/{uuid}       -> 200 sin credenciales
GET /api/users/api/v1/conferences/by-friendly/{friendlyId} -> 200 sin credenciales
```

Las respuestas incluyen más información que la necesaria para la cartelera
pública, entre ella configuración de sandboxes, paquetes adicionales,
repositorio Git, motor de certificados, XML de Drawio, escena de Excalidraw y
configuración operativa del evento.

**Referencias:**

- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java:665`
- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java:676`
- El DTO reducido `PublicConferenceView` comienza en la línea 687 del mismo archivo.

**Corrección propuesta:**

1. Proteger las rutas por UUID e ID amigable con autenticación, o hacer que
   devuelvan únicamente `PublicConferenceView`.
2. Mantener una ruta separada para el agregado completo del dashboard.
3. Añadir una prueba de contrato que falle si la respuesta pública contiene
   campos de sandbox, configuración interna, XML o escenas completas.

### AUD-02 — Token de sesión en query string

**Prioridad:** Alta  
**Evidencia:** `ConferenceHandler.extractToken` usa `Authorization: Bearer`
cuando existe, pero acepta `ib_token` como fallback. Este helper se usa también
en operaciones de modificación de estado: boletos, roles, sandboxes,
moderación y configuración del evento.

**Referencia:**

- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/ConferenceHandler.java:2562`

**Riesgo:** los tokens pueden terminar en historial del navegador, logs de
proxy, herramientas de desarrollo, capturas o encabezados `Referer`.

**Corrección propuesta:**

- Rechazar `ib_token` en `POST`, `PUT`, `PATCH` y `DELETE`.
- Para SSE usar un ticket de stream de un solo uso, corto y con expiración.
- Para iframes usar una cookie de sesión limitada al host y con expiración.
- Aplicar `Referrer-Policy: no-referrer` en las páginas que aún deban usar un
  token temporal en URL.
- Revisar y rotar tokens que hayan aparecido en logs o historiales de
  producción.

### AUD-03 — Aislamiento insuficiente del CLI multi-asiento

**Prioridad:** Alta  
**Evidencia dinámica:**

- El Pod CLI usa un volumen `/home` compartido entre asientos.
- En el Pod inspeccionado, el home del alumno tenía permisos `0755` en lugar de
  `0750`.
- El Pod probado tenía un solo alumno activo, por lo que no se confirmó una
  lectura cruzada de archivos; aun así, la protección esperada no estaba
  aplicada en ese Pod.
- La salida directa a Internet fue bloqueada por la NetworkPolicy; DNS sí
  resolvió, pero una conexión HTTPS externa fue rechazada.

**Referencias:**

- `infra/docker/sandbox-agent.py:160-175`
- `infra/docker/sandbox-agent.py:178-203`
- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/outbound/kubernetes/KubernetesPodClient.java:611`
- `InsightBloom-gitops/infrastructure/charts/insightbloom/templates/sandbox-networkpolicy.yaml:1`

**Corrección propuesta:**

1. Recrear los Pods CLI con la imagen que contiene el `chmod 0750`.
2. Verificar en vivo dos asientos del mismo Pod y de Pods distintos.
3. Confirmar que un alumno no puede listar, atravesar ni leer el home de otro.
4. Evaluar separar el workspace por Pod o por volumen independiente como
   defensa adicional.
5. Mantener la NetworkPolicy de egress por defecto denegado y permitir solo el
   proxy interno cuando el evento lo habilite.

### AUD-04 — Secreto interno con fallback de desarrollo

**Prioridad:** Alta  
**Evidencia:**

```java
System.getenv().getOrDefault(
    "SANDBOX_INCIDENT_REPORT_KEY",
    "dev-only-change-me"
)
```

**Referencia:**

- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/bootstrap/UsersApplication.java:233`

El secreto de incidentes tiene privilegios reducidos, pero un valor conocido
permite falsificar reportes internos si el Secret de Kubernetes no se monta.

**Corrección propuesta:** eliminar el valor predeterminado. El servicio debe
fallar al iniciar o rechazar el endpoint interno mientras la variable no esté
configurada.

### AUD-05 — Preguntas inactivas y respuestas de referencia sin rol

**Prioridad:** Media  
**Evidencia:**

```text
GET /api/survey/api/v1/conferences/{uuid}/survey/questions?onlyActive=false
    -> 200 sin credenciales
```

La rama `onlyActive=false` devuelve el modelo completo de `SurveyQuestion`, que
incluye `referenceAnswer`, estado activo y metadatos.

**Referencias:**

- `backend/services/insightbloom-survey/src/main/java/dev/rafex/insightbloom/survey/adapters/inbound/http/handlers/SurveyHandler.java:209-216`
- `backend/services/insightbloom-survey/src/main/java/dev/rafex/insightbloom/survey/domain/model/SurveyQuestion.java:75-81`

**Corrección propuesta:** exigir permiso de gestión de encuesta cuando se
soliciten preguntas inactivas, o devolver un DTO público que elimine
`referenceAnswer`, estado y metadatos internos.

### AUD-06 — Nubes públicas sin comprobación explícita de visibilidad

**Prioridad:** Media  
**Evidencia:** estas rutas responden sin autenticación:

- `/api/query/api/v1/conferences/{id}/cloud/doubts`
- `/api/query/api/v1/conferences/{id}/cloud/topics`
- `/api/query/api/v1/conferences/{id}/words/{word}/timeline`

**Referencia:**

- `backend/services/insightbloom-query/src/main/java/dev/rafex/insightbloom/query/adapters/inbound/http/handlers/ConferenceQueryHandler.java:48-55`

Son necesarias para eventos públicos activos, pero el handler no muestra una
comprobación local de visibilidad, estado o expiración. Debe validarse que un
evento privado, desactivado o expirado no pueda consultarse solo con un UUID.

**Corrección propuesta:** centralizar una política de lectura pública que
compruebe `visibility`, `status` y expiración antes de devolver la nube o abrir
el SSE.

### AUD-07 — Token en URL para descargar workspaces

**Prioridad:** Baja  
**Evidencia:** la descarga usa un token autocontenido en query string:

- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/WorkspaceDownloadHandler.java:15-18`
- `backend/services/insightbloom-users/src/main/java/dev/rafex/insightbloom/users/adapters/inbound/http/handlers/WorkspaceDownloadHandler.java:43-49`

La funcionalidad es deliberada porque el navegador abre la descarga como
navegación directa, pero debe limitarse a tokens de un solo uso y TTL corto.

## Controles verificados correctamente

- IDE, Etherpad, Drawio y Excalidraw responden `401` sin sesión.
- Los servicios Kubernetes son `ClusterIP`; no se encontraron servicios
  directos `LoadBalancer` o `NodePort` para estos componentes.
- Certificados, boletos, roles, moderación, resultados, sandboxes y
  configuración administrativa rechazaron solicitudes sin credenciales.
- Las estadísticas exigen token y rol de organizador o administrador en
  `backend/services/insightbloom-stats/src/main/java/dev/rafex/insightbloom/stats/adapters/inbound/http/handlers/StatsHandler.java:23-69`.
- Los Pods de sandbox usan `default` y no montan token de ServiceAccount.
- La política de egress por defecto permite DNS, pero no salida directa a
  Internet.
- Los secretos de GitOps están cifrados con SOPS. `helpers/check-plaintext.sh`
  terminó con código 0 y no encontró valores plaintext.
- No se encontraron claves privadas, tokens de GitHub, claves AWS ni tokens de
  Slack en los archivos rastreados. Los falsos positivos detectados pertenecen
  a nombres de paquetes en `package-lock.json`.

## Limitaciones de la auditoría

- No se descifraron secretos ni se imprimieron valores sensibles.
- El escáner `gitleaks`, `trufflehog` y `detect-secrets` no estaba instalado.
- El auditor auxiliar de secretos requiere un directorio temporal de secretos
  descifrados y no se ejecutó para evitar exponer material confidencial.
- Las pruebas dinámicas sin credenciales verifican exposición externa, pero no
  sustituyen una matriz completa de pruebas con usuarios reales de cada rol.
- El aislamiento cruzado del CLI requiere dos asientos activos para completar
  la prueba; el Pod inspeccionado tenía uno.

## Plan de remediación

- [ ] Corregir `AUD-01` y añadir pruebas de DTO público.
- [ ] Corregir `AUD-02` con tickets temporales para SSE/iframes.
- [ ] Recrear Pods CLI y cerrar `AUD-03` con pruebas entre dos alumnos.
- [ ] Eliminar el fallback `dev-only-change-me` de `AUD-04`.
- [ ] Proteger `onlyActive=false` y cerrar `AUD-05`.
- [ ] Aplicar política de visibilidad, estado y expiración a `AUD-06`.
- [ ] Convertir la descarga de workspace en token de un solo uso para cerrar
  `AUD-07`.
- [ ] Incorporar pruebas automatizadas de endpoint sin token, usuario normal y
  cada rol autorizado.

