# SPEC: Soporte aditivo de Slidev para presentaciones

## Initiative

slidev-presentations

## Status

active

## Summary

Agregar Slidev como segundo motor de presentaciones de InsightBloom, sin
reemplazar ni convertir las presentaciones existentes de Marp. Quien sube una
presentación selecciona explícitamente `MARP` o `SLIDEV` en el formulario de
carga; el backend usa esa selección para procesar el ZIP con el adaptador
correspondiente.

Slidev se integrará dentro del microservicio existente
`insightbloom-presentations`, reutilizando autenticación, preview, visor
público, panel del moderador, sincronización en vivo y almacenamiento por
conferencia. El alcance operativo conserva el ZIP fuente controlado y añade un
ZIP FAT precompilado experimental; ninguno permite instalar dependencias del
usuario durante la carga.

## Implementación vigente del MVP — 2026-07-21

- El formulario envía `presentationProvider=MARP|SLIDEV` junto con el ZIP. Las
  cargas antiguas sin el campo siguen interpretándose como Marp para mantener
  compatibilidad.
- El upload está limitado a 100 MiB comprimidos, 250 MiB descomprimidos y
  1000 entradas; traversal, symlinks, configuraciones de build, scripts,
  componentes Vue y otras extensiones no permitidas se rechazan antes del
  reemplazo del artefacto activo.
- El formato operativo del ZIP está documentado en
  [`workflows/SLIDEV-PACKAGING.md`](../../workflows/SLIDEV-PACKAGING.md):
  `slides.md` más assets declarativos. No se sube `dist/`, `node_modules`, un
  proyecto npm completo ni el resultado compilado localmente.
- El formato `slidev-artifact.json` + `dist/` se detecta automáticamente como
  `presentationFormat=fat`. Requiere `SLIDEV_FAT_ENABLED=true`, auditoría
  estructural/estática y hashes; los warnings sólo se permiten explícitamente
  con `SLIDEV_FAT_ALLOW_WARNINGS=true`.
- Slidev se construye con `@slidev/cli@52.18.0`, tema default fijado y una
  base pública por conferencia bajo
  `/api/presentations/api/v1/conferences/{uuid}/presentation/`. Esa base debe
  incluir el prefijo del proxy frontend; usar la ruta interna `/api/v1/...`
  rompe la carga de los assets JavaScript/CSS. El artefacto activo queda descrito por
  `manifest.json`; el reemplazo ocurre sólo después de validar y construir el
  nuevo directorio.
- Para Slidev, la URL de reproducción debe ser la raíz
  `/presentation/`, porque el router generado usa allí sus rutas `/1`, `/2`,
  etc. `/presentation/presenter` se usa para el modo del moderador. Marp
  conserva `/presentation/slides`.
- El SPA Slidev de audiencia se genera con `--without-notes`. Sus assets se
  sirven únicamente después de validar el acceso de conferencia; el endpoint
  inicial establece una cookie de token con alcance al directorio de la
  presentación para que los bundles relativos no pierdan autenticación.
- El preview Slidev no sirve el SPA: genera PNG de las primeras diapositivas y
  los entrega mediante una ruta pública separada. PDF y miniatura se generan
  bajo demanda con Chromium y quedan cacheados por conferencia.
- La navegación de Slidev se traduce al mismo WebSocket existente usando la
  ruta relativa de la diapositiva, conservando `hash` como campo compatible
  con Marp.

El MVP todavía deja fuera la caché por hash de fuente, el artefacto separado de
notas para el moderador y la ejecución de dependencias arbitrarias del ZIP.
El FAT permite JavaScript generado por el builder, pero no convierte el ZIP en
un proyecto ejecutable: requiere auditoría, CSP y el rollout controlado de su
feature flag.

## Problem

Marp cubre el flujo Markdown → slides HTML actual, pero algunos ponentes
necesitan capacidades propias de Slidev: componentes Vue, demostraciones
interactivas, snippets de código y exportaciones adicionales. Introducir esas
capacidades reemplazando Marp rompería presentaciones existentes, contratos de
API, rutas, artefactos almacenados y el flujo operativo de conferencias.

## Objective

Al completar la iniciativa, una persona autorizada para subir la presentación
podrá elegir `MARP` o `SLIDEV` en
cada carga, subir el ZIP correspondiente y presentar el resultado con el mismo
flujo de acceso y control en vivo que ya existe. Una presentación Marp existente
seguirá funcionando sin cambios ni re-procesamiento.

## Scope

### Includes

