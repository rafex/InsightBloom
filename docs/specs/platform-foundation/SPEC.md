# SPEC.md

## Resumen

Definir la base operativa del repositorio para que otros agentes puedan
construir InsightBloom con entrypoints consistentes para build, run, CI y
despliegue local.

## Alcance

- Estructura raiz del repo.
- `.github/workflows`.
- `Justfile`, `Makefile`, `helpers-build`, `helpers-run`.
- `infra/docker`, `infra/compose`, `infra/scripts`, `infra/helm/charts`.

## Requisitos clave

- Debe existir una interfaz operativa clara en `COMMANDS.md`.
- CI debe reutilizar los mismos comandos oficiales del repo.
- La estructura debe soportar frontend, microservicios y packages
  compartidos sin mezclar responsabilidades.
