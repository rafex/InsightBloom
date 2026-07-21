# InsightBloom

InsightBloom es una plataforma para crear y operar eventos interactivos en
vivo. Combina participación del público, moderación, visualizaciones,
encuestas, presentaciones, herramientas de trabajo y ambientes aislados de
desarrollo en una misma conferencia.

El proyecto comenzó como una nube de palabras para dudas y temas enviados por
chat. Actualmente funciona como una plataforma modular de eventos con control
de acceso, boletos, roles, capacidades configurables y servicios independientes.

## Qué permite hacer

### Eventos y acceso

- Crear eventos con nombre, identificador amigable, UUID, fecha, horario,
  ubicación y zona horaria.
- Definir el tipo de evento y las capacidades habilitadas: boletos, encuestas,
  presentación, chat, videollamada, diagramas, pizarra, notas e IDE.
- Emitir boletos con QR o UUID v4, compartirlos por correo o directamente,
  canjearlos y validarlos mediante escaneo o captura manual.
- Administrar aforo general o mapa de asientos.
- Revocar boletos con auditoría del responsable; un boleto revocado libera su
  espacio y deja de otorgar acceso. El participante puede recuperar acceso con
  otro boleto válido.
- Expirar automáticamente boletos cinco horas después del inicio del evento.

La única parte pública de un evento con acceso restringido es la visualización
de hasta cinco slides de la presentación. El resto requiere registro y boleto
válido, incluyendo las herramientas privadas y el material descargable.

### Participación y moderación

- Nubes de palabras separadas para dudas y temas usando D3.js y `d3-cloud`.
- Timeline cronológico de los mensajes asociados a cada palabra.
- Moderación automática y manual de palabras y mensajes, con censura,
  restauración y edición.
- Chat en tiempo real mediante FastAPI y WebSocket.
- Bot de asistencia con IA — Roberto — mediante un proveedor compatible con la
  API de OpenAI, como DeepSeek.

### Encuestas, presentaciones y certificados

- Crear, publicar y responder encuestas.
- Generar sugerencias de preguntas con IA cuando el proveedor está configurado.
- Emitir certificados de participación.
- Subir presentaciones como ZIP seleccionando el motor `Marp` o `Slidev`.
  Marp genera HTML tradicional; Slidev genera una SPA estática con navegación
  pública, modo presentador y sincronización en vivo.
- Mantener Marp como opción compatible para presentaciones existentes. Los ZIP
  de Slidev del MVP deben incluir un Markdown de entrada (`slides.md` o un único
  `.md`) y assets locales; no se aceptan `package.json`, plugins ni código
  ejecutable arbitrario.
- Mantener el motor de encuestas actual y planificar `survey-vue3-ui` como una
  segunda forma de crear/renderizar encuestas, sin reemplazar el flujo actual.

### Herramientas del evento

Un evento puede habilitar varias herramientas y definir una modalidad distinta
para cada una:

| Herramienta | Modalidades configurables |
|-------------|---------------------------|
| Drawio | Trabajo independiente o sólo el moderador |
| Excalidraw | Trabajo independiente o sólo el moderador |
| Etherpad | Trabajo independiente o sólo el moderador |

`INDEPENDENT` significa que cada participante trabaja en su propio espacio y no
se implementa colaboración entre participantes. `MODERATOR_ONLY` reserva la
edición al moderador y está orientado a publicar el material del moderador para
la audiencia. La configuración se persiste por herramienta, no como una opción
global del evento.

### IDE de código

Los eventos pueden ofrecer un ambiente aislado por participante:

- **Web**: code-server con editor visual en el navegador.
- **CLI**: Neovim servido mediante ttyd; puede reutilizarse entre varios
  alumnos, con espacio de trabajo aislado por usuario.
- Java 25, Python 3.12 y Node.js disponibles en las imágenes del IDE.
- Paquetes adicionales, repositorio Git remoto, memoria de JVM, acceso a
  internet y tamaño de los pools configurables por evento.