- Selector obligatorio de proveedor en el formulario de carga:
  `MARP | SLIDEV`.
- Compatibilidad con presentaciones existentes: si una presentación antigua no
  tiene proveedor en su manifiesto, se interpreta como `MARP`.
- Selector y ayudas específicas del proveedor junto al input del ZIP, antes de
  iniciar el procesamiento.
- Upload de un paquete Slidev fuente validado, con `slides.md` y assets locales,
  o de un artefacto FAT con `slidev-artifact.json` y `dist/` ya compilado.
- Construcción de una SPA estática Slidev con `--base` por conferencia.
- Artefactos separados para audiencia y moderador cuando sea necesario para no
  exponer notas del ponente a la audiencia.
- PDF, PPTX y PNG como exportaciones de Slidev; Markdown fuente y el paquete
  original como artefactos descargables administrativos, sujeto a límites.
- Reutilización de las rutas públicas y autenticadas actuales, con un
  manifiesto que indique el proveedor, versión y formatos disponibles.
- Integración con la sincronización WebSocket existente mediante un adaptador
  de navegación; no se habilitará el control remoto nativo de Slidev sin pasar
  por la autorización de InsightBloom.
- Preview limitado para asistentes sin acceso completo, sin filtrar el SPA
  completo ni las notas del ponente.
- Tests de contrato, seguridad de paquetes, build, exportación y regresión de
  Marp.
- Actualización de CI/CD y documentación operativa. El CD seguirá viviendo en
  `InsightBloom-gitops` y FluxCD.

### Excludes

- Reemplazar Marp, eliminar `@marp-team/marp-cli` o migrar presentaciones
  existentes automáticamente.
- Convertir Markdown Marp en Markdown Slidev o viceversa.
- Permitir `npm install` por conferencia, dependencias arbitrarias, plugins
  externos o un `vite.config` aportado por el usuario en el MVP.
- Ejecutar código Vue/Node proporcionado libremente por asistentes u
  organizadores dentro del proceso principal del servicio.
- Edición colaborativa de slides, historial multiusuario o una segunda capa de
  WebSocket.
- Usar el modo `--remote` de Slidev como canal de control paralelo al WebSocket
  autorizado de InsightBloom.
- Cambiar la experiencia de las herramientas de pizarra, diagramas o notas.

## Functional Requirements

- FR-001: El formulario de carga debe exigir que quien sube la presentación
  seleccione `MARP` o `SLIDEV` antes de enviar el ZIP. Para no romper el flujo
  actual, `MARP` puede aparecer preseleccionado, pero la opción debe ser visible
  y viajar explícitamente en la solicitud.
- FR-002: El endpoint de upload debe recibir el proveedor junto con el ZIP,
  seleccionar el adaptador correspondiente y rechazar una solicitud sin
  proveedor o con un proveedor desconocido.
- FR-003: El proveedor procesado debe persistirse en el manifiesto de la
  presentación activa y devolverse en status; las presentaciones antiguas sin
  ese dato se interpretan como `MARP`.
- FR-004: Una nueva carga puede cambiar el proveedor activo de la conferencia,
  pero sólo después de que la validación, build y artefactos hayan terminado
  correctamente. Una carga fallida no debe borrar ni cambiar la presentación
  anterior.
- FR-005: Un paquete Slidev fuente válido debe contener una entrada Markdown y
  sólo archivos permitidos por la política de seguridad del MVP. Los assets
  locales deben quedar disponibles desde la SPA construida.
- FR-016: Un paquete Slidev FAT válido debe contener `slidev-artifact.json`,
  `dist/index.html`, hashes de sus archivos y sólo ubicaciones publicables. El
  backend debe auditarlo y servirlo sin ejecutar `slidev build`.
- FR-006: El backend debe conservar un manifiesto por conferencia con proveedor,
  versión de Slidev/Marp, entrada, fecha, estado, hashes y exportaciones
  disponibles.
- FR-007: Las rutas existentes de status, slides, preview, PDF y WebSocket
  deben seguir funcionando para Marp. Para Slidev deben resolver el artefacto
  correspondiente sin que el frontend tenga que conocer URLs internas.
- FR-008: La vista pública debe requerir el mismo acceso de conferencia que
  Marp. El preview sin boleto sólo puede mostrar las primeras diapositivas o
  imágenes de preview y nunca notas, tokens ni el bundle completo.
- FR-009: La vista del moderador debe abrir la presentación Slidev completa y
  conservar el control de navegación en vivo existente.
