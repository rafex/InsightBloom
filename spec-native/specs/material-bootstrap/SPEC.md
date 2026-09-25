# SPEC: Preparación de materiales para IDEs

## Initiative
material-bootstrap

## Status
active

## Summary
Cada evento configura de forma independiente materiales públicos de GitHub y un preparador shell
o Python. El caché puede estar habilitado sin ejecutar un preparador; un script inline puede
ejecutarse sin configurar caché. Solo los scripts cuya fuente sea una ruta dentro del repositorio
requieren materiales cacheados. Un servicio interno sincroniza `url+ref` en un PVC local
`local-path` RWO, publica releases inmutables por SHA y sirve archivos/subárboles mediante HTTP
interno. Solo el servicio monta el PVC. `insightbloom-users` sincroniza con un bearer interno; el
sandbox recibe el `sourceKey`, la URL interna y el SHA, y `material-copy` descarga exclusivamente
la ruta solicitada. El preparador corre únicamente cuando el interruptor explícito está activo,
como el usuario del alumno antes de abrir el IDE, y deja estado/log sin impedir el acceso si falla.

## Requirements

- Solo se admiten URLs HTTPS públicas de `github.com`, sin credenciales, query ni fragmento.
- Solo el propietario del evento puede configurar materiales y preparador. El interruptor de
  ejecución del preparador es independiente de la fuente cacheada, se guarda apagado por defecto y
  al apagarse conserva el script sin ejecutarlo.
- Un repositorio/ref configurado habilita `material-copy` aunque el preparador esté apagado. Un
  preparador inline no requiere URL/ref; un preparador basado en ruta relativa del material sí.
- Al omitir el nuevo campo de activación, clientes antiguos conservan la semántica previa:
  configuraciones antiguas completas de bootstrap siguen activas.
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

## Acceptance scenarios

- Con el interruptor apagado, los campos del preparador se conservan y el script no se ejecuta.
- Un preparador inline activado funciona sin fuente de materiales ni solicitud a `/sync`.
- Una fuente cacheada puede sincronizarse y usarse con `material-copy` aunque no haya preparador
  activado.
- Un preparador versionado activado requiere una fuente y ref cacheadas válidas.
- La migración activa configuraciones antiguas completas una sola vez y no vuelve a activar una
  configuración que el propietario apagó posteriormente.

## Initial course configuration

La configuración recomendada para el taller de Agente IA usa
`https://github.com/rafex/presentaciones-cursos-talleres`, ref `main`, y copia desde
`talleres/crea-tu-agente-ia/ejercicios`. El SHA usado queda siempre registrado; el repositorio
completo nunca se clona dentro de cada workspace. En el corte al transporte HTTP, el claim
Longhorn anterior se conserva hasta verificar que ningún Pod activo lo monte; su retiro es una
operación GitOps posterior, nunca parte del rollout inicial.
