<template lang="pug">
.device-blocks-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Bloqueos de dispositivo
  p.hint Un dispositivo se bloquea automáticamente cuando se loguean demasiadas cuentas distintas
    |  desde él (posible compu compartida de laboratorio o intento de evasión de límites). Revisa
    |  y decide si desbloquear.

  LoadingState(v-if="loading" message="Cargando bloqueos de dispositivo…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  EmptyState(v-else-if="blocks.length === 0" message="No hay dispositivos bloqueados en este evento.")

  ModerationTable(v-else :items="blocks" :currentPage="1" :totalPages="1")
    template(#headers)
      th Dispositivo
      th Cuentas distintas
      th Bloqueado
      th Estado
      th Acciones
    template(#row="{ item }")
      td
        span.fingerprint {{ shortFingerprint(item.deviceFingerprint) }}
      td {{ item.accountCount }}
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
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import { ref, computed, onMounted } from 'vue'
import { listDeviceBlocks, unblockDevice, getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import type { DeviceBlock } from '@/services/api/types'

type DeviceBlockRow = DeviceBlock & { _loading: boolean }

export default {
  name: 'DeviceBlocksPage',
  components: { ModerationTable, DashboardBreadcrumb, ConferenceToolsNav, BaseButton, EmptyState, FeedbackMessage, LoadingState, StatusBadge },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const blocks = ref<DeviceBlockRow[]>([])
    const loading = ref(false)
    const error = ref('')
    const conferenceName = ref('')

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      error.value = ''
      try {
        const res = await listDeviceBlocks(props.conferenceId, auth.state.token as string)
        blocks.value = res.map((b) => ({ ...b, _loading: false }))
      } catch (e: any) {
        error.value = 'No fue posible cargar los bloqueos de dispositivo. Inténtalo nuevamente.'
      } finally { loading.value = false }
    }

    async function unblock(item: DeviceBlockRow) {
      if (!props.conferenceId) return
      item._loading = true
      try { await unblockDevice(props.conferenceId, item.uuid, auth.state.token as string); await load() }
      catch (e: any) {
        item._loading = false
        error.value = 'No fue posible desbloquear el dispositivo. Inténtalo nuevamente.'
      }
    }

    function shortFingerprint(fp: string): string {
      return fp.length > 16 ? `${fp.slice(0, 8)}…${fp.slice(-6)}` : fp
    }

    function formatDate(iso: string): string {
      return new Date(iso).toLocaleString('es-MX')
    }

    onMounted(async () => {
      load()
      if (props.conferenceId) {
        try {
          const conf = await getConference(props.conferenceId, auth.state.token as string)
          conferenceName.value = conf?.name || props.conferenceId
        } catch (e: any) { conferenceName.value = props.conferenceId as string }
      }
    })

    const breadcrumbItems = computed(() => [
      { label: conferenceName.value || props.conferenceId || '', to: `/dashboard/conferences/${props.conferenceId}/device-blocks`, loading: !conferenceName.value },
      { label: 'Bloqueos de dispositivo' }
    ])

    return { blocks, loading, error, conferenceName, breadcrumbItems, unblock, shortFingerprint, formatDate }
  }
}
</script>

<style scoped>
.device-blocks-page { }
h2 { color: var(--color-heading); margin-bottom: 8px; margin-top: 0; }
.hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 20px; max-width: 640px; }
.fingerprint { font-family: monospace; font-size: 0.85rem; color: var(--color-heading); }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
</style>
