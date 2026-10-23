<template lang="pug">
main.enrollment-page
  section.enrollment-card(aria-labelledby="enrollment-title")
    h1#enrollment-title Activa tu boleto
    template(v-if="!activated")
      p Para proteger el acceso, este boleto queda vinculado a la clave privada de este navegador. No se crea una cuenta.
      p(v-if="linkToken") El enlace solo se puede usar una vez. Quien tenga acceso al enlace podría activar primero el boleto.
      template(v-if="!challengeId && !linkToken")
        label(for="ticket-code") Código del boleto
        input#ticket-code(v-model.trim="ticketCode" autocomplete="off" required)
        BaseButton(:loading="busy" :disabled="!ticketCode" @click="sendOtp") Enviar código al correo registrado
      template(v-else)
        p(v-if="challengeId") Enviamos un código al correo asociado al boleto. El código vence en 10 minutos.
        label(for="attendee-name") Tu nombre
        input#attendee-name(v-model.trim="displayName" autocomplete="name" maxlength="120")
        label(for="device-label") Nombre de este dispositivo
        input#device-label(v-model.trim="deviceLabel" autocomplete="off" maxlength="80")
        template(v-if="challengeId")
          label(for="otp") Código de seis dígitos
          input#otp(v-model.trim="otp" inputmode="numeric" autocomplete="one-time-code" maxlength="6")
        label.replace-confirm(v-if="needsReplacement")
          input(v-model="replaceOldestConfirmed" type="checkbox")
          |  Revocar el dispositivo autorizado más antiguo y reemplazarlo por este. Esta acción no se puede deshacer.
        BaseButton(:loading="busy" :disabled="busy || (challengeId && otp.length !== 6) || (needsReplacement && !replaceOldestConfirmed)" @click="activate") Activar boleto en este dispositivo
      FeedbackMessage(v-if="error" :message="error" tone="error")
    template(v-else)
      p(role="status") Este boleto ya está vinculado a este dispositivo. El código cambia cada 30 segundos.
      .presentation-qr(v-if="presentation") TicketQr(:ticket-code="presentation" :show-code="false")
      p.help Conserva este dispositivo. Si lo pierdes, tendrás que verificar el correo asignado para reemplazarlo.
</template>

<script lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import TicketQr from '@/components/TicketQr.vue'
import { completeTicketEnrollment, startTicketEnrollmentOtp } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import { createTicketDeviceKeyPair, createTicketPresentation, storeTicketDeviceKey } from '@/utils/ticketDeviceCrypto'

export default {
  name: 'TicketEnrollmentPage',
  components: { BaseButton, FeedbackMessage, TicketQr },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const conferenceUuid = String(route.query.event || '')
    const ticketCode = ref(String(route.query.ticketCode || ''))
    const linkToken = ref(String(route.hash.match(/(?:^#|&)token=([^&]+)/)?.[1] || ''))
    const challengeId = ref('')
    const otp = ref('')
    const displayName = ref('')
    const deviceLabel = ref('Este dispositivo')
    const busy = ref(false)
    const activated = ref(false)
    const error = ref('')
    const needsReplacement = ref(false)
    const replaceOldestConfirmed = ref(false)
    const presentation = ref('')
    let timer: number | undefined
    let privateKey: CryptoKey | null = null
    let ticketUuid = ''
    let credentialUuid = ''

    async function refreshPresentation() {
      if (!privateKey || !ticketUuid || !credentialUuid) return
      presentation.value = await createTicketPresentation(ticketUuid, credentialUuid, privateKey)
    }

    async function sendOtp() {
      if (!conferenceUuid || !ticketCode.value) return
      busy.value = true; error.value = ''
      try { challengeId.value = (await startTicketEnrollmentOtp(conferenceUuid, ticketCode.value)).challengeId }
      catch { error.value = 'No se pudo enviar el código. Verifica el boleto o inténtalo más tarde.' }
      finally { busy.value = false }
    }

    async function activate() {
      if (!conferenceUuid || (!linkToken.value && !challengeId.value)) return
      busy.value = true; error.value = ''
      try {
        const keys = await createTicketDeviceKeyPair()
        const result = await completeTicketEnrollment(conferenceUuid, {
          enrollmentToken: linkToken.value || challengeId.value,
          otp: challengeId.value ? otp.value : undefined,
          displayName: displayName.value || 'Invitado',
          deviceLabel: deviceLabel.value || 'Este dispositivo',
          publicKeyJwk: JSON.stringify(keys.publicKeyJwk),
          replaceOldest: replaceOldestConfirmed.value
        }, auth.state.token)
        ticketUuid = result.ticket.uuid
        credentialUuid = result.credentialUuid
        privateKey = keys.privateKey
        await storeTicketDeviceKey(ticketUuid, credentialUuid, privateKey)
        if (result.token && result.guestUuid) auth.setSession({ token: result.token, role: 'guest', userUuid: result.guestUuid, expiresAt: result.expiresAt })
        linkToken.value = ''
        history.replaceState(null, '', location.pathname + location.search)
        activated.value = true
        await refreshPresentation()
      } catch (e: any) {
        const code = e.response?.data?.error?.code
        if (code === 'ticket_device_limit_reached') {
          needsReplacement.value = true
          error.value = 'El boleto alcanzó su límite de dispositivos. Confirma si quieres reemplazar el más antiguo.'
        } else error.value = code === 'ticket_otp_invalid'
          ? 'El código no coincide o ya venció. Solicita uno nuevo.'
          : 'No se pudo activar el boleto. Revisa el enlace o código e inténtalo nuevamente.'
      } finally { busy.value = false }
    }

    onMounted(() => { timer = window.setInterval(() => void refreshPresentation(), 30_000) })
    onBeforeUnmount(() => { if (timer) window.clearInterval(timer) })
    return { ticketCode, linkToken, challengeId, otp, displayName, deviceLabel, busy, activated, error, presentation, needsReplacement, replaceOldestConfirmed, sendOtp, activate }
  }
}
</script>

<style scoped>
.enrollment-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:var(--color-bg,#f4f5f8)}
.enrollment-card{width:min(100%,520px);display:grid;gap:14px;padding:clamp(22px,5vw,36px);border-radius:20px;background:var(--color-surface,#fff);box-shadow:0 16px 48px #1112}
label{font-weight:650}input{min-height:44px;padding:10px 12px;border:1px solid var(--color-border,#ccd0da);border-radius:8px;font:inherit}.help{color:var(--color-text-muted,#656b78)}.presentation-qr{justify-self:center;background:white;padding:8px}
</style>
