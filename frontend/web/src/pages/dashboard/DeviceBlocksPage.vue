<template lang="pug">
.device-blocks-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Bloqueos de dispositivo
  p.hint Un dispositivo se bloquea automáticamente cuando se loguean demasiadas cuentas distintas
    |  desde él (posible compu compartida de laboratorio o intento de evasión de límites). Revisa
    |  y decide si desbloquear.

  p.empty(v-if="!loading && blocks.length === 0") No hay dispositivos bloqueados en este evento.

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
        span.status(:class="item.unblockedAt ? 'status-unblocked' : 'status-blocked'")
          | {{ item.unblockedAt ? 'Desbloqueado' : 'Bloqueado' }}
      td.actions
        button.btn-sm.btn-success(
          v-if="!item.unblockedAt"
          @click="unblock(item)"
          :disabled="item._loading"
        ) Desbloquear
</template>

<script lang="ts">
import ModerationTable from '@/components/tables/ModerationTable.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import { ref, computed, onMounted } from 'vue'
import { listDeviceBlocks, unblockDevice, getConference } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import type { DeviceBlock } from '@/services/api/types'

type DeviceBlockRow = DeviceBlock & { _loading: boolean }

export default {
  name: 'DeviceBlocksPage',
  components: { ModerationTable, DashboardBreadcrumb, ConferenceToolsNav },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const blocks = ref<DeviceBlockRow[]>([])
    const loading = ref(false)
    const conferenceName = ref('')

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      try {
        const res = await listDeviceBlocks(props.conferenceId, auth.state.token as string)
        blocks.value = res.map((b) => ({ ...b, _loading: false }))
      } catch (e: any) { /* deja la tabla vacia */ } finally { loading.value = false }
    }

    async function unblock(item: DeviceBlockRow) {
      if (!props.conferenceId) return
      item._loading = true
      try { await unblockDevice(props.conferenceId, item.uuid, auth.state.token as string); await load() }
      catch (e: any) { item._loading = false }
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

    return { blocks, loading, conferenceName, breadcrumbItems, unblock, shortFingerprint, formatDate }
  }
}
</script>

<style scoped>
.device-blocks-page { }
h2 { color: #1e1b4b; margin-bottom: 8px; margin-top: 0; }
.hint { color: #6b7280; font-size: 0.85rem; margin-bottom: 20px; max-width: 640px; }
.empty { color: #6b7280; padding: 24px 0; }
.fingerprint { font-family: monospace; font-size: 0.85rem; color: #1e1b4b; }
.status { font-size: 0.82rem; font-weight: 600; padding: 2px 8px; border-radius: 10px; }
.status-blocked { background: #fee2e2; color: #991b1b; }
.status-unblocked { background: #dcfce7; color: #166534; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; }
.btn-sm { padding: 4px 10px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.82rem; }
.btn-success { background: #dcfce7; color: #16a34a; }
.btn-success:hover { background: #bbf7d0; }
.btn-sm:disabled { opacity: 0.5; cursor: not-allowed; }
</style>
