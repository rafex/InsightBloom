// Conversion de la URL que pega el organizador (YouTube/PeerTube) a una URL de embed. Vive en
// el frontend a proposito: el backend solo guarda la URL tal cual se pego, sin normalizarla (ver
// SetOnDemandVideoUseCase.java) -- asi un cambio de formato de embed no requiere migracion de
// datos, solo tocar esta funcion.

const YOUTUBE_ID_PATTERN = /(?:youtube\.com\/(?:watch\?v=|shorts\/)|youtu\.be\/)([\w-]{6,})/i

function toYoutubeEmbedUrl(url: string): string | null {
  if (url.includes('youtube.com/embed/')) return url
  const match = url.match(YOUTUBE_ID_PATTERN)
  if (!match) return null
  return `https://www.youtube.com/embed/${match[1]}`
}

function toPeerTubeEmbedUrl(url: string): string | null {
  try {
    const parsed = new URL(url)
    if (parsed.pathname.includes('/videos/embed/')) return url
    const watchMatch = parsed.pathname.match(/^\/w\/([\w-]+)$/)
      || parsed.pathname.match(/^\/videos\/watch\/([\w-]+)$/)
    if (!watchMatch) return null
    return `${parsed.origin}/videos/embed/${watchMatch[1]}`
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
