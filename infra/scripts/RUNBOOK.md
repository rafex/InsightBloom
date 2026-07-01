# Runbook — InsightBloom Scripts

Scripts de operación para el cluster k3s de InsightBloom.

**Namespace por defecto:** `insightbloom`
**Kubeconfig:** `~/.kube/config_k3s`

---

## fix-orphaned-memberships.sh

### Problema que resuelve

Cuando un organizador borra una conferencia a través del API, el cascade elimina:
- El registro de la conferencia (`conferences` table en `users.db`)
- Los datos en ingest, query, moderation, presentations y survey

**Sin embargo**, si la conferencia desaparece por otra vía (borrado manual del PVC, `helm uninstall`, limpieza directa del nodo) el cascade no corre, y los registros de membresía de los asistentes (`conference_memberships`) quedan huérfanos apuntando a una conferencia que ya no existe.

Síntoma visible: el asistente ve la conferencia como "DISPONIBLE" en "Mis conferencias" aunque el organizador ya no la tenga.

### Cuándo ejecutar

- Un usuario reporta que ve una conferencia "DISPONIBLE" que el organizador ya borró o que no existe.
- Tras un `helm uninstall` + `helm install` que borró los PVCs.
- Tras una migración o limpieza manual de datos.
- Como verificación rutinaria después de incidentes de pérdida de datos.

### Prerrequisitos

| Herramienta | Versión mínima | Verificar |
|-------------|---------------|-----------|
| `kubectl` | cualquiera | `kubectl version --client` |
| `sqlite3` | 3.x | `sqlite3 --version` |
| Acceso al cluster | — | `kubectl get pods -n insightbloom` |

### Uso

```bash
# 1. Ver diagnóstico sin tocar nada (recomendado primero)
./infra/scripts/fix-orphaned-memberships.sh --dry-run

# 2. Limpiar (pide confirmación antes de escribir al pod)
./infra/scripts/fix-orphaned-memberships.sh

# 3. Con namespace o kubeconfig explícito
./infra/scripts/fix-orphaned-memberships.sh \
  --namespace insightbloom \
  --kubeconfig ~/.kube/config_k3s
```

### Qué hace internamente

```
1. Localiza el pod insightbloom-users por label (app.kubernetes.io/name=insightbloom-users)
2. kubectl cp  pod:/data/users.db → /tmp/users-XXXXXX.db  (copia local)
3. Consulta SQLite localmente:
     SELECT * FROM conference_memberships m
     WHERE NOT EXISTS (SELECT 1 FROM conferences c WHERE c.uuid = m.conference_uuid)
4. Muestra el diagnóstico y pide confirmación (omitido con --dry-run)
5. DELETE FROM conference_memberships WHERE ...  (en la copia local)
6. kubectl cp  /tmp/users-XXXXXX.db → pod:/data/users.db  (devuelve al pod)
7. kubectl rollout restart deployment/insightbloom-users -n insightbloom
8. Espera a que el pod quede Running (timeout 60 s)
```

### Salida esperada (modo normal)

```
Pod encontrado: insightbloom-users-68f4b6c956-ml6ht (namespace: insightbloom)
Copiando /data/users.db desde el pod...

=== Diagnóstico ===
Conferencias totales:|1
Memberships totales:|3
Memberships huérfanas (conferencia inexistente):|3

Detalle de memberships huérfanas:
user_uuid   conference_uuid                       conference_name_snapshot  joined_at
----------  ------------------------------------  ------------------------  --------------------
abc123...   cd60f191-1f81-430e-92dc-3cd0bc715ec3  demo                      2026-06-01T10:00:00Z
...

¿Eliminar 3 membership(s) huérfana(s)? [s/N] s
Limpieza completada. Memberships restantes: 0
Copiando DB limpia de vuelta al pod...
Reiniciando deployment/insightbloom-users...
deployment.apps/insightbloom-users successfully rolled out

Listo. Las memberships huérfanas fueron eliminadas y el servicio está corriendo.
```

### Salida esperada (sin huérfanos)

```
No hay registros huérfanos. No se requiere acción.
```

### Riesgos y precauciones

| Riesgo | Mitigación |
|--------|-----------|
| Borrar memberships válidas | El script solo borra filas cuyo `conference_uuid` no existe en `conferences`; las activas no se tocan |
| Ventana de indisponibilidad | El `rollout restart` causa ~10 s de downtime del users service; SQLite libera el lock al reiniciar |
| Corrupción si el pod escribe mientras se copia | El servicio sigue activo durante la copia; en un escenario de alta carga usar `--dry-run` primero y ejecutar en horario de baja actividad |
| Pérdida de la copia local | El archivo temporal se borra al salir con `trap cleanup EXIT`; si el script falla a mitad camino el pod conserva su DB original |

### Rollback

Si el restart falla o el pod no levanta, restaurar el DB que se copió antes de ejecutar el script:

```bash
# Obtener el pod nuevo
POD=$(kubectl get pods -n insightbloom -l app.kubernetes.io/name=insightbloom-users \
      -o jsonpath='{.items[0].metadata.name}')

# Copiar el backup (si lo tienes) o forzar un rollback del deployment
kubectl rollout undo deployment/insightbloom-users -n insightbloom
```

---

## wait-for-service.sh

Script genérico de healthcheck usado por el pipeline de CI/CD para esperar a que un servicio HTTP responda en `/health` antes de continuar el despliegue. No requiere operación manual.

```bash
./infra/scripts/wait-for-service.sh <url> [timeout_segundos]
```
