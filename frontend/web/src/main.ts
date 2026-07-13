import { createApp } from 'vue'
import axios, { type AxiosError } from 'axios'
import App from './App.vue'
import router from './app/router'
import 'animate.css'
import './styles/global.css'

// Sesión revocada o expirada del lado del servidor: limpiar y redirigir sin
// pasar por authStore.logout() (evita re-disparar otra llamada autenticada).
// Solo aplica si ya creiamos tener sesion (habia token guardado): un 401 de una
// llamada opcional hecha en modo anonimo (ej. jaas-token en VideoConferencePage,
// que se llama para todos los visitantes y espera 401 sin sesion para recaer en
// Jitsi publico) NO debe expulsar al usuario a /login.
axios.interceptors.response.use(
  (res) => res,
  (err: AxiosError) => {
    if (err.response?.status === 401 && localStorage.getItem('ib_token')) {
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