- NetworkPolicy y pods efímeros para limitar el aislamiento y consumo de
  recursos.

### Usuarios y seguridad

- Roles globales: `ADMIN`, `ORGANIZER`, `MODERATOR` y `GUEST`.
- Roles específicos por evento, como anfitrión y personal de acceso.
- Autenticación con JWT, invitados con fingerprint de dispositivo y OTP
  opcional por SMS o correo.
- Comunicación interna entre servicios protegida con `X-Internal-Auth`.
- Los secretos no se versionan en este repositorio.

## Arquitectura

```text
Navegador
   │
   ▼
Web Vue 3 + nginx ───────────────► Tools Gateway ──► Drawio / Excalidraw / Etherpad
   │                                      │
   ├── users       ──┐                    └────────► Pods de IDE por evento/usuario
   ├── ingest       ─┤
   ├── query        ├──► SQLite independiente por servicio
   ├── moderation   ┤
   ├── stats        ┤
   └── survey       ┘

Chat FastAPI + WebSocket ───────► ingest / users / proveedor de IA
Presentations Node + Marp/Slidev ► archivos, SPA y slides HTML
```

El backend Java usa arquitectura hexagonal: dominio, casos de uso, puertos y
adaptadores. Cada servicio mantiene su propia base SQLite en modo WAL. El
frontend es una SPA Vue 3 que concentra navegación, experiencia de evento y
dashboards; las reglas de acceso y permisos se validan en backend.

## Componentes principales

| Componente | Tecnología | Responsabilidad | Puerto principal |
|------------|------------|-----------------|------------------|
| `insightbloom-web` | Vue 3, Vite, nginx | SPA, dashboards, conferencia y proxy API | 80 |
| `insightbloom-users` | Java 25, Ether 9.5.5 | usuarios, autenticación, eventos, roles, boletos y acceso | 8081 |
| `insightbloom-ingest` | Java 25 | recepción y normalización de mensajes | 8082 |
| `insightbloom-query` | Java 25 | nubes y timelines | 8083 |
| `insightbloom-moderation` | Java 25 | censura y moderación | 8084 |
| `insightbloom-stats` | Java 25 | agregados y relevancia | 8085 |
| `insightbloom-survey` | Java 25 | encuestas, respuestas y certificados | 8086 |
| `insightbloom-tools-gateway` | Java 25 | sesión autenticada y proxy hacia herramientas/IDE | 8090 |
| `insightbloom-presentations` | Node.js, Express, Marp, Slidev | conversión, exportación y entrega de presentaciones | 8091 |
| `insightbloom-chat` | Python, FastAPI | chat, WebSocket y bot IA | según entorno |
| `insightbloom-telegram` | Python, FastAPI | integración de Telegram | según entorno |

## Estructura del repositorio

```text
InsightBloom/
├── backend/
│   ├── common/             # Código compartido Java
│   ├── contracts/          # Contratos y DTOs compartidos
│   ├── services/           # Microservicios Java y presentaciones Node
│   └── cli/                # CLI administrativo
├── frontend/web/           # SPA Vue 3 + Vite
├── chat/                   # Chat FastAPI + WebSocket + bot IA
├── telegram/               # Integración Telegram
├── container/              # Docker Compose y Dockerfiles principales
├── infra/docker/           # Imágenes de Etherpad e IDE
├── scripts/                # Build, ejecución, simulación y pruebas
├── .github/workflows/      # CI, tests y publicación de imágenes
├── agents/                 # Seguridad y diagnóstico operativo
├── docs/                   # Documentación legacy
├── spec-native/            # Fuente de verdad de producto y arquitectura
├── Makefile                # Build, tests y lint
└── Justfile                # Flujos de desarrollo y operación local
```

## Desarrollo local

### Requisitos

- Java 25.
- Node.js y npm.
- Python 3.12 para chat y Telegram.
- Docker Compose para ejecutar el stack contenerizado.
- `just` y `make` para los flujos del proyecto.

### Instalación

```bash
make install
```

### Modos de ejecución

