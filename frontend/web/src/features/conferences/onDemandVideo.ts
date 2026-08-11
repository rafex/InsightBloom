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
