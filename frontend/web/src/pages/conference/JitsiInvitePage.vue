<template lang="pug">
.jitsi-invite-page
  .invite-card(v-if="loading")
    h1 Validando acceso
    LoadingState(message="Espera un momento mientras comprobamos tu sesión y tu boleto.")
  .invite-card(v-else-if="errorCode === 'login_required'")
    .icon(aria-hidden="true") 🔐
    h1 Inicia sesión para entrar
    p La videollamada sólo está disponible para usuarios con sesión iniciada y acceso al evento.
    .actions
      BaseLink(:to="{ path: '/login', query: { redirect: invitePath } }") Iniciar sesión
      BaseLink(variant="secondary" :to="{ path: '/register', query: { redirect: invitePath } }") Crear cuenta
  .invite-card(v-else-if="errorCode === 'ticket_required'")
    .icon(aria-hidden="true") 🎟️
    h1 Boleto requerido
    p Necesitas un boleto vigente para acceder a la videollamada de este evento.
    .actions
      BaseLink(:to="`/c/${friendlyId}/ticket`") Ver mi boleto
      BaseLink(variant="secondary" :to="`/c/${friendlyId}/presentation`") Volver al evento
  .invite-card(v-else)
    .icon(aria-hidden="true") 🚫
    h1 No se pudo abrir la videollamada
    p {{ errorMessage }}
    .actions
      BaseLink(variant="secondary" :to="`/c/${friendlyId}/presentation`") Volver al evento
</template>

<script lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
import { getJitsiInviteAccess } from '@/services/api/usersApi'
import BaseLink from '@/components/ui/BaseLink.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

export default {
  name: 'JitsiInvitePage',
  components: { BaseLink, LoadingState },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const auth = useAuthStore()
    const friendlyId = String(route.params.friendlyId || '')
    const loading = ref(true)
    const errorCode = ref('')
    const errorMessage = ref('La invitación no es válida o el evento no tiene videollamada habilitada.')
    const invitePath = `/jitsi/${encodeURIComponent(friendlyId)}`
    const isUserSession = computed(() => auth.isAuthenticated() && auth.state.role !== 'guest')

    onMounted(async () => {
      if (!isUserSession.value || !auth.state.token) {
        errorCode.value = 'login_required'
        loading.value = false
        return
      }
      try {
        await getJitsiInviteAccess(friendlyId, auth.state.token)
        await router.replace(`/c/${encodeURIComponent(friendlyId)}/video`)
      } catch (error: any) {
        const code = error?.response?.data?.error
        errorCode.value = code === 'ticket_required' ? 'ticket_required' : 'access_denied'
        if (code === 'capability_not_available') {
          errorMessage.value = 'Este evento no tiene la videollamada habilitada.'
        } else if (code === 'conference_not_found') {
          errorMessage.value = 'El evento no existe o ya no está disponible.'
        } else if (code !== 'ticket_required') {
          errorMessage.value = 'No se pudo validar tu acceso. Intenta nuevamente desde el evento.'
        }
        loading.value = false
      }
    })

    return { friendlyId, loading, errorCode, errorMessage, invitePath }
  }
}
</script>

<style scoped>
.jitsi-invite-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: var(--color-bg); color: var(--color-heading); font-family: system-ui, sans-serif; }
.invite-card { width: min(100%, 460px); padding: 36px 28px; text-align: center; background: var(--color-surface); border: 1px solid var(--color-primary-border); border-radius: 18px; box-shadow: 0 12px 32px rgba(49, 46, 129, .12); }
.invite-card h1 { margin: 12px 0 8px; font-size: 1.45rem; }
.invite-card p { margin: 0; color: var(--color-text-muted); line-height: 1.55; }
.icon { font-size: 2.25rem; }
.actions { display: flex; justify-content: center; flex-wrap: wrap; gap: 10px; margin-top: 24px; }
</style>
