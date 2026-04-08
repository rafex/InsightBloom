# CONVENTIONS.md

Reglas operativas y de implementacion del proyecto.

## Debe cubrir

- Naming de archivos, carpetas, funciones y componentes.
- Convenciones de testing.
- Convenciones de branching y commits.
- Convenciones de estructura por feature o capa.
- Reglas de documentacion y actualizacion de contexto.

## Template

### Codigo

- Preferir cambios pequenos y locales.
- Evitar duplicacion accidental.
- Seguir la estructura definida en `ARCHITECTURE.md`.
- En backend, respetar arquitectura hexagonal:
  dominio y casos de uso no dependen de adaptadores.
- Los handlers HTTP no deben contener logica de negocio.
- SQLite, crypto y clientes HTTP internos deben entrar por puertos y
  adaptadores.
- Todos los microservicios backend deben partir de la misma estructura base
  de carpetas para reducir ambiguedad operativa.
- El frontend debe mantener separacion clara entre `pages`, `features`,
  `components` y `services`.
- Las rutas viven en `app/router` y la logica de negocio del frontend no
  debe dispersarse dentro de componentes visuales.

### Operacion

- `Makefile` es el builder: compila, testea, lintea y produce artefactos.
  No orquesta procesos ni arranca servicios.
- `Justfile` es el task runner: orquesta flujos de desarrollo, demos y
  operaciones. Cuando necesita compilar, delega a `make`.
- `scripts/build/` contiene scripts de build y no scripts de ejecucion.
- `scripts/run/` contiene scripts de arranque y no scripts de build.
- `scripts/sim/` contiene scripts de simulacion y demo.
- Los workflows en `.github/workflows` deben usar los mismos comandos
  oficiales definidos en `COMMANDS.md`, `Justfile` o `Makefile`.

### HTTP

- Las respuestas exitosas devuelven JSON con `data` y `meta` cuando aplique.
- Los errores devuelven JSON con `error.code`, `error.message`,
  `error.details` y `meta.requestId`.
- La paginacion usa `page`, `pageSize`, `total`, `totalPages`.
- Las APIs publicas no exponen ids seriales internos.

### Tests

- Cada cambio relevante debe definir su estrategia de validacion.
- Los tests deben vivir cerca del codigo o donde el proyecto lo defina.

### Documentacion

- Los `README.md` indexan.
- Los archivos en MAYUSCULAS contienen contexto fuente.
- No duplicar hechos entre documentos sin una razon fuerte.

### Agentes

- Antes de editar, leer el `README.md` de la carpeta.
- Actualizar el documento fuente si cambia una verdad compartida.
