import { createApp } from 'vue'
import axios, { type AxiosError } from 'axios'
import App from './App.vue'
import router from './app/router'
import { getFingerprint } from '@/services/auth/fingerprint'
import { handleSessionAxiosError } from '@/features/auth/sessionGuard'
import 'animate.css'
import './styles/global.css'
import 'survey-core/survey-core.css'

// Adjunta la huella real (ThumbmarkJS) a TODA llamada autenticada, sin tocar cada funcion de
// usersApi.ts/authApi.ts individualmente. El backend la compara contra la que se guardo al
// emitir el token en el login (DeviceFingerprintAuditHandler) -- si no coincide, solo lo audita
// para revision de un admin, nunca corta la sesión (ver docs/device-fingerprinting.md).
axios.interceptors.request.use(async (config) => {
  if (localStorage.getItem('ib_token')) {
    try {
      config.headers = config.headers || {}
      config.headers['X-Device-Fingerprint'] = await getFingerprint()
    } catch (e) { /* best-effort: nunca debe bloquear el request real */ }
  }
  return config
})

axios.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    handleSessionAxiosError(err)
    return Promise.reject(err)
  }
)

const app = createApp(App)
app.use(router)
app.mount('#app')
