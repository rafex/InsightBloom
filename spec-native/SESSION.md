# SESSION.md

Estado activo de trabajo para continuidad multi-agente.

---

## Estado actual

- **Iniciativa activa**: `code-ide-sandbox-prewarm`
- **Branch**: `main`
- **Último agente**: Codex
- **Última acción**: completado el contrato LSP de Web IDE y CLI para Java,
  Python, JS/TS, HTML y CSS; se añadieron servidores npm precargados, la
  configuración Neovim y la siembra offline de tipos Node también en code-server.

---

## Contexto de la sesión

InsightBloom conserva Marp como motor compatible y añade Slidev como proveedor
seleccionable por carga. El MVP acepta ZIP controlados, genera una SPA Slidev
con base por conferencia, la protege con el acceso existente y traduce sus
rutas de navegación al WebSocket de presentaciones. El CD continúa fuera de
este repositorio, en `InsightBloom-gitops`, reconciliado por FluxCD.

Si la audiencia muestra `ticket_required` y el WebSocket devuelve `502`,
comprobar primero que la imagen del servicio incluya esta corrección: la
cookie debe tener `Path=/api/presentations/api/v1/conferences/{id}/presentation`.

La iniciativa de certificados por evento agrega dos motores seleccionables por
evento: `INHOUSE` (PDFBox, por defecto y compatible con lo existente) y
`HTML_CHROME` (catálogo/editor JSON controlado y renderizado interno con
Playwright + Chromium). La opción global `Diseño de certificado` permanece
como respaldo del motor Inhouse; la acción del evento abre el editor que
corresponde al motor configurado.

La iniciativa actual acelera la entrada al IDE: el organizador o el staff
operativo puede preparar el pool antes del evento. Kubernetes sigue arrancando
los Pods de forma asíncrona y la asignación por alumno mantiene el aislamiento y
los límites de capacidad existentes.

La publicación web del IDE usa un snapshot ZIP del workspace, no expone el
sandbox vivo: `users` autoriza y descarga el workspace; `presentations` audita
HTML, JavaScript, CSS, imágenes y fuentes, lo extrae de forma atómica y lo sirve
en `preview-insightbloom.v1.rafex.cloud` con CSP estricta, hash, TTL y
revocación. El procedimiento está en `workflows/IDE-WEB-PUBLICATION.md`.

El modo `terminal-nvim` ahora tiene soporte LSP para Java, Python, HTML, CSS,
`.js`, `.jsx`, `.ts`, `.tsx`, `package.json`, `jsconfig.json` y `tsconfig.json`.
Los tipos de Node se mantienen en la imagen y se enlazan dentro del workspace
efímero, por lo que ambos IDE no dependen de Internet para autocompletar `fs`,
`http`, `process`, `Buffer` y APIs relacionadas.

## Próximos pasos

- [ ] Completar exportación PPTX y caché por hash de Slidev
- [ ] Añadir pruebas automatizadas de ZIP malicioso, regresión Marp y WebSocket
- [ ] Validar el Docker build del servicio cuando Podman esté disponible
- [ ] Reconciliar el tag de imagen en `InsightBloom-gitops` mediante FluxCD
- [ ] Entregar a los agentes de presentaciones el formato de ZIP definido en
  `workflows/SLIDEV-PACKAGING.md`
- [ ] Completar tests de autorización y sanitización del editor de certificados
- [ ] Validar en UI la selección del motor al crear y configurar un evento,
  incluyendo la ruta legacy por evento y la escala responsive del editor HTML
- [ ] Validar en el cluster el pre-warm del pool completo y el reemplazo de Pods
  cuando se ocupan todos los asientos configurados

## Notas

- `docs/` se mantiene como referencia legacy con aviso de deprecación.
- `agents/` mantiene artefactos operativos (SECURITY.md, DIAGNOSE.md).
- Los documentos migrados conservan su contenido original; se actualizarán progresivamente.