```bash
# Compilar y levantar servicios Java + frontend en modo desarrollo
just dev

# Levantar todo el stack con Docker Compose en primer plano
just container-dev

# Levantar todo el stack en segundo plano
just up

# Ver logs del stack o de un servicio
just logs
just logs insightbloom-users

# Detener contenedores; down-clean también elimina volúmenes SQLite
just down
just down-clean
```

El chat requiere `DEEPSEEK_API_KEY` cuando se ejecuta con Docker Compose o con
`just chat-dev`. Las demás credenciales e integraciones se configuran mediante
variables de entorno y nunca deben escribirse en archivos versionados.

### Build, tests y lint

```bash
# Pipeline local completo
just ci

# Comandos equivalentes por área
make build
make services-test
make web-test
make lint

# Frontend
npm --prefix frontend/web run typecheck
npm --prefix frontend/web run test
npm --prefix frontend/web run build

# Backend
./mvnw -f backend/services/pom.xml test
```

Para crear o actualizar usuarios administrativos se usa el CLI, no un usuario
sembrado automáticamente:

```bash
just create-user -- --username <usuario> --password <clave> --role ORGANIZER
```

## CI, GHCR y despliegue

Este repositorio controla el código y el CI. GitHub Actions ejecuta validaciones
de backend, frontend y chat, y publica las imágenes correspondientes en:

```text
ghcr.io/rafex/insightbloom-*
```

Los workflows `publish-*.yml` usan filtros por rutas y publican, entre otros,
los tags `latest`, SHA y `build-<run_id>`.

El CD no vive aquí. El despliegue declarativo está en:

```text
/Users/rafex/repository/github/rafex/InsightBloom-gitops
```

FluxCD en `k3s-server1` observa GHCR, actualiza el `HelmRelease` en el
repositorio GitOps y reconcilia el namespace `insightbloom`. Para verificar el
rollout:

```bash
export KUBECONFIG=~/.kube/config_k3s_server1
kubectl get pods -n insightbloom \
  -o custom-columns=NAME:.metadata.name,IMAGE:.spec.containers[0].image
```

Los cambios de Helm, valores, secrets o política de Flux deben hacerse en
`InsightBloom-gitops`, no mediante un deploy manual desde este repositorio.

## Documentación de referencia

La fuente de verdad del contexto del proyecto está en `spec-native/`:

| Tema | Documento |
|------|-----------|
| Producto y capacidades | [`spec-native/PRODUCT.md`](./spec-native/PRODUCT.md) |
| Arquitectura | [`spec-native/ARCHITECTURE.md`](./spec-native/ARCHITECTURE.md) |
| Stack y restricciones | [`spec-native/STACK.md`](./spec-native/STACK.md) |
| Comandos | [`spec-native/COMMANDS.md`](./spec-native/COMMANDS.md) |
| Roles y permisos | [`spec-native/ROLES.md`](./spec-native/ROLES.md) |
| CI | [`spec-native/pipelines/CI.md`](./spec-native/pipelines/CI.md) |
| CD y GitOps | [`spec-native/pipelines/CD.md`](./spec-native/pipelines/CD.md) |
| Boletos y acceso | [`spec-native/tasks/ticket-access-plan.md`](./spec-native/tasks/ticket-access-plan.md) |
| Modos de herramientas visuales | [`spec-native/tasks/event-canvas-access-modes-plan.md`](./spec-native/tasks/event-canvas-access-modes-plan.md) |
| SurveyJS | [`spec-native/tasks/surveyjs-vue3-ui-plan.md`](./spec-native/tasks/surveyjs-vue3-ui-plan.md) |
| Seguridad | [`agents/SECURITY.md`](./agents/SECURITY.md) |
| Diagnóstico | [`agents/DIAGNOSE.md`](./agents/DIAGNOSE.md) |

Para agentes y colaboradores, leer primero [`AGENTS.md`](./AGENTS.md) y luego
[`spec-native/README.md`](./spec-native/README.md).

## Licencia

Ver [`LICENSE`](./LICENSE).
