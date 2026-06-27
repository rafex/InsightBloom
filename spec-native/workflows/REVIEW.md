# REVIEW.md

Cómo revisar trabajo contra la spec.

## Flujo de revisión

1. Leer la spec de la iniciativa (`specs/<iniciativa>/SPEC.md`).
2. Verificar cada criterio de aceptación:
   - **Given** — ¿está la precondición cubierta por el código?
   - **When** — ¿se ejecuta correctamente la acción?
   - **Then** — ¿se cumple el resultado esperado?
3. Ejecutar tests del módulo afectado:
   ```bash
   make services-test    # backend
   make web-test         # frontend
   cd chat && pytest -v  # chat
   ```
4. Ejecutar lint:
   ```bash
   make lint
   ```
5. Revisar diff contra `main`:
   ```bash
   git diff main...HEAD --stat
   git diff main...HEAD
   ```
6. Verificar que no hay secretos expuestos:
   ```bash
   # Usar skill security-audit o revisar manualmente
   ```
7. Confirmar que los archivos modificados coinciden con `expected_files` en `TASKS.md`.

## Checklist de revisión

- [ ] Criterios de aceptación cumplidos (spec).
- [ ] Tests pasan (unit + integración si aplica).
- [ ] Lint limpio.
- [ ] Sin secretos expuestos.
- [ ] Commits con formato Conventional Commits.
- [ ] Estructura de paquetes respeta [`ARCHITECTURE.md`](../ARCHITECTURE.md).
- [ ] No se introdujo duplicación de código.
- [ ] Nuevas decisiones registradas en [`DECISIONS.md`](../DECISIONS.md) si son persistentes.

## Resultado

- **Aprueba**: merge a `main`, marcar tarea `done`, actualizar `TRACEABILITY.md`.
- **Requiere cambios**: documentar hallazgos, reabrir tarea si es necesario.
- **Rechaza**: documentar motivo, evaluar si la spec necesita revisión.
