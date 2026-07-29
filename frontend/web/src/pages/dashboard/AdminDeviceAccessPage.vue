<template lang="pug">
.device-access-page
  h2 Acceso por dispositivo

  .loading-text(v-if="loading") Cargando...
  .settings-card(v-else)
    h3 Umbrales a nivel plataforma
    p.field-hint Estos límites aplican a TODA la plataforma (no a un evento puntual) — se calculan
      |  sobre la huella real del dispositivo (ThumbmarkJS), capturada desde el login.

    .form-group
      label Máx. sesiones simultáneas por usuario
      p.field-hint Al superar el límite, se cierra automáticamente la sesión más vieja de ese usuario.
      input(v-model.number="maxSessionsPerUser" type="number" min="1" max="20" placeholder="3 (por defecto)")

    .form-group
      label Máx. cuentas distintas por dispositivo
      p.field-hint Si un mismo dispositivo acumula más cuentas activas que este número, se bloquea el login desde ese dispositivo.
      input(v-model.number="maxAccountsPerDevice" type="number" min="1" max="50" placeholder="5 (por defecto)")

    .form-group
      label Máx. registros por dispositivo por día
      p.field-hint Frena el spam de creación de cuentas nuevas desde el mismo dispositivo en 24h.
      input(v-model.number="maxRegistrationsPerDevicePerDay" type="number" min="1" max="50" placeholder="3 (por defecto)")

    BaseButton(:loading="saving" @click="save") Guardar cambios
    p.success(v-if="saved") Cambios guardados.
    p.error(v-if="error") {{ error }}

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
        span.status(:class="item.unblockedAt ? 'status-unblocked' : 'status-blocked'")
          | {{ item.unblockedAt ? 'Desbloqueado' : 'Bloqueado' }}
      td.actions
        button.btn-sm.btn-success(
          v-if="!item.unblockedAt"
          @click="unblock(item)"
          :disabled="item._loading"
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
      td {{ item.subjectKind === 'guest' ? 'Invitado' : 'Usuario' }} · {{ shortFingerprint(item.subjectUuid) }}
      td
        span.fingerprint {{ shortFingerprint(item.loginFingerprint) }}
      td
        span.fingerprint {{ shortFingerprint(item.lastSeenFingerprint) }}
      td {{ item.occurrenceCount }}
      td {{ formatDate(item.firstSeenAt) }}
      td {{ formatDate(item.lastSeenAt) }}
      td
        span.status(:class="item.reviewedAt ? 'status-unblocked' : 'status-blocked'")
          | {{ item.reviewedAt ? 'Revisado' : 'Pendiente' }}
      td.actions
        button.btn-sm.btn-success(
          v-if="!item.reviewedAt"
          @click="review(item)"
          :disabled="item._loading"
        ) Marcar revisado
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { ref, onMounted } from 'vue'
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
  components: { ModerationTable, BaseButton, EmptyState },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const maxAccountsPerDevice = ref<number | null>(null)
    const maxSessionsPerUser = ref<number | null>(null)
    const maxRegistrationsPerDevicePerDay = ref<number | null>(null)
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')

    const blocks = ref<PlatformDeviceBlockRow[]>([])
    const loadingBlocks = ref(true)

    const flags = ref<DeviceFingerprintFlagRow[]>([])
    const loadingFlags = ref(true)

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
      } catch (e: any) { /* deja la tabla vacia */ } finally { loadingFlags.value = false }
    }

    onMounted(async () => {
      try {
        const settings = await getDeviceAccessSettings(auth.state.token as string)
        maxAccountsPerDevice.value = settings.maxAccountsPerDevice
        maxSessionsPerUser.value = settings.maxSessionsPerUser
        maxRegistrationsPerDevicePerDay.value = settings.maxRegistrationsPerDevicePerDay
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

    function reasonLabel(reason: string): string {
      return reason === 'REGISTRATION_SPAM' ? 'Spam de registro' : 'Multicuenta'
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX')
    }

    return {
      loading, maxAccountsPerDevice, maxSessionsPerUser, maxRegistrationsPerDevicePerDay,
      saving, saved, error, save,
      blocks, loadingBlocks, unblock, shortFingerprint, reasonLabel, formatDate,
      flags, loadingFlags, review
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
.form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.form-group label { font-weight: 600; font-size: 0.9rem; color: var(--color-text-secondary); }
.form-group input { padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.9rem; }
.success { color: var(--color-success); font-size: 0.85rem; margin-top: 10px; }
.error { color: var(--color-danger); font-size: 0.85rem; margin-top: 10px; }
.blocks-title { color: var(--color-heading); font-size: 1rem; margin-bottom: 8px; }
.fingerprint { font-family: monospace; font-size: 0.85rem; color: var(--color-heading); }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-blocked { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.status-unblocked { background: var(--color-success-soft); color: var(--color-success); }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-success { background: var(--color-success-soft); color: var(--color-success); }
.btn-success:hover { background: var(--color-success-soft); }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
