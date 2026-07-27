// Semántica canónica de capabilities por tipo de evento (auditoría UX 2026-07-26): estaba
// reimplementada con variaciones en ConferenceToolsNav, ConferencesListPage y ConferencePage.
// Regla compartida por las tres: si el catálogo aún no cargó (o falló el fetch), NO se ocultan
// herramientas — el backend sigue siendo la autoridad (responde 409/403 si algo no aplica) y así
// se evita el parpadeo de tabs al cargar.
import type { EventType, EventCapability } from '@/services/api/types'

export function eventTypeHasCapability(
  eventTypes: EventType[],
  eventTypeKey: string | undefined,
  capability: EventCapability
): boolean {
  if (eventTypes.length === 0) return true
  const type = eventTypes.find((t) => t.key === eventTypeKey)
  return type ? type.capabilities.includes(capability) : true
}
