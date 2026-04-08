<template lang="pug">
.login-page
  AppHeader
  main.login-main
    .login-card.animate__animated.animate__fadeIn
      h2 Acceso organizador
      p.hint PoC: ingresa cualquier usuario registrado (ej: admin)
      .form-group
        label Usuario
        input(v-model="username" type="text" placeholder="admin" @keyup.enter="doLogin")
      .error(v-if="error") {{ error }}
      button.btn-primary(@click="doLogin" :disabled="loading")
        span(v-if="loading") Entrando...
        span(v-else) Iniciar sesión
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'LoginPage',
  components: { AppHeader },
  setup() {
    const username = ref('admin')
    const error = ref('')
    const loading = ref(false)
    const router = useRouter()
    const auth = useAuthStore()
    async function doLogin() {
      if (!username.value.trim()) return
      loading.value = true; error.value = ''
      try {
        await auth.login(username.value.trim())
        router.push('/dashboard')
      } catch (e) {
        error.value = 'Usuario no encontrado o error de conexión'
      } finally {
        loading.value = false
      }
    }
    return { username, error, loading, doLogin }
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
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
</style>
