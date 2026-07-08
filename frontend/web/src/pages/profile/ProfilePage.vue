<template lang="pug">
.profile-page
  AppHeader
  main.profile-main
    .profile-card
      h2 Mi perfil
      .profile-loading(v-if="loading") Cargando...
      template(v-else)
        .registration-data
          h3 Datos de registro
          .data-row
            span.data-label Correo electrónico
            span.data-value {{ profileData.email || '—' }}
            span.badge-verified(v-if="profileData.email && profileData.emailVerified") ✓ verificado
          .data-row(v-if="profileData.phone")
            span.data-label Teléfono
            span.data-value {{ profileData.phone }}
            span.badge-verified(v-if="profileData.phoneVerified") ✓ verificado
          .data-row(v-if="profileData.socialLinks && profileData.socialLinks.length")
            span.data-label Redes sociales
            .social-list
              span.social-chip(v-for="l in profileData.socialLinks" :key="l.platform") {{ l.platform }}: {{ l.url }}

        h3 Nombre para tu certificado
        p.hint Completa tu nombre y apellido para personalizar tu certificado de asistencia.
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

        h3.password-title Cambiar contraseña
        .form-group(v-if="hasPassword")
          label Contraseña actual
          input(v-model="currentPassword" type="password" placeholder="••••••••")
        .form-group
          label Nueva contraseña
          input(v-model="newPassword" type="password" placeholder="••••••••")
        .error(v-if="passwordError") {{ passwordError }}
        .success(v-if="passwordSuccess") ¡Contraseña actualizada!
        button.btn-primary(@click="changePassword" :disabled="changingPassword || !newPassword")
          span(v-if="changingPassword") Guardando...
          span(v-else) Cambiar contraseña
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref, onMounted } from 'vue'
import { getUserProfile, updateUserProfile, changePassword } from '@/services/api/usersApi'
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
    const profileData = ref({})
    const hasPassword = ref(true)

    const currentPassword = ref('')
    const newPassword = ref('')
    const changingPassword = ref(false)
    const passwordError = ref('')
    const passwordSuccess = ref(false)

    onMounted(async () => {
      try {
        const profile = await getUserProfile(auth.state.userUuid)
        profileData.value = profile
        firstName.value = profile.firstName || ''
        lastName.value = profile.lastName || ''
      } catch (e: any) {
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
      } catch (e: any) {
        error.value = 'No se pudo guardar tu perfil.'
      } finally {
        saving.value = false
      }
    }

    async function changePasswordHandler() {
      passwordError.value = ''
      passwordSuccess.value = false
      changingPassword.value = true
      try {
        await changePassword(auth.state.userUuid, { currentPassword: currentPassword.value, newPassword: newPassword.value }, auth.state.token)
        passwordSuccess.value = true
        currentPassword.value = ''
        newPassword.value = ''
      } catch (e: any) {
        passwordError.value = e.response?.status === 400
          ? 'La contraseña actual es incorrecta.'
          : 'No se pudo cambiar tu contraseña.'
      } finally {
        changingPassword.value = false
      }
    }

    return {
      loading, saving, error, success, firstName, lastName, save, profileData, hasPassword,
      currentPassword, newPassword, changingPassword, passwordError, passwordSuccess,
      changePassword: changePasswordHandler
    }
  }
}
</script>

<style scoped>
.profile-page { min-height: 100vh; background: #f5f3ff; }
.profile-main { display: flex; justify-content: center; padding: 60px 24px; }
.profile-card { background: #fff; border-radius: 16px; padding: 40px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); max-width: 460px; width: 100%; }
h2 { margin: 0 0 8px; color: #1e1b4b; }
h3 { margin: 24px 0 12px; color: #1e1b4b; font-size: 1rem; }
h3:first-of-type { margin-top: 8px; }
.password-title { border-top: 1px solid #e5e7eb; padding-top: 20px; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 16px; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; }
input:focus { outline: none; border-color: #4f46e5; }
.btn-primary { width: 100%; padding: 12px; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 1rem; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.success { color: #059669; font-size: 0.9rem; margin-bottom: 12px; }
.profile-loading { color: #6b7280; }

.registration-data { background: #f9fafb; border-radius: 10px; padding: 16px; margin-bottom: 8px; }
.data-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 6px 0; font-size: 0.9rem; }
.data-label { color: #6b7280; min-width: 130px; font-weight: 500; }
.data-value { color: #1e1b4b; font-weight: 500; }
.badge-verified { font-size: 0.72rem; color: #059669; background: #d1fae5; padding: 2px 8px; border-radius: 99px; }
.social-list { display: flex; flex-direction: column; gap: 4px; }
.social-chip { font-size: 0.82rem; color: #374151; }

@media (max-width: 480px) {
  .profile-main { padding: 24px 16px; }
  .profile-card { padding: 24px; }
  .data-label { min-width: 100px; }
}
</style>
