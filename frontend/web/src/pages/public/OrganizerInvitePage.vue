<template lang="pug">
.invite-page
  AppHeader
  main#main-content.invite-main(tabindex="-1")
    section.invite-card(aria-labelledby="invite-title" aria-live="polite")
      .invite-mark(aria-hidden="true") ✉
      h1#invite-title Invitación para organizar eventos
      LoadingState(v-if="loading" message="Comprobando tu invitación…")
      template(v-else-if="valid")
        p.page-hint Te invitaron a crear y organizar eventos en InsightBloom.
        p.invite-detail Invitación dirigida a #[strong {{ emailHint }}]
        p.invite-detail Vence: #[strong {{ expiryLabel }}]
        FeedbackMessage(v-if="error" :message="error" tone="error")
        template(v-if="!auth.state.token")
          p.page-hint Para activar el acceso, crea una cuenta o inicia sesión con el correo invitado. Verificaremos ese correo antes de habilitar la creación de eventos.
          .actions
            BaseButton(size="lg" @click="goRegister") Crear cuenta y continuar
            BaseButton(variant="secondary" size="lg" @click="goLogin") Ya tengo cuenta
        template(v-else)
          p.page-hint Confirma para asociar el permiso de organizador a la cuenta cuyo correo verificado coincide con la invitación.
          BaseButton(size="lg" :loading="saving" :disabled="saving" @click="accept") Aceptar invitación
          BaseButton(variant="ghost" type="button" @click="switchAccount") Cambiar de cuenta
      template(v-else)
        FeedbackMessage(message="Este enlace no está disponible. Puede haber vencido, haberse usado o haber sido revocado. Solicita al administrador una invitación nueva." tone="error")
        BaseLink(to="/") Volver a InsightBloom
</template>

<script lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/authStore'
import { previewOrganizerInvite, redeemOrganizerInvite } from '@/services/api/organizerInvitationsApi'
import AppHeader from '@/app/layout/AppHeader.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

const INVITE_STORAGE_KEY = 'ib_organizer_invite_token'

export default {
  name: 'OrganizerInvitePage',
  components: { AppHeader, BaseButton, BaseLink, FeedbackMessage, LoadingState },
  setup() {
    const auth = useAuthStore(); const router = useRouter()
    const loading = ref(true); const saving = ref(false); const valid = ref(false); const emailHint = ref(''); const expiresAt = ref(''); const error = ref('')
    const expiryLabel = computed(() => expiresAt.value ? new Intl.DateTimeFormat('es-MX',{dateStyle:'long',timeStyle:'short'}).format(new Date(expiresAt.value)) : '')
    const inviteToken = () => sessionStorage.getItem(INVITE_STORAGE_KEY) || ''
    async function load() {
      const fragment = new URLSearchParams(window.location.hash.slice(1))
      const raw = fragment.get('token')
      if (raw) {
        sessionStorage.setItem(INVITE_STORAGE_KEY, raw)
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}`)
      }
      const value = inviteToken()
      if (!value) { loading.value = false; return }
      try { const result = await previewOrganizerInvite(value); valid.value = result.valid; emailHint.value = result.emailHint || ''; expiresAt.value = result.expiresAt || '' }
      catch (e: any) { valid.value = false }
      finally { loading.value = false }
    }
    function goRegister() { router.push({ path: '/register', query: { redirect: '/organizer-invite' } }) }
    function goLogin() { router.push({ path: '/login', query: { redirect: '/organizer-invite' } }) }
    async function accept() {
      if (!auth.state.token || !inviteToken()) return
      saving.value = true; error.value = ''
      try {
        const session = await redeemOrganizerInvite(auth.state.token as string, inviteToken())
        auth.setSession(session); sessionStorage.removeItem(INVITE_STORAGE_KEY)
        await router.replace('/dashboard')
      } catch (e: any) {
        error.value = 'No se pudo aceptar. Asegúrate de haber iniciado sesión con el correo invitado y de haberlo verificado.'
      } finally { saving.value = false }
    }
    async function switchAccount() { await auth.logout(); await router.push({ path: '/login', query: { redirect: '/organizer-invite' } }) }
    onMounted(load)
    return { auth, loading, saving, valid, emailHint, expiryLabel, error, goRegister, goLogin, accept, switchAccount }
  }
}
</script>

<style scoped>
.invite-page { min-height:100vh; background:var(--color-bg); }
.invite-main { display:flex; justify-content:center; align-items:flex-start; padding:clamp(24px,7vh,72px) 16px; }
.invite-card { width:min(100%,520px); background:var(--color-surface); border:1px solid var(--color-border-subtle); border-radius:18px; padding:clamp(24px,6vw,44px); box-shadow:var(--shadow-card); text-align:center; }
.invite-mark { display:grid; place-items:center; width:56px; height:56px; border-radius:50%; margin:0 auto 18px; background:var(--color-primary-soft); color:var(--color-primary); font-size:1.6rem; }
h1 { color:var(--color-heading); font-size:1.5rem; margin:0 0 12px; }
.invite-detail { color:var(--color-text-secondary); margin:10px 0; overflow-wrap:anywhere; }
.actions { display:flex; flex-direction:column; gap:10px; margin-top:20px; }
</style>
