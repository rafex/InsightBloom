// Envoltorio fino sobre la Notification API del navegador -- sin infra de Web Push (VAPID,
// service worker con push handler): solo dispara mientras hay una pestaña abierta y en background
// (document.hidden), que es lo que la campana in-app ya cubre cuando la pestaña está al frente.
// El propio navegador recuerda si el usuario ya concedió/denegó el permiso -- no hace falta
// guardar esa preferencia por nuestra cuenta, alcanza con no volver a pedirlo si ya se resolvió.

let permissionRequested = false

export function isBrowserNotificationSupported(): boolean {
  return typeof window !== 'undefined' && 'Notification' in window
}

async function ensurePermission(): Promise<boolean> {
  if (!isBrowserNotificationSupported()) return false
  if (Notification.permission === 'granted') return true
  if (Notification.permission === 'denied') return false
  if (permissionRequested) return false
  permissionRequested = true
  try {
    const result = await Notification.requestPermission()
    return result === 'granted'
  } catch {
    return false
  }
}

/** Dispara una notificación del navegador solo si la pestaña está oculta -- si está al frente,
 * la campana in-app ya es visible y una notificación del SO sería redundante. */
export async function notifyBrowserIfHidden(title: string, options?: NotificationOptions): Promise<void> {
  if (!document.hidden) return
  const allowed = await ensurePermission()
  if (!allowed) return
  try {
    new Notification(title, options)
  } catch {
    // Algunos navegadores (ej. Safari en ciertas versiones) pueden rechazar la construcción
    // directa fuera de un service worker -- se ignora, la campana in-app sigue siendo la fuente
    // de verdad.
  }
}
