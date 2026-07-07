import { createApp } from 'vue'
import axios, { type AxiosError } from 'axios'
import App from './App.vue'
import router from './app/router'
import 'animate.css'
import './styles/global.css'

// Sesión revocada o expirada del lado del servidor: limpiar y redirigir sin
// pasar por authStore.logout() (evita re-disparar otra llamada autenticada).
axios.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('ib_token')
      localStorage.removeItem('ib_role')
      localStorage.removeItem('ib_user_uuid')
      localStorage.removeItem('ib_expires_at')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

const app = createApp(App)
app.use(router)
app.mount('#app')
