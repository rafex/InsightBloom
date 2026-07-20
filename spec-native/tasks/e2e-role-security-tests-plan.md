# Plan: Suite E2E de navegador (Selenium/Playwright) por rol

## Estado: draft — pendiente de aprobación

## Contexto

El 2026-07-19/20 se hizo una auditoría de seguridad (secretos, autenticación, autorización,
boletos) y se corrigieron 10 hallazgos, todos del tipo BOLA/IDOR (un organizador podía operar
sobre conferencias ajenas), exposición de PII sin auth, spoofing de autor en moderación, y
condiciones de carrera en boletos/check-in. Los fixes se verificaron con `mvn test` (unitarios,
con mocks) y con validación manual del usuario — pero **no hay ningún test automatizado que
maneje un navegador real, inicie sesión como cada rol, y confirme que la UI (no solo la API)
respeta esos límites**.

El usuario pidió pensar un plan para:
1. Tests estilo Selenium (navegador real) que cubran los distintos roles.
2. Un usuario/contraseña dedicado a este propósito por rol, versionado en el repo GitOps
   (`InsightBloom-gitops`), no en el repo de aplicación.

Este documento es el plan — no se implementa nada hasta que se apruebe.

---

## 0. Decisión abierta: ¿Selenium o Playwright?

El pedido dice "tipo selenium", pero antes de implementar conviene confirmar la herramienta:

| | Selenium (+ pytest, ya hay `test/` en Python) | Playwright |
|---|---|---|
| Integración con el repo | Encaja con `test/test_e2e_*.py` existentes (pytest + httpx) | Requiere nuevo runner (Node o Python) |
| Velocidad / flakiness | Más lento, más flaky con esperas explícitas | Auto-wait nativo, más estable |
| Multi-rol / multi-sesión | Requiere un WebDriver por sesión, más pesado | `browser.newContext()` aislado y barato, ideal para 4 roles en paralelo |
| Trazas para debug | Screenshots manuales | Trace viewer, video, screenshot automático en falla |
| Stack del frontend | El frontend es Vue 3 + Vite, sin atadura a un runner en particular | ídem |

**Recomendación:** Playwright (Python, para reusar `test/requirements.txt` y los fixtures de
`conftest.py` ya existentes) — es el sucesor funcional de Selenium para este caso de uso y reduce
flakiness en la CI. Si el usuario prefiere Selenium real (WebDriver), el plan de abajo es igual de
válido cambiando solo la capa de driver (sección 3). **Este plan asume Playwright salvo que se
indique lo contrario al aprobar.**

---

## 1. Objetivo y alcance de los tests

Cubrir, vía navegador real (no llamadas HTTP directas), los 10 hallazgos corregidos + la matriz
de roles de [`ROLES.md`](../ROLES.md). Casos concretos por hallazgo:

| # | Caso E2E | Rol A (dueño) | Rol B (ajeno) | Resultado esperado |
|---|---|---|---|---|
| 1 | Organizador B intenta crear/editar/purgar preguntas de encuesta de la conferencia de A | organizer-a | organizer-b | 403 en UI, sin cambios visibles |
| 2 | Ver perfil de otro usuario sin ser organizador/admin/dueño | attendee-a | attendee-b | 403, perfil no se muestra |
| 3 | Organizador B intenta cambiar config de sandbox/device-access de la conferencia de A | organizer-a | organizer-b | 403 en dashboard de config |
| 4 | Organizador B intenta ver archivos de sandbox de un alumno de la conferencia de A | organizer-a | organizer-b | 403 en el visor de archivos |
| 5 | Moderador de B censura/edita contenido de la conferencia de A; se verifica autor real (no falsificable desde DevTools) | organizer-a | moderator-b | 403; y si se permite, el autor registrado es el subject del token, no uno inventado |
| 6 | Organizador B intenta hacer check-in de un boleto de la conferencia de A | organizer-a | organizer-b | 403 en el scanner de check-in |
| 7 | Un mismo asistente hace doble-click en "Reservar" (2 pestañas a la vez) | attendee | — | Solo un boleto, aforo descontado una sola vez |
| 8 | Cancelar el mismo boleto dos veces en paralelo | attendee | — | Aforo no queda negativo/duplicado |
| 9 | Organizador (no admin) intenta editar `CertificateSettings` (config global) | organizer | — | UI no permite guardar / 403 |
| 10 | Escanear el mismo QR de check-in dos veces rápido (2 pestañas) | organizer | — | Solo un check-in registrado, el segundo ve "ya ingresado" |

Además, cobertura base de la matriz de `ROLES.md` (login, dashboard visible según rol, accesos
denegados en nav) para que la suite sirva como regresión general de permisos, no solo de estos 10
hallazgos puntuales.

**Fuera de alcance de este plan:** performance/carga, accesibilidad, cross-browser matrix (se
corre solo en Chromium headless para CI; Firefox/WebKit quedan como extensión futura opcional).

---

## 2. Usuarios de prueba por rol

