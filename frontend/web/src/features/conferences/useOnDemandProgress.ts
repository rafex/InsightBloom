// Progreso de reproduccion del video on-demand (solo YouTube -- PeerTube no tiene API de
// currentTime/seekTo confirmada). Se guarda en localStorage bajo una clave por conferencia,
// siguiendo la convencion de prefijo `ib_` ya usada en authStore.ts/OnboardingTour.vue. No hay
// backend involucrado a proposito: es progreso por navegador/dispositivo, no cross-device.

function storageKey(conferenceId: string): string {
  return `ib_ondemand_progress_${conferenceId}`
}

/** Segundos guardados para esta conferencia, o null si no hay progreso previo. */
export function getSavedProgress(conferenceId: string): number | null {
  try {
    const raw = localStorage.getItem(storageKey(conferenceId))
    if (!raw) return null
    const seconds = Number(raw)
    return Number.isFinite(seconds) && seconds >= 0 ? seconds : null
  } catch {
    return null
  }
}

export function saveProgress(conferenceId: string, seconds: number): void {
  try {
    localStorage.setItem(storageKey(conferenceId), String(Math.floor(seconds)))
  } catch { /* localStorage puede fallar en modo privado/cuota llena -- no es critico */ }
}

export function clearProgress(conferenceId: string): void {
  try {
    localStorage.removeItem(storageKey(conferenceId))
  } catch { /* ver saveProgress */ }
}
