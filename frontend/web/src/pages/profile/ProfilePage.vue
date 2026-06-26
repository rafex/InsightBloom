<template lang="pug">
.profile-page
  AppHeader
  main.profile-main
    .profile-card
      h2 Mi perfil
      p.hint Completa tu nombre y apellido para personalizar tu certificado de asistencia.
      .profile-loading(v-if="loading") Cargando...
      template(v-else)
        .form-group
          label Nombre
          input(v-model="firstName" placeholder="Tu nombre")
        .form-group
          label Apellido
          input(v-model="lastName" placeholder="Tu apellido")
        .error(v-if="error") {{ error }}
        .success(v-if="success") ¡Perfil actualizado!
        button.btn-primary(@click="save" :disabled="saving")
          span(v-if="saving") Guardando...
          span(v-else) Guardar
</template>

<script>
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref, onMounted } from 'vue'
import { getUserProfile, updateUserProfile } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'ProfilePage',
  components: { AppHeader },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const saving = ref(false)
    const error = ref('')
    const success = ref(false)
    const firstName = ref('')
    const lastName = ref('')

    onMounted(async () => {
      try {
        const profile = await getUserProfile(auth.state.userUuid)
        firstName.value = profile.firstName || ''
        lastName.value = profile.lastName || ''
      } catch (e) {
        error.value = 'No se pudo cargar tu perfil.'
      } finally {
        loading.value = false
      }
    })

    async function save() {
      error.value = ''
      success.value = false
      saving.value = true
      try {
        await updateUserProfile(auth.state.userUuid, { firstName: firstName.value, lastName: lastName.value }, auth.state.token)
        success.value = true
      } catch (e) {
        error.value = 'No se pudo guardar tu perfil.'
      } finally {
        saving.value = false
      }
    }

    return { loading, saving, error, success, firstName, lastName, save }
  }
}
</script>

<style scoped>
.profile-page { min-height: 100vh; background: #f5f3ff; }
.profile-main { display: flex; justify-content: center; padding: 60px 24px; }
.profile-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 460px; width: 100%; }
h2 { margin: 0 0 8px; color: #1e1b4b; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 24px; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; }
input:focus { outline: none; border-color: #4f46e5; }
.btn-primary { width: 100%; padding: 12px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.success { color: #059669; font-size: 0.9rem; margin-bottom: 12px; }
.profile-loading { color: #6b7280; }
</style>
