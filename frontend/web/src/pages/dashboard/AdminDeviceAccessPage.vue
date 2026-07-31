<template lang="pug">
.device-access-page
  h2 Acceso por dispositivo

  LoadingState(v-if="loading" message="Cargando configuración de dispositivos…")
  .settings-card(v-else)
    SaveState(:state="saveState")
    h3 Umbrales a nivel plataforma
    p.field-hint Estos límites aplican a TODA la plataforma (no a un evento puntual) — se calculan
      |  sobre la huella real del dispositivo (ThumbmarkJS), capturada desde el login.

    FormField(label="Máx. sesiones simultáneas por usuario" hint="Al superar el límite, se cierra automáticamente la sesión más vieja de ese usuario.")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model.number="maxSessionsPerUser" type="number" min="1" max="20" placeholder="3 (por defecto)")

    FormField(label="Máx. cuentas distintas por dispositivo" hint="Si un mismo dispositivo acumula más cuentas activas que este número, se bloquea el login desde ese dispositivo.")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model.number="maxAccountsPerDevice" type="number" min="1" max="50" placeholder="5 (por defecto)")

    FormField(label="Máx. registros por dispositivo por día" hint="Frena el spam de creación de cuentas nuevas desde el mismo dispositivo en 24h.")
      template(#default="{ id, describedBy }")
        input(:id="id" :aria-describedby="describedBy" v-model.number="maxRegistrationsPerDevicePerDay" type="number" min="1" max="50" placeholder="3 (por defecto)")

    BaseButton(:loading="saving" :disabled="saving || saveState === 'clean' || saveState === 'saved'" @click="save") Guardar cambios
    FeedbackMessage(v-if="saved" message="Cambios guardados." tone="success")
    FeedbackMessage(v-if="error" :message="error" tone="error")
  FeedbackMessage(v-if="copyFeedback" :message="copyFeedback" tone="success")

  h3.blocks-title Dispositivos bloqueados
  EmptyState(v-if="!loadingBlocks && blocks.length === 0" message="No hay dispositivos bloqueados a nivel plataforma.")
  ModerationTable(v-else :items="blocks" :currentPage="1" :totalPages="1")
    template(#headers)
      th Dispositivo
      th Motivo
      th Relacionados
      th Bloqueado
      th Estado
      th Acciones
    template(#row="{ item }")
      td
        span.fingerprint {{ shortFingerprint(item.deviceFingerprint) }}
      td {{ reasonLabel(item.reason) }}
      td {{ item.relatedCount }}
      td {{ formatDate(item.blockedAt) }}
      td
        StatusBadge(:status="item.unblockedAt ? 'ACTIVE' : 'BANNED'" :label="item.unblockedAt ? 'Desbloqueado' : 'Bloqueado'")
      td.actions
        BaseButton(
          variant="success"
          size="sm"
          v-if="!item.unblockedAt"
          @click="unblock(item)"
          :disabled="item._loading"
          :loading="item._loading"
        ) Desbloquear

  h3.blocks-title Discrepancias de huella detectadas
  p.field-hint Se detecta (sin bloquear) cuando el fingerprint de un request no coincide con el
    |  del login de esa misma sesión — no significa que la sesión se cortó, solo queda visible para revisar.
  EmptyState(v-if="!loadingFlags && flags.length === 0" message="No hay discrepancias detectadas.")
  ModerationTable(v-else :items="flags" :currentPage="1" :totalPages="1")
    template(#headers)
      th Sujeto
      th Huella del login
      th Última huella vista
      th Veces
      th Primera vez
      th Última vez
      th Estado
      th Acciones
    template(#row="{ item }")
      td
        .subject-cell
          span.subject-name {{ subjectLabel(item) }}
          BaseButton.uuid-copy(
            variant="ghost"
            size="sm"
            type="button"
            :title="`Copiar UUID: ${item.subjectUuid}`"
            :aria-label="`Copiar UUID de ${subjectLabel(item)}`"
            @click="copyUuid(item.subjectUuid)"
          ) · {{ shortUuid(item.subjectUuid) }}
      td
        span.fingerprint {{ shortFingerprint(item.loginFingerprint) }}
      td
        span.fingerprint {{ shortFingerprint(item.lastSeenFingerprint) }}
      td {{ item.occurrenceCount }}
      td {{ formatDate(item.firstSeenAt) }}
      td {{ formatDate(item.lastSeenAt) }}
      td
        StatusBadge(:status="item.reviewedAt ? 'ACTIVE' : 'PENDING'" :label="item.reviewedAt ? 'Revisado' : 'Pendiente'")
      td.actions
        BaseButton(
          variant="success"
          size="sm"
          v-if="!item.reviewedAt"
          @click="review(item)"
          :disabled="item._loading"
          :loading="item._loading"
        ) Marcar revisado
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import { getUser } from '@/services/api/adminApi'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import FormField from '@/components/ui/FormField.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { ref, computed, onMounted } from 'vue'
import {
  getDeviceAccessSettings, setDeviceAccessSettings, listPlatformDeviceBlocks, unblockPlatformDevice,
  listDeviceFingerprintFlags, reviewDeviceFingerprintFlag
} from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import type { PlatformDeviceBlock, DeviceFingerprintFlag } from '@/services/api/types'

type PlatformDeviceBlockRow = PlatformDeviceBlock & { _loading: boolean }
type DeviceFingerprintFlagRow = DeviceFingerprintFlag & { _loading: boolean }

export default {
  name: 'AdminDeviceAccessPage',
  components: { ModerationTable, BaseButton, EmptyState, FeedbackMessage, FormField, LoadingState, SaveState, StatusBadge },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const maxAccountsPerDevice = ref<number | null>(null)
    const maxSessionsPerUser = ref<number | null>(null)
    const maxRegistrationsPerDevicePerDay = ref<number | null>(null)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')
    const initialSettings = ref('')
    const copyFeedback = ref('')

    const saveState = computed(() => {
      if (saving.value) return 'saving'
      const current = JSON.stringify({
        maxAccountsPerDevice: maxAccountsPerDevice.value,
        maxSessionsPerUser: maxSessionsPerUser.value,
        maxRegistrationsPerDevicePerDay: maxRegistrationsPerDevicePerDay.value
      })
      if (current !== initialSettings.value) return 'dirty'
      if (saved.value) return 'saved'
      return 'clean'
    })

    const blocks = ref<PlatformDeviceBlockRow[]>([])
    const loadingBlocks = ref(true)

    const flags = ref<DeviceFingerprintFlagRow[]>([])
    const loadingFlags = ref(true)
    const subjectNames = ref<Record<string, string>>({})

    async function loadBlocks() {
      loadingBlocks.value = true
      try {
        const res = await listPlatformDeviceBlocks(auth.state.token as string)
        blocks.value = res.map((b) => ({ ...b, _loading: false }))
      } catch (e: any) { /* deja la tabla vacia */ } finally { loadingBlocks.value = false }
    }

    async function loadFlags() {
      loadingFlags.value = true
      try {
        const res = await listDeviceFingerprintFlags(auth.state.token as string)
        flags.value = res.map((f) => ({ ...f, _loading: false }))
        const userUuids = [...new Set(flags.value.filter((flag) => flag.subjectKind === 'user').map((flag) => flag.subjectUuid))]
        const profiles = await Promise.all(userUuids.map(async (uuid) => {
          try {
            const user = await getUser(uuid, auth.state.token as string)
            return [uuid, user.displayName || [user.firstName, user.lastName].filter(Boolean).join(' ') || user.email || 'Usuario'] as const
          } catch {
            return [uuid, 'Usuario'] as const
          }
        }))
        subjectNames.value = Object.fromEntries(profiles)
      } catch (e: any) { /* deja la tabla vacia */ } finally { loadingFlags.value = false }
    }

    onMounted(async () => {
      try {
        const settings = await getDeviceAccessSettings(auth.state.token as string)
        maxAccountsPerDevice.value = settings.maxAccountsPerDevice
        maxSessionsPerUser.value = settings.maxSessionsPerUser
        maxRegistrationsPerDevicePerDay.value = settings.maxRegistrationsPerDevicePerDay
        initialSettings.value = JSON.stringify({
          maxAccountsPerDevice: maxAccountsPerDevice.value,
          maxSessionsPerUser: maxSessionsPerUser.value,
          maxRegistrationsPerDevicePerDay: maxRegistrationsPerDevicePerDay.value
        })
      } catch (e: any) {
        error.value = 'No fue posible cargar la configuración de dispositivos.'
      } finally {
        loading.value = false
      }
      loadBlocks()
      loadFlags()
    })

    async function save() {
      saving.value = true; saved.value = false; error.value = ''
      try {
        const settings = await setDeviceAccessSettings(
          maxAccountsPerDevice.value, maxSessionsPerUser.value, maxRegistrationsPerDevicePerDay.value,
          auth.state.token as string
        )
        maxAccountsPerDevice.value = settings.maxAccountsPerDevice
        maxSessionsPerUser.value = settings.maxSessionsPerUser
        maxRegistrationsPerDevicePerDay.value = settings.maxRegistrationsPerDevicePerDay
        initialSettings.value = JSON.stringify({
          maxAccountsPerDevice: maxAccountsPerDevice.value,
          maxSessionsPerUser: maxSessionsPerUser.value,
          maxRegistrationsPerDevicePerDay: maxRegistrationsPerDevicePerDay.value
        })
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    async function unblock(item: PlatformDeviceBlockRow) {
      item._loading = true
      try { await unblockPlatformDevice(item.uuid, auth.state.token as string); await loadBlocks() }
      catch (e: any) { item._loading = false }
    }

    async function review(item: DeviceFingerprintFlagRow) {
      item._loading = true
      try { await reviewDeviceFingerprintFlag(item.uuid, auth.state.token as string); await loadFlags() }
      catch (e: any) { item._loading = false }
    }

    function shortFingerprint(fp: string): string {
      return fp.length > 16 ? `${fp.slice(0, 8)}…${fp.slice(-6)}` : fp
    }

    function shortUuid(uuid: string): string {
      return uuid.length > 18 ? `${uuid.slice(0, 8)}…${uuid.slice(-6)}` : uuid
    }

    function subjectLabel(item: DeviceFingerprintFlagRow): string {
      return item.subjectKind === 'guest' ? 'Invitado' : subjectNames.value[item.subjectUuid] || 'Usuario'
    }

    async function copyUuid(uuid: string) {
      await navigator.clipboard?.writeText(uuid)
      copyFeedback.value = 'UUID copiado. Puedes buscarlo en Usuarios.'
      window.setTimeout(() => { copyFeedback.value = '' }, 3000)
    }

    function reasonLabel(reason: string): string {
      return reason === 'REGISTRATION_SPAM' ? 'Spam de registro' : 'Multicuenta'
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX')
    }

    return {
      loading, maxAccountsPerDevice, maxSessionsPerUser, maxRegistrationsPerDevicePerDay,
      saving, saved, error, saveState, save,
      blocks, loadingBlocks, unblock, shortFingerprint, reasonLabel, formatDate,
      flags, loadingFlags, review, copyFeedback, subjectLabel, shortUuid, copyUuid
    }
  }
}
</script>

<style scoped>
.device-access-page { padding: 24px; max-width: 720px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 16px; }
.settings-card { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); margin-bottom: 32px; }
.settings-card h3 { margin: 0 0 8px; color: var(--color-heading); font-size: 1rem; }
.field-hint { margin: 0 0 12px; font-size: 0.85rem; }
.blocks-title { color: var(--color-heading); font-size: 1rem; margin-bottom: 8px; }
.fingerprint { font-family: monospace; font-size: 0.85rem; color: var(--color-heading); }
.subject-cell { display: flex; align-items: center; gap: 4px; min-width: 170px; }
.subject-name { color: var(--color-text-secondary); }
.uuid-copy { padding: 2px 6px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.78rem; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
