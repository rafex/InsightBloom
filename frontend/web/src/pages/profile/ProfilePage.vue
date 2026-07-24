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
        h3 Perfil público del organizador
        p.hint Si organizas un evento público, esta imagen aparecerá junto a tu nombre. Se guarda optimizada y sin SVG.
        .profile-photo-editor
          img.profile-photo(v-if="profilePhoto" :src="profilePhoto" alt="Vista previa de tu foto pública")
          .profile-photo.placeholder(v-else aria-hidden="true") 👤
          .photo-actions
            label.btn-secondary(for="profile-photo-input") Seleccionar foto
            input#profile-photo-input.hidden-input(type="file" accept="image/png,image/jpeg" @change="onPhotoSelected")
            button.btn-ghost(v-if="profilePhoto" type="button" @click="profilePhoto = ''") Quitar foto
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
import type { UserProfile } from '@/services/api/types'
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
    const profileData = ref<UserProfile>({ uuid: '' })
    const profilePhoto = ref('')
    const hasPassword = ref(true)

    const currentPassword = ref('')
    const newPassword = ref('')
    const changingPassword = ref(false)
    const passwordError = ref('')
    const passwordSuccess = ref(false)

    onMounted(async () => {
      try {
        const profile = await getUserProfile(auth.state.userUuid as string, auth.state.token as string)
        profileData.value = profile
        firstName.value = profile.firstName || ''
        lastName.value = profile.lastName || ''
        profilePhoto.value = profile.publicProfilePhotoBase64 || ''
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
        const updated = await updateUserProfile(auth.state.userUuid as string, {
          firstName: firstName.value, lastName: lastName.value,
          publicProfilePhotoBase64: profilePhoto.value || null
        }, auth.state.token as string)
        profileData.value = updated
        success.value = true
      } catch (e: any) {
        error.value = 'No se pudo guardar tu perfil.'
      } finally {
        saving.value = false
      }
    }

    function onPhotoSelected(event: Event) {
      const file = (event.target as HTMLInputElement).files?.[0]
      if (!file) return
      if (file.size > 3 * 1024 * 1024) {
        error.value = 'La imagen no puede superar 3 MB.'
        return
      }
      const reader = new FileReader()
      reader.onload = () => {
        const image = new Image()
        image.onload = () => {
          const max = 512
          const scale = Math.min(1, max / Math.max(image.width, image.height))
          const canvas = document.createElement('canvas')
          canvas.width = Math.max(1, Math.round(image.width * scale))
          canvas.height = Math.max(1, Math.round(image.height * scale))
          const context = canvas.getContext('2d')
          if (!context) return
          context.fillStyle = '#ffffff'
          context.fillRect(0, 0, canvas.width, canvas.height)
          context.drawImage(image, 0, 0, canvas.width, canvas.height)
          profilePhoto.value = canvas.toDataURL('image/jpeg', 0.85)
          error.value = ''
        }
        image.src = reader.result as string
      }
      reader.readAsDataURL(file)
    }

    async function changePasswordHandler() {
      passwordError.value = ''
      passwordSuccess.value = false
      changingPassword.value = true
      try {
        await changePassword(auth.state.userUuid as string, { currentPassword: currentPassword.value, newPassword: newPassword.value }, auth.state.token as string)
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
      loading, saving, error, success, firstName, lastName, save, profileData, profilePhoto, onPhotoSelected, hasPassword,
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
.profile-photo-editor { display: flex; align-items: center; gap: 16px; margin-bottom: 18px; }
.profile-photo { width: 88px; height: 88px; border-radius: 50%; object-fit: cover; border: 2px solid #e0e7ff; }
.profile-photo.placeholder { display: grid; place-items: center; background: #eef2ff; font-size: 2.3rem; }
.photo-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.btn-secondary, .btn-ghost { padding: 9px 12px; border-radius: 8px; cursor: pointer; font-size: .85rem; }
.btn-secondary { background: #4f46e5; color: #fff; font-weight: 700; }
.btn-ghost { background: #fff; color: #4f46e5; border: 1px solid #c7d2fe; }
.hidden-input { display: none; }
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