- FR-010: La audiencia autenticada debe seguir la diapositiva publicada por el
  moderador mediante el canal WebSocket actual, con reconexión y fallback
  existentes.
- FR-011: Las notas del ponente sólo podrán estar en el artefacto del moderador
  y nunca en el SPA de audiencia ni en un preview público.
- FR-012: El moderador podrá descargar las exportaciones de Slidev disponibles:
  PDF, PPTX y PNG por diapositiva. El formato Markdown y el paquete original
  quedarán restringidos a la descarga administrativa/autorizada.
- FR-013: Las presentaciones Marp existentes deben seguir sirviéndose desde sus
  artefactos actuales aunque el servicio sea actualizado para incluir Slidev.
- FR-014: Si se vuelve a subir una presentación, el sistema
  debe reemplazar atómicamente el artefacto activo o mantener el anterior como
  rollback; nunca debe dejar `status=ready` apuntando a un directorio parcial.
- FR-015: Los errores de validación, build, exportación, tamaño, timeout y
  recursos deben mostrarse en el panel de gestión sin revelar logs internos ni
  rutas del contenedor.

## Non-functional Requirements

- NFR-001: La construcción Slidev debe ejecutarse como usuario no root, en un
  directorio temporal aislado, con límites de tiempo, memoria, CPU, tamaño de
  archivo, número de archivos y concurrencia.
- NFR-002: El proceso de build no debe instalar dependencias desde Internet ni
  leer secretos del entorno de la aplicación. Las versiones de Slidev,
  Playwright y temas permitidos deben quedar fijadas en `package-lock.json`.
- NFR-003: El extractor debe rechazar traversal (`../`), enlaces simbólicos,
  nombres ambiguos y archivos fuera de la cuota antes de escribir en
  `DATA_DIR`.
- NFR-004: El ZIP fuente debe permitir únicamente Markdown, imágenes, fuentes y
  estilos declarados por la política. `package.json`, `vite.config.*`,
  `*.vue`, plugins y scripts ejecutables se rechazan. El FAT puede contener
  JavaScript generado dentro de `dist/`, pero nunca código fuente, dependencias,
  source maps ni configuración de build.
- NFR-005: La salida debe ser reproducible con la misma fuente, versión de
  herramienta y configuración. El manifiesto debe permitir diagnosticar qué
  proveedor generó cada artefacto.
- NFR-006: Marp no debe añadir una dependencia ni una etapa de build que
  cambie su salida, tiempos o rutas sin una prueba de regresión explícita.
- NFR-007: La publicación de una nueva versión debe ser observable mediante
  logs estructurados con conferencia, proveedor, versión, duración, tamaño y
  resultado, sin incluir el contenido de las diapositivas.
- NFR-008: La imagen del servicio debe conservar compatibilidad con Node 20 y
  Chromium disponible, pero el impacto de tamaño y tiempo de exportación debe
  medirse antes de habilitar Slidev en producción.

## Acceptance Criteria

### Scenario 1 — Compatibilidad de Marp

- **Given** una conferencia existente con una presentación Marp
- **When** se despliega una versión que incluye Slidev
- **Then** el status, preview, visor, PDF y sincronización de Marp funcionan
  con los mismos contratos y sin re-subir la presentación.

### Scenario 2 — Alta de Slidev

- **Given** un organizador selecciona `SLIDEV` en el formulario y carga un ZIP válido
- **When** termina el procesamiento
- **Then** el panel muestra el estado listo, el moderador ve la SPA y el
  asistente autorizado puede ver las diapositivas publicadas.

### Scenario 3 — Seguridad de paquete

- **Given** un ZIP Slidev con `../secret`, symlink, `package.json` o plugin
- **When** se valida la carga
- **Then** el servidor la rechaza antes de ejecutar Slidev y no modifica el
  artefacto activo.

### Scenario 4 — Preview

- **Given** un asistente sin boleto
- **When** abre la presentación
- **Then** sólo ve el preview limitado y no puede obtener el SPA completo,
  notas, Markdown privado ni exportaciones administrativas.

### Scenario 5 — Exportación

- **Given** una presentación Slidev lista
- **When** el organizador solicita PDF, PPTX o PNG
- **Then** el servicio devuelve el formato solicitado, registra el resultado y
  reutiliza el artefacto cacheado en solicitudes posteriores.

### Scenario 6 — Sincronización en vivo

- **Given** el moderador navega una presentación Slidev
- **When** cambia la diapositiva
- **Then** la audiencia conectada recibe el mismo estado a través del WebSocket
  de InsightBloom; cerrar o reconectar el socket no concede acceso adicional.

