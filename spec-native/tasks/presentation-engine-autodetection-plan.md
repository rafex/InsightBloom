# Plan futuro: autodetección de Marp y Slidev

Estado: `planned`
Iniciativa: `slidev-presentations`

## Contexto

La carga de presentaciones actualmente recibe el motor de forma explícita
(`MARP` o `SLIDEV`). Esta decisión evita inferencias ambiguas y mantiene el
contrato de procesamiento estable mientras se consolidan los ZIP fuente y FAT
de Slidev.

La autodetección queda como una mejora futura. No debe mezclarse con la
corrección de acceso por boleto ni con la auditoría de archivos: detectar el
motor no convierte un ZIP en confiable.

## Reglas de detección propuestas

1. Si existe `slidev-artifact.json` válido y la estructura `dist/` requerida,
   clasificar como `SLIDEV` en formato `fat`.
2. Si existe un `slides.md` de Slidev con sus marcadores o metadatos esperados,
   clasificar como `SLIDEV` en formato `source`.
3. Si el Markdown contiene frontmatter `marp: true` y no hay señales de Slidev,
   clasificar como `MARP`.
4. Si hay un único Markdown sin señales concluyentes, aplicar un fallback
   documentado y compatible con la versión vigente; no depender solo del nombre
   del archivo.
5. Si aparecen señales contradictorias, varios candidatos o un manifiesto
   inválido, rechazar con `400 presentation_engine_ambiguous` y explicar qué
   archivos provocaron la ambigüedad.

## Flujo de usuario futuro

- El moderador sube el ZIP.
- El backend inspecciona nombres, manifiesto, frontmatter y estructura antes de
  ejecutar cualquier engine.
- La interfaz muestra `Motor detectado: Marp/Slidev` y `Formato: source/FAT`.
- Opcionalmente podrá existir un override explícito para casos heredados, pero
  nunca podrá forzar un engine incompatible con la estructura auditada.

## Seguridad y compatibilidad

- Mantener límites de tamaño, cantidad de entradas, expansión total,
  traversal/symlinks y extensiones permitidas.
- Auditar el ZIP antes de compilar o publicar; la detección no es una auditoría.
- Mantener separado el tratamiento de Marp y Slidev para que sus CSP,
  exportaciones, rutas y artefactos no se mezclen.
- Conservar errores específicos para que el agente que genera el ZIP pueda
  corregirlo sin ensayo y error.
- Agregar pruebas para Marp válido, Slidev source válido, Slidev FAT válido,
  ZIP ambiguo, manifiesto inválido y ZIP malicioso.

## Criterios de aceptación

- La detección produce el mismo resultado que seleccionar manualmente el motor
  en los ZIP ya soportados.
- Un ZIP ambiguo nunca se ejecuta ni reemplaza la presentación existente.
- El motor detectado queda persistido en el manifiesto de la conferencia.
- Público, presentador, PDF y vista de moderación usan el motor persistido.
- La carga explícita actual continúa funcionando como fallback durante una
  migración gradual.
