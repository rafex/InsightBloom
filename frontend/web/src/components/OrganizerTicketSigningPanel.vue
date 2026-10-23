<template lang="pug">
section.organizer-signing(aria-labelledby="organizer-signing-title")
  h3#organizer-signing-title Firma del organizador para boletos offline
  p.field-hint Cada evento usa su propia clave Ed25519. La privada se crea y conserva en este navegador; el servidor recibe solo la clave pública, firmas COSE y datos firmados.
  FeedbackMessage(v-if="loadError" :message="loadError" tone="error")
  FeedbackMessage(v-if="message" :message="message" tone="success")
  .coord-field(v-if="!keyStatus")
    label.coord-label(for="organizer-key-backup-passphrase") Contraseña para el respaldo cifrado
    input#organizer-key-backup-passphrase(v-model="backupPassphrase" type="password" autocomplete="new-password" minlength="12" placeholder="Al menos 12 caracteres")
    p.field-hint Guarda el archivo de respaldo fuera de InsightBloom y conserva su contraseña. No podemos recuperar la clave si pierdes el navegador y el respaldo.
    BaseButton(variant="secondary" type="button" :disabled="busy || backupPassphrase.trim().length < 12" @click="createKey") {{ busy ? 'Creando clave…' : 'Crear clave del evento y descargar respaldo' }}
  template(v-else)
    p.signing-key-state Clave activa: {{ keyStatus.keyId }} · registrada {{ new Date(keyStatus.createdAt).toLocaleString() }}
    BaseButton(variant="danger" type="button" :disabled="busy" @click="revokeKey") Revocar clave del evento
    p.field-hint(v-if="!localKey") La clave pública está registrada, pero esta sesión no tiene la privada local. Restaura el archivo de respaldo para firmar boletos.
    .coord-field(v-if="!localKey")
      label.coord-label(for="organizer-key-backup-file") Restaurar respaldo
      input#organizer-key-backup-file(type="file" accept="application/json,.json" @change="restoreKeyFile")
      label.coord-label(for="organizer-key-restore-passphrase") Contraseña del respaldo
      input#organizer-key-restore-passphrase(v-model="restorePassphrase" type="password" autocomplete="current-password")
      BaseButton(variant="secondary" type="button" :disabled="busy || !selectedBackup || !restorePassphrase" @click="restoreSelectedBackup") Restaurar clave en este navegador
    BaseButton(v-if="localKey" variant="secondary" type="button" :disabled="busy" @click="refreshPending") Actualizar cola ({{ pendingCount }})
    ul.pending-list(v-if="localKey && pending.length" aria-label="Boletos que esperan firma")
      li(v-for="item in pending" :key="item.ticketUuid")
        span {{ new Date(item.issuedAt).toLocaleString() }} · boleto {{ item.ticketUuid }}
        BaseButton(variant="secondary" size="sm" type="button" :disabled="busy" @click="signOne(item)") Firmar este boleto
    BaseButton(v-if="localKey" variant="secondary" type="button" :disabled="busy || pendingCount > 0" @click="prepareOfflinePackage")
      span(v-if="busy") Preparando…
      span(v-else) Firmar paquete para lectores offline
    p.field-hint(v-if="localKey") El paquete incluye boletos firmados, cancelaciones conocidas y dispositivos vinculados. Vence en un máximo de 24 horas; cada lector debe descargarlo antes de desconectarse.
    p.field-hint(v-if="pendingCount === 0 && localKey") No hay boletos pendientes de firma en este momento.
      FeedbackMessage(v-if="loadError" :message="loadError" tone="error")
</template>

