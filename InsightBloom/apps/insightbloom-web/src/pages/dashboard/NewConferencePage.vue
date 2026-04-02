<template lang="pug">
.new-conf-page
  h2 Nueva conferencia
  .form(v-if="!created")
    .form-group
      label Nombre de la conferencia
      input(v-model="name" type="text" placeholder="Conferencia IA 2026" @keyup.enter="create")
    .error(v-if="error") {{ error }}
    button.btn-primary(@click="create" :disabled="loading || !name.trim()")
      span(v-if="loading") Creando...
      span(v-else) Crear conferencia
  .created-info.animate__animated.animate__fadeIn(v-else)
    h3 ¡Conferencia creada!
    .info-row
      span Nombre:
      strong {{ created.name }}
    .info-row
      span ID amigable:
      .friendly-id {{ created.friendlyId }}
    .info-row
      span Link público:
      a(:href="`/c/${created.friendlyId}/doubts`" target="_blank") /c/{{ created.friendlyId }}
    .actions
      router-link.btn-outline(:to="`/dashboard/conferences/${created.conferenceId}/moderation/messages`") Ver moderación mensajes
      router-link.btn-outline(:to="`/dashboard/conferences/${created.conferenceId}/moderation/words`") Ver moderación palabras
      button.btn-primary(@click="reset") Crear otra
</template>

<script>
import { ref } from 'vue'
import { createConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
export default {
  name: 'NewConferencePage',
  setup() {
    const name = ref('')
    const error = ref('')
    const loading = ref(false)
    const created = ref(null)
    const auth = useAuthStore()
    async function create() {
      if (!name.value.trim()) return
      loading.value = true; error.value = ''
      try {
        created.value = await createConference(name.value.trim(), auth.state.token)
      } catch (e) {
        error.value = e.response?.data?.error?.message || 'Error al crear la conferencia'
      } finally { loading.value = false }
    }
    function reset() { name.value = ''; created.value = null }
    return { name, error, loading, created, create, reset }
  }
}
</script>

<style scoped>
.new-conf-page { max-width: 560px; }
h2 { color: #1e1b4b; margin-bottom: 24px; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; }
input:focus { outline: none; border-color: #4f46e5; }
.btn-primary { padding: 10px 22px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.created-info { background: #f0fdf4; border: 1.5px solid #86efac; border-radius: 12px; padding: 24px; }
h3 { color: #166534; margin: 0 0 16px; }
.info-row { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; font-size: 0.95rem; }
.info-row span { color: #6b7280; min-width: 100px; }
.friendly-id { font-family: monospace; background: #dcfce7; padding: 4px 10px; border-radius: 6px; font-size: 1.1rem; font-weight: 700; color: #14532d; letter-spacing: 1px; }
.actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 20px; }
.btn-outline { padding: 8px 16px; border: 1.5px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 0.9rem; }
</style>
