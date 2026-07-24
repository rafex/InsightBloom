export interface MapCoordinates {
  latitude: number
  longitude: number
}

const NUMBER = '[-+]?\\d+(?:\\.\\d+)?'

function validCoordinates(latitude: number, longitude: number): MapCoordinates | null {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null
  return { latitude, longitude }
}

function pair(value: string | null | undefined): MapCoordinates | null {
  if (!value) return null
  const match = value.match(new RegExp(`(${NUMBER})\\s*[,/ ]\\s*(${NUMBER})`))
  if (!match) return null
  return validCoordinates(Number(match[1]), Number(match[2]))
}

/** Extrae coordenadas sin hacer peticiones remotas: solo se aceptan URLs conocidas de mapas. */
export function parseMapCoordinates(value: string): MapCoordinates | null {
  const raw = value.trim()
  if (!raw) return null
  let url: URL
  try {
    url = new URL(raw)
  } catch {
    return null
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return null

  const host = url.hostname.toLowerCase()
  const isGoogle = (host === 'google.com' || host.endsWith('.google.com')) && url.pathname.includes('/maps')
  const isOpenStreetMap = host === 'openstreetmap.org' || host.endsWith('.openstreetmap.org') || host === 'osm.org'
  if (!isGoogle && !isOpenStreetMap) return null

  if (isGoogle) {
    const at = url.pathname.match(new RegExp(`/@(${NUMBER}),(${NUMBER})`))
    const fromAt = at ? validCoordinates(Number(at[1]), Number(at[2])) : null
    if (fromAt) return fromAt
    for (const key of ['query', 'q', 'll']) {
      const fromQuery = pair(url.searchParams.get(key))
      if (fromQuery) return fromQuery
    }
  }

  const mlat = Number(url.searchParams.get('mlat'))
  const mlon = Number(url.searchParams.get('mlon'))
  const fromOsmQuery = validCoordinates(mlat, mlon)
  if (fromOsmQuery) return fromOsmQuery

  const map = url.hash.match(new RegExp(`#map=\\d+/(${NUMBER})/(${NUMBER})`))
  return map ? validCoordinates(Number(map[1]), Number(map[2])) : null
}
