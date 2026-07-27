// Cola global de toasts (auditoría UX 2026-07-26): la app no tenía ningún mecanismo de
// notificación — todo éxito/error era texto inline con 3 rojos y 2 verdes distintos según la
// página. Estado module-scope a propósito (mismo patrón que authStore): un solo montaje de
// <AppToast> en App.vue lo consume.
import { ref } from 'vue'

export interface ToastItem {
  id: number
  kind: 'success' | 'error' | 'info'
  message: string
}

const items = ref<ToastItem[]>([])
let seq = 0

function push(kind: ToastItem['kind'], message: string, timeoutMs = 5000) {
  const id = ++seq
  items.value.push({ id, kind, message })
  if (timeoutMs > 0) setTimeout(() => dismiss(id), timeoutMs)
}

function dismiss(id: number) {
  items.value = items.value.filter((t) => t.id !== id)
}

export function useToasts() {
  return {
    items,
    dismiss,
    success: (msg: string) => push('success', msg),
    error: (msg: string, timeoutMs = 8000) => push('error', msg, timeoutMs),
    info: (msg: string) => push('info', msg)
  }
}
