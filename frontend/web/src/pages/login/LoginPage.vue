<template lang="pug">
.login-page
  AppHeader
  main.login-main
    .login-card.animate__animated.animate__fadeIn
      h2 Iniciar sesión
      p.hint Inicia sesión con tu correo electrónico verificado
      .form-group
        label Correo electrónico
        input(v-model="username" type="email" autocomplete="username" placeholder="tu@correo.com" @keyup.enter="doLogin")
      .form-group
        label Contraseña
        input(v-model="password" type="password" autocomplete="current-password" placeholder="••••••••" @keyup.enter="doLogin")
      .error(v-if="error") {{ error }}
      button.btn-primary(@click="doLogin" :disabled="loading")
        span(v-if="loading") Entrando...
        span(v-else) Iniciar sesión
      p.register-hint ¿No tienes cuenta? #[router-link(to="/register") Regístrate]
      //- Entrada como invitado visible solo cuando se llegó desde un evento (auditoría UX: el
      //- modo anónimo existía pero solo se descubría por accidente dentro de la página del
      //- boleto — y es justo la opción de menor fricción para quien escaneó un QR).
      .guest-block(v-if="guestTarget")
        .divider o
        button.btn-guest(type="button" @click="continueAsGuest") Continuar como invitado
        p.guest-hint Entrás al evento sin cuenta: podés ver la presentación y participar de forma limitada, pero sin certificado ni historial.
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'LoginPage',
  components: { AppHeader },
  setup() {
    const username = ref('')
    const password = ref('')
    const error = ref('')
    const loading = ref(false)
    const router = useRouter()
    const route = useRoute()
    const auth = useAuthStore()

    // Solo se ofrece "invitado" si venimos redirigidos desde la página de un evento (/c/...):
    // sin contexto de evento no hay a dónde entrar como invitado.
    const redirectPath = String(route.query.redirect || '')
    const guestTarget = redirectPath.startsWith('/c/') ? redirectPath : ''
    function continueAsGuest() { router.push(guestTarget) }
    async function doLogin() {
      if (!username.value.trim() || !password.value.trim()) {
        error.value = 'Correo y contraseña son obligatorios'
        return
      }
      loading.value = true; error.value = ''
      try {
        await auth.login(username.value.trim(), password.value)
        router.push(String(route.query.redirect || '/dashboard'))
      } catch (e: any) {
        // Distinguir "te equivocaste" de "no hay conexion": la accion correctiva es opuesta
        // (corregir credenciales vs reintentar) y el mensaje mezclado no dejaba saber cual.
        const status = e?.response?.status
        if (status === 403) {
          error.value = 'Este dispositivo fue bloqueado por uso indebido de la plataforma. Contactá a un administrador.'
        } else if (status === 401 || status === 400) {
          error.value = 'Correo o contraseña incorrectos.'
        } else if (!e?.response) {
          error.value = 'No se pudo conectar con el servidor. Verificá tu conexión y reintentá.'
        } else {
          error.value = 'El servidor respondió con un error inesperado. Reintentá en unos segundos.'
        }
      } finally {
        loading.value = false
      }
    }
    return { username, password, error, loading, doLogin, guestTarget, continueAsGuest }
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; background: #f5f3ff; }
.login-main { display: flex; justify-content: center; padding: 80px 24px; }
.login-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 400px; width: 100%; }
h2 { margin: 0 0 8px; color: #1e1b4b; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 24px; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; }
input:focus { outline: none; border-color: #4f46e5; }
.btn-primary { width: 100%; padding: 12px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.guest-block { margin-top: 14px; text-align: center; }
.divider { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 10px; }
.btn-guest {
  width: 100%; padding: 10px; background: var(--color-surface); color: var(--color-primary);
  border: 1px solid var(--color-primary-border); border-radius: var(--radius-md);
  cursor: pointer; font-size: 0.95rem; font-weight: 600;
}
.btn-guest:hover { background: var(--color-primary-soft); }
.guest-hint { margin: 8px 0 0; font-size: 0.8rem; color: var(--color-text-muted); }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.register-hint { text-align: center; margin: 16px 0 0; font-size: 0.85rem; color: #6b7280; }
.register-hint a { color: #4f46e5; font-weight: 600; text-decoration: none; }
.register-hint a:hover { text-decoration: underline; }

@media (max-width: 480px) {
  .login-main { padding: 32px 16px; }
  .login-card { padding: 24px; }
}
</style>
