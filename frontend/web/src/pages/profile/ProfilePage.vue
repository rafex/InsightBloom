<template lang="pug">
.profile-page
  AppHeader
  main#main-content.profile-main(tabindex="-1")
    .profile-card
      h2 Mi perfil
      LoadingState(v-if="loading" message="Cargando perfil…")
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
        SaveState(:state="saveState")
        FormField(label="Nombre")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="firstName" placeholder="Tu nombre")
        FormField(label="Apellido")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="lastName" placeholder="Tu apellido")
        h3 Perfil público del organizador
        p.hint Si organizas un evento público, esta imagen aparecerá junto a tu nombre. Se guarda optimizada y sin SVG.
        .profile-photo-editor
          img.profile-photo(v-if="profilePhoto" :src="profilePhoto" alt="Vista previa de tu foto pública")
          .profile-photo.placeholder(v-else aria-hidden="true") 👤
          .photo-actions
            label.link-btn.link-btn-secondary.link-btn-sm(for="profile-photo-input") Seleccionar foto
            input#profile-photo-input.hidden-input(type="file" accept="image/png,image/jpeg" @change="onPhotoSelected")
            BaseButton(variant="ghost" size="sm" v-if="profilePhoto" type="button" @click="profilePhoto = ''") Quitar foto
        FeedbackMessage(v-if="error" :message="error" tone="error")
        FeedbackMessage(v-if="success" message="¡Perfil actualizado!" tone="success")
        BaseButton(size="lg" :loading="saving" :disabled="saving || saveState === 'clean' || saveState === 'saved'" @click="save") Guardar

        h3.password-title Cambiar contraseña
        FormField(v-if="hasPassword" label="Contraseña actual")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="currentPassword" type="password" placeholder="••••••••")
        FormField(label="Nueva contraseña")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="newPassword" type="password" placeholder="••••••••")
        FeedbackMessage(v-if="passwordError" :message="passwordError" tone="error")
        FeedbackMessage(v-if="passwordSuccess" message="¡Contraseña actualizada!" tone="success")
        BaseButton(size="lg" :disabled="changingPassword || !newPassword" @click="changePassword") Cambiar contraseña

        h3.password-title Método de acceso
        p.hint(v-if="profileData.authMethod === 'OTP_EMAIL'") Activo: código de acceso por correo. Entrás con un código de 6 dígitos que te mandamos a tu correo, ya no con tu contraseña.
        p.hint(v-else) Activo: contraseña. Podés cambiar a un código de acceso por correo — cada inicio de sesión te va a pedir un código nuevo enviado a tu correo, en vez de tu contraseña.
        FormField(label="Confirmá tu contraseña actual para cambiar el método")
          template(#default="{ id, describedBy }")
            input(:id="id" :aria-describedby="describedBy" v-model="authMethodPassword" type="password" placeholder="••••••••")
        FeedbackMessage(v-if="authMethodError" :message="authMethodError" tone="error")
        FeedbackMessage(v-if="authMethodSuccess" message="¡Método de acceso actualizado!" tone="success")
        BaseButton(
          size="lg"
          variant="secondary"
          :disabled="changingAuthMethod || !authMethodPassword"
          @click="toggleAuthMethod"
        ) {{ profileData.authMethod === 'OTP_EMAIL' ? 'Volver a usar contraseña' : 'Activar código por correo' }}
</template>

<script lang="ts">
import AppHeader from '@/app/layout/AppHeader.vue'
import { ref, computed, onMounted } from 'vue'
import { getUserProfile, updateUserProfile, changePassword, setAuthMethod } from '@/services/api/usersApi'
import type { AuthMethod } from '@/services/api/usersApi'
import type { UserProfile } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import FormField from '@/components/ui/FormField.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'

export default {
  name: 'ProfilePage',
  components: { AppHeader, BaseButton, FeedbackMessage, FormField, LoadingState, SaveState },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const saving = ref(false)
    const error = ref('')
    const success = ref(false)
    const initialProfile = ref('')
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

    const authMethodPassword = ref('')
    const changingAuthMethod = ref(false)
    const authMethodError = ref('')
    const authMethodSuccess = ref(false)

    const profileSnapshot = () => JSON.stringify({
      firstName: firstName.value,
      lastName: lastName.value,
      profilePhoto: profilePhoto.value
    })
    const saveState = computed(() => {
      if (saving.value) return 'saving'
      if (profileSnapshot() !== initialProfile.value) return 'dirty'
      if (success.value) return 'saved'
      return 'clean'
    })

    onMounted(async () => {
      try {
        const profile = await getUserProfile(auth.state.userUuid as string, auth.state.token as string)
        profileData.value = profile
        firstName.value = profile.firstName || ''
        lastName.value = profile.lastName || ''
        profilePhoto.value = profile.publicProfilePhotoBase64 || ''
        initialProfile.value = profileSnapshot()
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
        initialProfile.value = profileSnapshot()
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
          context.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--color-text-inverse').trim()
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

    async function toggleAuthMethod() {
      authMethodError.value = ''
      authMethodSuccess.value = false
      changingAuthMethod.value = true
      try {
        const newMethod: AuthMethod = profileData.value.authMethod === 'OTP_EMAIL' ? 'PASSWORD' : 'OTP_EMAIL'
        const result = await setAuthMethod(auth.state.userUuid as string,
          { currentPassword: authMethodPassword.value, newMethod }, auth.state.token as string)
        profileData.value = { ...profileData.value, authMethod: result.authMethod }
        authMethodPassword.value = ''
        authMethodSuccess.value = true
      } catch (e: any) {
        authMethodError.value = e.response?.status === 400
          ? 'La contraseña actual es incorrecta.'
          : 'No se pudo cambiar el método de acceso.'
      } finally {
        changingAuthMethod.value = false
      }
    }

    return {
      loading, saving, error, success, saveState, firstName, lastName, save, profileData, profilePhoto, onPhotoSelected, hasPassword,
      currentPassword, newPassword, changingPassword, passwordError, passwordSuccess,
      changePassword: changePasswordHandler,
      authMethodPassword, changingAuthMethod, authMethodError, authMethodSuccess, toggleAuthMethod
    }
  }
}
</script>

<style scoped>
.profile-page { min-height: 100vh; background: var(--color-bg); }
.profile-main { display: flex; justify-content: center; padding: 60px 24px; }
.profile-card { background: var(--color-surface); border-radius: 16px; padding: 40px; box-shadow: var(--shadow-card); max-width: 460px; width: 100%; }
h2 { margin: 0 0 8px; color: var(--color-heading); }
h3 { margin: 24px 0 12px; color: var(--color-heading); font-size: 1rem; }
h3:first-of-type { margin-top: 8px; }
.password-title { border-top: 1px solid var(--color-border-subtle); padding-top: 20px; }
.hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 16px; }
.hidden-input { display: none; }

.registration-data { background: var(--color-surface-muted); border-radius: 10px; padding: 16px; margin-bottom: 8px; }
.data-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 6px 0; font-size: 0.9rem; }
.data-label { color: var(--color-text-muted); min-width: 130px; font-weight: 500; }
.data-value { color: var(--color-heading); font-weight: 500; }
.badge-verified { font-size: 0.72rem; color: var(--color-success); background: var(--color-success-soft); padding: 2px 8px; border-radius: 99px; }
.social-list { display: flex; flex-direction: column; gap: 4px; }
.social-chip { font-size: 0.82rem; color: var(--color-text-secondary); }

@media (max-width: 480px) {
  .profile-main { padding: 24px 16px; }
  .profile-card { padding: 24px; }
  .data-label { min-width: 100px; }
}
</style>
