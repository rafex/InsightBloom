# SPEC: Preparación de materiales para IDEs

## Initiative
material-bootstrap

## Status
active

## Summary
Cada evento puede declarar un repositorio público de GitHub y un preparador shell o Python. Un
servicio interno sincroniza `url+ref` en un PVC local `local-path` RWO, publica releases
inmutables por SHA y sirve archivos/subárboles mediante HTTP interno. Solo el servicio monta el
PVC. `insightbloom-users` sincroniza con un bearer interno; el sandbox recibe el `sourceKey`, la
URL interna y el SHA, y `material-copy` descarga exclusivamente la ruta solicitada. El
preparador corre como el usuario del alumno antes de abrir el IDE y deja estado/log en el
workspace sin impedir el acceso si falla.

## Requirements

- Solo se admiten URLs HTTPS públicas de `github.com`, sin credenciales, query ni fragmento.
- La configuración completa es exclusiva del propietario del evento: URL, ref, tipo de script,
  origen inline o ruta relativa del material y contenido/ruta.
- Cada sincronización sigue el ref configurado y fija el SHA efectivo en el Pod y en
  `.insightbloom/bootstrap-status.json`; las descargas no siguen un alias mutable como `current`.
- El PVC `local-path` es RWO y solo lo monta el servicio. Los sandboxes no montan el repositorio,
  no pueden llamar a `/sync` y no reciben credenciales Git ni de Kubernetes.
- `/sync` requiere el secreto interno de plataforma; las descargas GET solo se permiten desde
  `insightbloom-users` y los Pods sandbox mediante NetworkPolicy. No hay Ingress público.
- El Deployment y PVC del caché viven en el namespace de aplicación junto a `users`, de modo que
  no se replica el secreto privilegiado al namespace de sandboxes. El acceso sandbox es solo al
  Service y puerto del caché.
- El cache tiene límites configurables de tamaño por archivo/repo y total, limpieza de revisiones
  antiguas y expiración de fuentes inactivas. Es regenerable, de nodo único y sin HA.
- El helper `material-copy` descarga subárboles sin sobrescribir archivos existentes. Los
  scripts conservan libertad para crear o reorganizar recursos dentro de su propio workspace.
- Si el caché o el script falla, el IDE permanece disponible con log y README de diagnóstico.

## Initial course configuration

La configuración recomendada para el taller de Agente IA usa
`https://github.com/rafex/presentaciones-cursos-talleres`, ref `main`, y copia desde
`talleres/crea-tu-agente-ia/ejercicios`. El SHA usado queda siempre registrado; el repositorio
completo nunca se clona dentro de cada workspace. En el corte al transporte HTTP, el claim
Longhorn anterior se conserva hasta verificar que ningún Pod activo lo monte; su retiro es una
operación GitOps posterior, nunca parte del rollout inicial.
