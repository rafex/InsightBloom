// Conversion de la URL que pega el organizador (YouTube/PeerTube) a una URL de embed. Vive en
// el frontend a proposito: el backend solo guarda la URL tal cual se pego, sin normalizarla (ver
// SetOnDemandVideoUseCase.java) -- asi un cambio de formato de embed no requiere migracion de
// datos, solo tocar esta funcion.
import type { EventCapability, OnDemandCuePoint } from '@/services/api/types'

// Catalogo estatico de pestañas publicas a las que puede apuntar una sugerencia de cue-point --
// fuente unica para el editor manual/Markdown (OnDemandVideoManagePage.vue) y el panel de
// notificaciones publico (OnDemandFloatingVideo.vue), que necesita mostrar el nombre en español
// de la herramienta, no el path tecnico.
export const TOOL_CATALOG: Array<{ path: string, label: string, capability: EventCapability }> = [
  { path: 'doubts', label: 'Nube de dudas', capability: 'WORD_CLOUD' },
  { path: 'topics', label: 'Nube de temas', capability: 'WORD_CLOUD' },
  { path: 'presentation', label: 'Presentación', capability: 'PRESENTATION' },
  { path: 'survey', label: 'Encuesta', capability: 'SURVEY' },
  { path: 'ide', label: 'IDE', capability: 'CODE_IDE' },
  { path: 'diagrams', label: 'Diagramas', capability: 'DIAGRAMMING' },
  { path: 'notes', label: 'Notas colaborativas', capability: 'COLLAB_NOTES' },
  { path: 'whiteboard', label: 'Pizarra', capability: 'WHITEBOARD' }
]

/** Nombre en español de una herramienta por su path, o el path tal cual si no está en el catálogo. */
export function toolLabelForPath(path: string): string {
  return TOOL_CATALOG.find(t => t.path === path)?.label || path
}

// Ventana de 3s: el poll de OnDemandFloatingVideo.vue corre cada 1s, asi que un cue-point puntual
// no se pierde entre dos lecturas consecutivas si el timestamp cae justo entre medio.
const CUE_POINT_TRIGGER_WINDOW_SECONDS = 3

/**
 * Dado el tiempo actual de reproduccion, devuelve el cue-point que corresponde disparar como
 * notificacion nueva, o null si ninguno aplica todavia. `alreadyFiredSeconds` son los `atSeconds`
 * que ya generaron una notificacion en esta sesion (se pasa por fuera para que descartar una
 * notificacion no la deje disparar de nuevo si el usuario rebobina el video y vuelve a pasar por
 * ahi -- ver OnDemandFloatingVideo.vue).
 */
export function findCuePointToTrigger(
  cuePoints: OnDemandCuePoint[],
  currentTime: number,
  alreadyFiredSeconds: ReadonlySet<number>
): OnDemandCuePoint | null {
  return cuePoints.find(cue =>
    currentTime >= cue.atSeconds
    && currentTime < cue.atSeconds + CUE_POINT_TRIGGER_WINDOW_SECONDS
    && !alreadyFiredSeconds.has(cue.atSeconds)) || null
}

const YOUTUBE_ID_PATTERN = /(?:youtube\.com\/(?:watch\?v=|shorts\/)|youtu\.be\/)([\w-]{6,})/i

function toYoutubeEmbedUrl(url: string): string | null {
  if (url.includes('youtube.com/embed/')) return url
  const match = url.match(YOUTUBE_ID_PATTERN)
  if (!match) return null
  return `https://www.youtube.com/embed/${match[1]}`
}

function withEmbedParams(url: string, params: Record<string, string>): string {
  try {
    const parsed = new URL(url)
    Object.entries(params).forEach(([key, value]) => parsed.searchParams.set(key, value))
    return parsed.toString()
  } catch {
    return url
  }
}

function toPeerTubeEmbedUrl(url: string): string | null {
  try {
    const parsed = new URL(url)
    if (parsed.pathname.includes('/videos/embed/')) return withEmbedParams(url, { api: '1', autoplay: '0' })
    const watchMatch = parsed.pathname.match(/^\/w\/([\w-]+)$/)
      || parsed.pathname.match(/^\/videos\/watch\/([\w-]+)$/)
    if (!watchMatch) return null
    return withEmbedParams(`${parsed.origin}/videos/embed/${watchMatch[1]}`, { api: '1', autoplay: '0' })
  } catch {
    return null
  }
}

/** Devuelve la URL de embed para el reproductor, o null si no se reconoce el formato. */
export function toEmbedUrl(provider: 'YOUTUBE' | 'PEERTUBE' | null | undefined, url: string | null | undefined): string | null {
  if (!provider || !url) return null
  if (provider === 'YOUTUBE') return toYoutubeEmbedUrl(url)
  if (provider === 'PEERTUBE') return toPeerTubeEmbedUrl(url)
  return null
}

export interface CuePointRow {
  minutes: number
  seconds: number
  label: string
  toolPath: string
}

export interface CuePointToolOption {
  path: string
  label: string
}

// Formato acordado con el organizador para carga masiva (ver OnDemandVideoManagePage.vue):
//   - 0:15 Abrí la encuesta ahora → survey
// El separador acepta tanto la flecha real como "->" para poder tipearlo sin caracteres
// especiales. El token final matchea el `path` o el `label` (en español, case-insensitive) de
// alguna herramienta habilitada -- lo que sea mas comodo de escribir a mano.
const CUE_POINT_LINE_PATTERN = /^-\s*(\d+):(\d{2})\s+(.+?)\s*(?:→|->)\s*(\S+)\s*$/

/**
 * Parsea una lista de sugerencias en Markdown. Todo o nada: si cualquier línea falla, no se
 * aplica ningún cambio (evita dejar al organizador con una carga parcial sin darse cuenta).
 */
export function parseCuePointsMarkdown(
  markdown: string,
  toolOptions: CuePointToolOption[]
): { cuePoints: CuePointRow[] } | { errors: string[] } {
  const lines = markdown.split('\n').map(line => line.trim()).filter(line => line.length > 0)
  if (lines.length === 0) return { errors: ['No se encontró ninguna línea de sugerencia.'] }

  const errors: string[] = []
  const cuePoints: CuePointRow[] = []

  lines.forEach((line, index) => {
    const lineNumber = index + 1
    const match = line.match(CUE_POINT_LINE_PATTERN)
    if (!match) {
      errors.push(`Línea ${lineNumber}: formato inválido. Usá "- M:SS texto → herramienta".`)
      return
    }
    const [, minutesStr, secondsStr, label, toolToken] = match
    const minutes = Number(minutesStr)
    const seconds = Number(secondsStr)
    if (seconds > 59) {
      errors.push(`Línea ${lineNumber}: los segundos deben estar entre 00 y 59.`)
      return
    }
    const trimmedLabel = label.trim()
    if (!trimmedLabel) {
      errors.push(`Línea ${lineNumber}: falta el texto de la sugerencia.`)
      return
    }
    const tool = toolOptions.find(opt =>
      opt.path.toLowerCase() === toolToken.toLowerCase() || opt.label.toLowerCase() === toolToken.toLowerCase())
    if (!tool) {
      const available = toolOptions.map(opt => opt.path).join(', ')
      errors.push(`Línea ${lineNumber}: herramienta "${toolToken}" no reconocida. Opciones disponibles: ${available}.`)
      return
    }
    cuePoints.push({ minutes, seconds, label: trimmedLabel, toolPath: tool.path })
  })

  return errors.length > 0 ? { errors } : { cuePoints }
}