Se necesitan usuarios **dedicados exclusivamente a este propósito**, distintos de cualquier dato
real, para no contaminar la base de producción ni depender de cuentas que alguien pueda borrar/
editar manualmente.

### 2.1 Catálogo de usuarios

| Usuario | Rol(es) | Uso en los tests |
|---|---|---|
| `e2e-admin` | ADMIN | Admin-only checks (certificados, gestión de usuarios) |
| `e2e-organizer-a` | ORGANIZER | "Dueño" de la conferencia de prueba A |
| `e2e-organizer-b` | ORGANIZER | "Ajeno" — intenta operar sobre recursos de A (casos BOLA) |
| `e2e-moderator-a` | MODERATOR | Moderación dentro de la conferencia de A |
| `e2e-attendee-a` / `e2e-attendee-b` | (registro público, ATTENDEE) | Casos de boletos/encuestas/perfil — **no** se crean vía CLI admin, se registran en el test mismo (o se re-crean si ya existen) porque el registro público siempre asigna ATTENDEE |

Nota: según `ROLES.md`, ADMIN/ORGANIZER/MODERATOR solo pueden crearse vía CLI admin — no hay
registro público para esos roles, así que estos sí van al catálogo de credenciales fijas de abajo.
Los `attendee` pueden auto-registrarse dentro del test (no necesitan secreto persistente), pero se
documentan igual para trazabilidad.

### 2.2 Convención de credenciales

- Prefijo `e2e-` en el username, para que sea grep-eable y nunca colisione con un usuario real.
- Contraseñas generadas aleatoriamente (no reutilizar ninguna contraseña real ni de otro
  ambiente), guardadas **solo** en el secreto SOPS — nunca en texto plano en el repo de app ni en
  logs de CI.
- Email de contacto: no usar direcciones reales (`e2e-admin@e2e.insightbloom.invalid`) para que un
  eventual envío de correo (verificación, notificaciones) no le llegue a nadie.

### 2.3 Dónde viven las credenciales: `InsightBloom-gitops`

Siguiendo el patrón ya establecido en `infrastructure/secrets/` (SOPS + Age, ver
`infrastructure/secrets/README.md`):

- Nuevo archivo: `infrastructure/secrets/e2e-test-users.yaml`, cifrado igual que los demás
  (`sops --encrypt --config .sops.yaml`).
- Claves en `data:`: `admin-username`, `admin-password`, `organizer-a-username`,
  `organizer-a-password`, `organizer-b-username`, `organizer-b-password`,
  `moderator-a-username`, `moderator-a-password` (8 claves, 4 pares usuario/contraseña).
- Se agrega una fila a la tabla de equivalencias de `infrastructure/secrets/README.md` mapeando
  cada clave a un secret de GitHub Actions del repo `InsightBloom` (ej. `E2E_ADMIN_PASSWORD`,
  etc.), igual que ya se hace con `PASSWORD_ADMIN_USER`.
- El Secret de Kubernetes resultante (`e2e-test-users`) se monta como variables de entorno **solo**
  en el Job de CI que corre la suite E2E (no en ningún Deployment de aplicación) — su único
  consumidor es el runner de tests.

### 2.4 Cómo se crean/mantienen los usuarios reales en el cluster

- Reusar `scripts/run/k3s-create-user.sh` (ya soporta upsert idempotente por `username`) para
  crear/actualizar `e2e-admin`, `e2e-organizer-a`, `e2e-organizer-b`, `e2e-moderator-a` contra el
  ambiente donde corra la suite.
- Nuevo script wrapper `scripts/run/seed-e2e-users.sh` que:
  1. Lee las credenciales desde variables de entorno (inyectadas desde el Secret de GitOps en CI,
     o desde `sops -d` en un run manual).
  2. Llama a `k3s-create-user.sh` una vez por usuario con su rol correspondiente.
  3. Es idempotente — correrlo de nuevo solo resetea la contraseña/rol, no duplica usuarios.
- Se corre como paso previo (`pre-suite`) de la suite E2E, nunca manualmente contra producción sin
  confirmar el ambiente.

### 2.5 Aislamiento de datos: conferencia de prueba dedicada

Para que los tests no dependan de conferencias reales (que alguien puede borrar/editar) ni las
ensucien:

- La suite crea (o reutiliza si ya existe, por `friendlyId` fijo `e2e-test-conf`) su propia
  conferencia al arrancar, vía `e2e-organizer-a`.
- Al final de la corrida, un paso de limpieza cancela boletos de prueba y resetea el estado
  reservable (no borra la conferencia entera, para evitar recrear friendlyId/QRs entre corridas).
- Nunca se apunta la suite a una conferencia real de un evento en curso.

---

## 3. Estructura del proyecto de tests

Nuevo directorio en el repo `InsightBloom` (no en gitops — el código de test es público/versionado
normalmente, solo las credenciales van a gitops):