<script lang="ts">
import { computed, defineComponent, onMounted, ref } from 'vue'
import { useAuthStore } from '@/features/auth/authStore'
import {
  getOrganizerTicketSigningKey, getPendingTicketSignatures, getOfflineTicketManifest,
  registerOrganizerTicketSigningKey, revokeOrganizerTicketSigningKey, submitTicketSignature, submitOfflineTicketBundle,
  type OrganizerTicketKeyStatus, type PendingTicketSignature
} from '@/services/api/usersApi'
import {
  createOrganizerKeyProof, createOrganizerSigningKey, getOrganizerSigningKey,
  restoreOrganizerSigningKey, signCosePayload, type EncryptedOrganizerKeyBackup, type OrganizerSigningKey
} from '@/utils/ticketOrganizerCrypto'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'

export default defineComponent({
  name: 'OrganizerTicketSigningPanel',
  components: { BaseButton, FeedbackMessage },
  props: { conferenceId: { type: String, required: true } },
  setup(props) {
    const auth = useAuthStore()
    const keyStatus = ref<OrganizerTicketKeyStatus | null>(null)
    const localKey = ref<OrganizerSigningKey>()
    const pending = ref<PendingTicketSignature[]>([])
    const backupPassphrase = ref('')
    const restorePassphrase = ref('')
    const selectedBackup = ref<EncryptedOrganizerKeyBackup>()
    const busy = ref(false)
    const message = ref('')
    const loadError = ref('')
    const pendingCount = computed(() => pending.value.length)

    async function load() {
      loadError.value = ''
      try {
        keyStatus.value = await getOrganizerTicketSigningKey(props.conferenceId, auth.state.token as string)
        localKey.value = await getOrganizerSigningKey(props.conferenceId)
        if (keyStatus.value && localKey.value?.keyId !== keyStatus.value.keyId) localKey.value = undefined
        if (localKey.value) pending.value = await getPendingTicketSignatures(props.conferenceId, auth.state.token as string)
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || 'No se pudo consultar la firma del evento.' }
    }

    async function createKey() {
      busy.value = true; loadError.value = ''; message.value = ''
      try {
        const created = await createOrganizerSigningKey(props.conferenceId, backupPassphrase.value)
        const challenge = `insightbloom-event-key-proof-v1|${props.conferenceId}|${created.key.keyId}`
        const proof = await createOrganizerKeyProof(created.key, challenge)
        keyStatus.value = await registerOrganizerTicketSigningKey(props.conferenceId, {
          keyId: created.key.keyId, publicKeySpki: created.key.publicKeySpki, proof
        }, auth.state.token as string)
        localKey.value = created.key
        downloadBackup(created.backup)
        backupPassphrase.value = ''
        message.value = 'Clave del evento registrada. El respaldo cifrado se descargó en este dispositivo.'
        pending.value = await getPendingTicketSignatures(props.conferenceId, auth.state.token as string)
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || error.message || 'No se pudo crear la clave.' }
      finally { busy.value = false }
    }

    function downloadBackup(backup: EncryptedOrganizerKeyBackup) {
      const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url; anchor.download = `insightbloom-event-${props.conferenceId}-signing-key-backup.json`
      anchor.click(); URL.revokeObjectURL(url)
    }

    async function restoreKeyFile(event: Event) {
      const file = (event.target as HTMLInputElement).files?.[0]
      selectedBackup.value = undefined
      if (!file) return
      try {
        const backup = JSON.parse(await file.text()) as EncryptedOrganizerKeyBackup
        if (backup.eventUuid !== props.conferenceId) throw new Error('El respaldo corresponde a otro evento.')
        selectedBackup.value = backup
        if (!restorePassphrase.value) return
        await restoreSelectedBackup()
      } catch (error: any) { loadError.value = error.message || 'El archivo de respaldo no es válido.' }
    }

    async function restoreSelectedBackup() {
      if (!selectedBackup.value) return
      busy.value = true; loadError.value = ''
      try {
        localKey.value = await restoreOrganizerSigningKey(selectedBackup.value, restorePassphrase.value)
        if (!keyStatus.value || localKey.value.keyId !== keyStatus.value.keyId) throw new Error('La clave restaurada no coincide con la clave pública activa del evento.')
        pending.value = await getPendingTicketSignatures(props.conferenceId, auth.state.token as string)
        restorePassphrase.value = ''; selectedBackup.value = undefined
        message.value = 'Respaldo restaurado en este navegador.'
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || error.message || 'No se pudo restaurar el respaldo.' }
      finally { busy.value = false }
    }

    async function refreshPending() {
      if (!localKey.value) return
      busy.value = true; loadError.value = ''
      try { pending.value = await getPendingTicketSignatures(props.conferenceId, auth.state.token as string) }
      catch (error: any) { loadError.value = error.response?.data?.error?.message || 'No se pudo actualizar la cola de firma.' }
      finally { busy.value = false }
    }

    async function signOne(item: PendingTicketSignature) {
      if (!localKey.value) return
      busy.value = true; message.value = ''; loadError.value = ''
      try {
        const payload = Uint8Array.from(atob(item.payloadBase64Url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - item.payloadBase64Url.length % 4) % 4)), c => c.charCodeAt(0))
        await submitTicketSignature(props.conferenceId, item.ticketUuid, await signCosePayload(localKey.value, payload), auth.state.token as string)
        pending.value = await getPendingTicketSignatures(props.conferenceId, auth.state.token as string)
        message.value = `Boleto ${item.ticketUuid} firmado y habilitado para entrega.`
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || error.message || 'No se pudo firmar el boleto.' }
      finally { busy.value = false }
    }

    async function revokeKey() {
      if (!window.confirm('Revocar esta clave impedirá firmar boletos y paquetes nuevos. Los lectores que ya se desconectaron seguirán aceptando su paquete hasta que venza. ¿Continuar?')) return
      busy.value = true; loadError.value = ''; message.value = ''
      try {
        await revokeOrganizerTicketSigningKey(props.conferenceId, auth.state.token as string)
        keyStatus.value = null; localKey.value = undefined; pending.value = []
        message.value = 'Clave revocada. Registra una nueva clave para volver a emitir y preparar paquetes offline.'
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || 'No se pudo revocar la clave.' }
      finally { busy.value = false }
    }

    async function prepareOfflinePackage() {
      if (!localKey.value) return
      busy.value = true; message.value = ''; loadError.value = ''
      try {
        const manifest = await getOfflineTicketManifest(props.conferenceId, auth.state.token as string)
        if (manifest.keyId !== localKey.value.keyId) throw new Error('La clave local no coincide con la clave activa del evento.')
        const normalized = manifest.payloadBase64Url.replace(/-/g, '+').replace(/_/g, '/')
        const payload = Uint8Array.from(atob(normalized + '='.repeat((4 - normalized.length % 4) % 4)), c => c.charCodeAt(0))
        await submitOfflineTicketBundle(props.conferenceId, manifest.payload, await signCosePayload(localKey.value, payload), auth.state.token as string)
        message.value = `Paquete offline firmado. Vence ${new Date(manifest.expiresAt).toLocaleString()}. Descárgalo en cada lector antes de desconectarlo.`
      } catch (error: any) { loadError.value = error.response?.data?.error?.message || error.message || 'No se pudo preparar el paquete offline.' }
      finally { busy.value = false }
    }

    onMounted(() => void load())
    return { keyStatus, localKey, backupPassphrase, restorePassphrase, busy, message, loadError, pendingCount,
      pending, selectedBackup, createKey, restoreKeyFile, restoreSelectedBackup, refreshPending, signOne, prepareOfflinePackage, revokeKey }
  }
})
</script>

<style scoped>
.organizer-signing { margin-top: 1.5rem; padding: 1.25rem; border: 1px solid var(--border-color, #d8dee9); border-radius: 0.75rem; }
.organizer-signing h3 { margin-top: 0; }
.signing-key-state { font-weight: 600; overflow-wrap: anywhere; }
.pending-list { display: grid; gap: .75rem; padding-left: 1.25rem; }
.pending-list li { display: flex; align-items: center; justify-content: space-between; gap: .75rem; flex-wrap: wrap; }
</style>
