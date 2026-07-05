import { ref } from 'vue'
import { useAuthStore } from './authStore'

const CHECK_INTERVAL_MS = 30_000
const SILENT_REFRESH_THRESHOLD_MS = 2 * 60_000 // refrescar si faltan >2min y hubo actividad reciente
const ACTIVITY_WINDOW_MS = 5 * 60_000 // "actividad reciente" = últimos 5min
const WARNING_THRESHOLD_MS = 60_000 // mostrar modal cuando falta <=1min

let lastActivityAt = Date.now()
let activityListenersAttached = false

function attachActivityListeners() {
  if (activityListenersAttached || typeof window === 'undefined') return
  activityListenersAttached = true
  const mark = () => { lastActivityAt = Date.now() }
  ;['mousemove', 'keydown', 'click', 'touchstart'].forEach((evt) => {
    window.addEventListener(evt, mark, { passive: true })
  })
}

export function useSessionManager() {
  const auth = useAuthStore()
  const showWarning = ref(false)
  const secondsRemaining = ref(0)
  let intervalId = null
  let countdownId = null

  function clearCountdown() {
    if (countdownId) clearInterval(countdownId)
    countdownId = null
  }

  async function forceLogoutAndRedirect() {
    clearCountdown()
    showWarning.value = false
    await auth.logout()
    if (window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
  }

  function startCountdown(msRemaining) {
    if (countdownId) return
    secondsRemaining.value = Math.max(0, Math.ceil(msRemaining / 1000))
    countdownId = setInterval(() => {
      secondsRemaining.value -= 1
      if (secondsRemaining.value <= 0) {
        forceLogoutAndRedirect()
      }
    }, 1000)
  }

  async function keepConnected() {
    const ok = await auth.refresh()
    clearCountdown()
    showWarning.value = false
    if (!ok) await forceLogoutAndRedirect()
  }

  async function tick() {
    if (!auth.state.token || !auth.state.expiresAt) return
    const msRemaining = new Date(auth.state.expiresAt).getTime() - Date.now()

    if (msRemaining <= 0) {
      await forceLogoutAndRedirect()
      return
    }

    if (msRemaining <= WARNING_THRESHOLD_MS) {
      if (!showWarning.value) {
        showWarning.value = true
        startCountdown(msRemaining)
      }
      return
    }

    const activeRecently = (Date.now() - lastActivityAt) <= ACTIVITY_WINDOW_MS
    if (msRemaining <= SILENT_REFRESH_THRESHOLD_MS + CHECK_INTERVAL_MS && activeRecently) {
      await auth.refresh()
    }
  }

  function start() {
    attachActivityListeners()
    if (intervalId) return
    intervalId = setInterval(tick, CHECK_INTERVAL_MS)
    tick()
  }

  function stop() {
    if (intervalId) clearInterval(intervalId)
    intervalId = null
    clearCountdown()
  }

  return { showWarning, secondsRemaining, keepConnected, start, stop }
}
