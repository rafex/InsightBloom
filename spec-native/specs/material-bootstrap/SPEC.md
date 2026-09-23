# SPEC: Preparación de materiales para IDEs

## Initiative
material-bootstrap

## Status
active

## Summary
Cada evento puede declarar un repositorio público de GitHub y un preparador shell o Python. Un
servicio interno actualiza una copia compartida RWX por `url+ref`, publica releases atómicos por
SHA y los sandboxes la montan de solo lectura. El preparador corre como el usuario del alumno,
antes de abrir el IDE, y deja estado y log dentro del workspace sin impedir el acceso si falla.

## Requirements

- Solo se admiten URLs HTTPS públicas de `github.com`, sin credenciales, query ni fragmento.
- La configuración completa es exclusiva del propietario del evento: URL, ref, tipo de script,
  origen inline o ruta relativa del material y contenido/ruta.
- Cada sincronización sigue el ref configurado y registra el SHA efectivo en
  `.insightbloom/bootstrap-status.json`.
- El volumen material es RWX y solo lectura para Web, Neovim y LazyVim; ningún sandbox escribe
  en él ni recibe credenciales Git o de Kubernetes.
- El helper `material-copy` copia subárboles sin sobrescribir archivos existentes. Los scripts
  conservan libertad para crear o reorganizar recursos dentro de su propio workspace.
- Si el caché o el script falla, el IDE permanece disponible con log y README de diagnóstico.

## Initial course configuration

La configuración recomendada para el taller de Agente IA usa
`https://github.com/rafex/presentaciones-cursos-talleres`, ref `main`, y copia desde
`talleres/crea-tu-agente-ia/ejercicios`. El SHA usado queda siempre registrado; el repositorio
completo nunca se clona dentro de cada workspace.