### Scenario 7 — Cambio de proveedor y reemplazo atómico

- **Given** una presentación Marp activa y una nueva carga marcada como `SLIDEV`
- **When** el build Slidev termina correctamente
- **Then** Slidev pasa a ser el proveedor activo y el status lo informa.

- **Given** una presentación activa y una nueva carga que falla
- **When** termina el build fallido
- **Then** la versión anterior continúa disponible y el frontend muestra el
  error de la nueva carga.

### Scenario 8 — Slidev FAT

- **Given** un ZIP Slidev con `slidev-artifact.json`, `dist/index.html` y hashes
  válidos
- **When** `SLIDEV_FAT_ENABLED=true` y el auditor no encuentra reglas bloqueantes
- **Then** el servicio publica `presentationFormat=fat`, sirve el `dist` sin
  recompilar y conserva previews/PDF empaquetados cuando existen.

- **Given** un FAT con `source/`, traversal, hash incorrecto o una regla de
  seguridad bloqueante
- **When** se recibe en el endpoint
- **Then** se rechaza sin reemplazar la presentación activa.

## Dependencies

- `backend/services/insightbloom-presentations` como servicio de integración.
- `@slidev/cli` y el conjunto de temas/versiones que se decida fijar.
- Chromium/Playwright para exportaciones PDF, PPTX y PNG.
- Contratos actuales de `insightbloom-users` para acceso de conferencia.
- `frontend/web` para proveedor, gestión, preview y visor.
- Workflow `publish-presentations.yml` para construir/publicar GHCR.
- `/Users/rafex/repository/github/rafex/InsightBloom-gitops` sólo si cambian
  valores, recursos, límites o la imagen observada por FluxCD.

## Risks

- **R-001 — Ejecución de código durante el build:** Slidev es una aplicación
  Vue/Vite, no un conversor Markdown puramente declarativo. Mitigación:
  política de archivos permitidos en MVP, sin instalación de paquetes, usuario
  no root y spike de seguridad antes de aceptar componentes personalizados.
- **R-002 — Exposición de notas:** el mismo bundle puede contener información
  del ponente. Mitigación: builds separados audiencia/moderador o filtrado
  verificable; prueba que inspeccione el artefacto final.
- **R-003 — Deriva de navegación:** Slidev y Marp pueden representar el estado
  de slide de forma diferente. Mitigación: adapter de navegación probado con
  hash/route y contrato de eventos común; no acoplar el backend a una URL
  generada manualmente.
- **R-004 — Imagen y exportaciones pesadas:** Playwright y Slidev pueden
  aumentar la imagen y consumir CPU/memoria. Mitigación: medir en spike,
  cachear por hash, limitar concurrencia y decidir si el exportador se separa
  como worker antes del rollout.
- **R-005 — Compatibilidad de assets:** imports remotos o temas externos pueden
  romper builds offline. Mitigación: assets locales y temas allowlisted en MVP,
  mensajes de validación claros y una fase futura para dependencias confiables.
- **R-006 — CD separado:** una imagen nueva no implica que FluxCD ya la haya
  desplegado. Mitigación: documentar la cadena GHCR → InsightBloom-gitops →
  FluxCD y validar el tag del pod antes de pruebas manuales.

## Execution Plan

→ `tasks/slidev-presentations/TASKS.md`

## Validation Plan

- Unit tests del selector de proveedor, migración por defecto y manifiesto.
- Tests de seguridad de ZIP con traversal, symlinks, cuotas, archivos
  ejecutables y configuraciones prohibidas.
- Tests de contrato de las rutas existentes para Marp y nuevas variantes para
  Slidev.
- Fixture mínimo Slidev que ejecute `build` y exporte PDF/PPTX/PNG.
- Fixture Marp existente que confirme la no regresión.
- Test del WebSocket que verifique navegación para ambos proveedores.
- Inspección del bundle de audiencia para confirmar ausencia de notas y
  metadatos privados.
- Build Docker del servicio y smoke test local con Compose.
- PR CI verde; el despliegue real sólo se verifica después de la reconciliación
  de FluxCD en el repositorio GitOps.

## External References

- [Slidev — Getting Started](https://sli.dev/guide/)
- [Slidev — Building and Hosting](https://sli.dev/guide/hosting)
- [Slidev — Exporting](https://sli.dev/guide/exporting.html)
- [Slidev — Customizations](https://sli.dev/custom/)
