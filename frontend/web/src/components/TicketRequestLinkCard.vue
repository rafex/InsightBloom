<template lang="pug">
section(aria-labelledby="ticket-request-link-heading")
  h3#ticket-request-link-heading Enlace privado para solicitar boleto
  p Comparte el enlace o QR solo con tus invitados. Se reserva un boleto por correo verificado si queda cupo. El enlace vence después de siete días.
  p(v-if="expiresAt" role="status") {{ expired ? 'Enlace vencido' : 'Enlace activo hasta ' + new Date(expiresAt).toLocaleString() }}
  p(v-else-if="!busy") No hay un enlace activo.
  BaseButton(type="button" :loading="busy" @click="rotate") {{ expiresAt && !expired ? 'Reemplazar enlace' : 'Generar enlace' }}
  BaseButton(v-if="expiresAt" type="button" variant="secondary" :disabled="busy" @click="revoke") Revocar enlace
  p(v-if="expiresAt && !expired && !url") Por seguridad, el enlace solo se muestra al generarlo. Puedes reemplazarlo si necesitas volver a compartirlo.
  div(v-if="url")
    label(for="ticket-request-url") Enlace para invitados
    input#ticket-request-url(:value="url" readonly)
    BaseButton(type="button" variant="secondary" @click="copy") Copiar enlace
    div(v-if="qr")
      img(:src="qr" width="200" height="200" alt="QR del enlace privado para solicitar boleto")
      p
        a(:href="qr" download="solicitar-boleto.png") Descargar QR
  FeedbackMessage(v-if="message" :message="message" :tone="failed ? 'error' : 'success'")
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import QRCode from 'qrcode'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import { useAuthStore } from '@/features/auth/authStore'
import { getTicketRequestLinkStatus, revokeTicketRequestLink, rotateTicketRequestLink } from '@/services/api/usersApi'

const props = defineProps<{ conferenceId: string, friendlyId: string }>()
const auth = useAuthStore()
const expiresAt = ref('')
const expired = computed(() => Boolean(expiresAt.value) && Date.parse(expiresAt.value) <= Date.now())
const url = ref('')
const qr = ref('')
const busy = ref(false)
const message = ref('')
const failed = ref(false)

async function update(action: (token: string) => Promise<void>) {
  if (busy.value || !auth.state.token) return
  busy.value = true
  message.value = ''
  failed.value = false
  try {
    await action(auth.state.token)
  } catch (error: any) {
    failed.value = true
    message.value = error.response?.data?.error?.message || 'No se pudo actualizar el enlace.'
  } finally {
    busy.value = false
  }
}

onMounted(() => update(async token => {
  const result = await getTicketRequestLinkStatus(props.conferenceId, token)
  expiresAt.value = result.expiresAt || ''
}))

async function rotate() {
  if (expiresAt.value && !expired.value && !window.confirm('¿Reemplazar el enlace? El anterior dejará de aceptar solicitudes.')) return
  await update(async token => {
    const result = await rotateTicketRequestLink(props.conferenceId, token)
    expiresAt.value = result.expiresAt
    qr.value = ''
    url.value = `${window.location.origin}/events/${encodeURIComponent(props.friendlyId)}/checkout#solicitud=${result.token}`
    qr.value = await QRCode.toDataURL(url.value, { width: 400 })
    message.value = 'Enlace generado.'
  })
}

async function revoke() {
  if (!window.confirm('¿Revocar el enlace? Los boletos ya emitidos seguirán vigentes.')) return
  await update(async token => {
    await revokeTicketRequestLink(props.conferenceId, token)
    expiresAt.value = ''
    url.value = ''
    qr.value = ''
    message.value = 'Enlace revocado.'
  })
}

async function copy() {
  try {
    await navigator.clipboard.writeText(url.value)
    failed.value = false
    message.value = 'Enlace copiado.'
  } catch {
    failed.value = true
    message.value = 'Selecciona el enlace del campo para copiarlo.'
  }
}
</script>