```
test/
  e2e-ui/
    conftest.py              # fixtures: browser, contexts por rol, credenciales desde env
    pages/                   # Page Object Model
      login_page.py
      dashboard_page.py
      survey_manage_page.py
      sandbox_config_page.py
      check_in_scanner_page.py
      certificate_settings_page.py
    fixtures/
      roles.py               # e2e-admin / organizer-a / organizer-b / moderator-a como fixtures
      conference.py          # crea/reutiliza la conferencia e2e-test-conf
    test_bola_survey.py      # hallazgo 1
    test_bola_profile.py     # hallazgo 2
    test_bola_sandbox_config.py   # hallazgo 3
    test_bola_sandbox_files.py    # hallazgo 4
    test_moderation_ownership_and_author.py  # hallazgo 5
    test_bola_checkin.py     # hallazgo 6
    test_reservation_race.py # hallazgos 7, 8, 10 (usa 2 contexts en paralelo)
    test_certificate_admin_only.py  # hallazgo 9
    test_roles_matrix.py     # regresión general de ROLES.md
  requirements.txt           # playwright, pytest-playwright, pytest-asyncio si aplica
  playwright.config / conftest fixtures de instalación de browsers
```

Reutiliza el patrón ya existente de `test/conftest.py` (fixture `compose_up` o equivalente) donde
tenga sentido, pero esta suite corre contra un ambiente **desplegado** (staging o el propio k3s),
no contra `docker compose` local, porque necesita el flujo real de Ingress/TLS/dashboard servido.

---

## 4. Integración en CI

- Nuevo workflow `.github/workflows/e2e-ui.yml`.
- **No** corre en cada push (sería lento y potencialmente flaky sobre un cluster compartido) —
  dispara:
  - Manualmente (`workflow_dispatch`).
  - Nightly (`schedule`), como smoke test de regresión de permisos.
  - Opcional a futuro: en PRs que tocan archivos de `adapters/inbound/http/handlers/**` (los
    puntos exactos donde vivieron los 10 hallazgos), para atrapar regresiones de autorización
    antes del merge.
- El job:
  1. Descifra `e2e-test-users.yaml` con la Age key de CI (mismo mecanismo que ya usa GitOps para
     desplegar, revisar si conviene una key de solo-lectura separada para este job).
  2. Corre `seed-e2e-users.sh` contra el ambiente objetivo.
  3. Instala Playwright + navegadores (`playwright install --with-deps chromium`).
  4. Corre `pytest test/e2e-ui/ -m e2e_ui`.
  5. Sube trace/video de los tests fallidos como artifact.

---

## 5. Fases de implementación

1. **Fase 0 — Aprobación:** confirmar Playwright vs Selenium, y contra qué ambiente corre
   (¿staging dedicado o el mismo cluster de producción con datos de prueba aislados?).
2. **Fase 1 — Credenciales:** crear `infrastructure/secrets/e2e-test-users.yaml` en
   `InsightBloom-gitops`, actualizar `infrastructure/secrets/README.md`, agregar los GitHub
   Secrets equivalentes.
3. **Fase 2 — Seed:** `scripts/run/seed-e2e-users.sh` + verificación manual de que los 4 usuarios
   quedan creados y loguean.
4. **Fase 3 — Esqueleto Playwright:** `test/e2e-ui/` con `conftest.py`, fixtures de rol, Page
   Objects mínimos (login, dashboard).
5. **Fase 4 — Casos BOLA (hallazgos 1-6):** un test por hallazgo, siguiendo la tabla de la
   sección 1.
6. **Fase 5 — Casos de carrera (hallazgos 7, 8, 10):** tests con 2 `BrowserContext` disparando la
   acción en paralelo (`asyncio.gather` o threads), assertions sobre el estado final (no sobre el
   orden de respuestas).
7. **Fase 6 — Admin-only (hallazgo 9) + regresión de matriz de roles.**
8. **Fase 7 — CI:** workflow `e2e-ui.yml`, nightly + manual.

---

## 6. Riesgos / cosas a decidir antes de implementar

- **Ambiente de destino:** correr esto contra el cluster real (aunque sea con datos aislados)
  implica que un bug en el seed o en la limpieza puede dejar basura visible para usuarios reales.
  Alternativa más segura: un namespace/ambiente "staging" separado — a confirmar con el usuario si
  existe o vale la pena crearlo.
- **Rotación de contraseñas:** las contraseñas de estos 4 usuarios quedan también en GitHub
  Actions Secrets — mismo nivel de exposición que `PASSWORD_ADMIN_USER` hoy, aceptable si se seedea
  solo contra el ambiente de test, pero conviene rotarlas si se sospecha filtración, igual que
  cualquier otro secreto de la tabla.
- **Flakiness de UI real:** a diferencia de los tests actuales (API vía httpx), un test de
  navegador depende de que el frontend no cambie selectores sin querer — se recomienda usar
  `data-testid` en los elementos clave que toquen estos flujos, no CSS classes, para que la suite
  no se rompa con cambios de estilo.
- **Emails de invitados/otp:** si algún flujo probado dispara envío de email/SMS real (Zoho/Twilio
  configurados), hay que mockear o usar un proveedor sandbox — no hay que gastar cuota real de
  esos servicios corriendo la suite nightly.
