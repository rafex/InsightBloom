<template lang="pug">
.join-page
  h1 Unirse a una conferencia
  p.hint Ingresa el código de la conferencia (ID amigable, código corto o UUID)
  .form-row
    input(v-model="code" placeholder="ej. taller-uptlax" @keyup.enter="doJoin")
    button.btn-primary(:disabled="!code.trim() || loading" @click="doJoin") {{ loading ? 'Uniendo...' : 'Unirme' }}
  p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { joinConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'JoinConferencePage',
  setup() {
    const router = useRouter()
    const auth = useAuthStore()
    const code = ref('')
    const loading = ref(false)
    const error = ref('')

    async function doJoin() {
      error.value = ''
      loading.value = true
      try {
        const conference = await joinConference(code.value.trim(), auth.state.token)
        router.push(`/c/${conference.friendlyId}/doubts`)
      } catch (e: any) {
        error.value = e.response?.status === 404
          ? 'Esta conferencia ya no se encuentra disponible o el código es incorrecto.'
          : 'No se pudo unir a la conferencia. Intenta de nuevo.'
      } finally {
        loading.value = false
      }
    }

    return { code, loading, error, doJoin }
  }
}
</script>

<style scoped>
.join-page { max-width: 480px; }
h1 { color: #1e1b4b; margin: 0 0 8px; font-size: 1.5rem; }
.hint { color: #6b7280; font-size: 0.9rem; margin-bottom: 20px; }
.form-row { display: flex; gap: 8px; }
input {
  flex: 1; padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
input:focus { outline: none; border-color: #4f46e5; }
.btn-primary {
  padding: 10px 20px; background: #4f46e5; color: #fff; border: none; border-radius: 8px;
  cursor: pointer; font-weight: 600; font-size: 0.95rem;
}
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.error { color: #dc2626; margin-top: 12px; font-size: 0.9rem; }

@media (max-width: 420px) {
  .form-row { flex-direction: column; }
}
</style>
