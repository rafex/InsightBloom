<template lang="pug">
.mod-ide-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Editor Monaco
  p.field-hint Archivos de cada alumno conectado a un sandbox — abrí el editor para revisar su avance y apoyarlo en vivo.

  .toolbar
    button.btn-outline(type="button" @click="load" :disabled="loading")
      span(v-if="loading") Cargando...
      span(v-else) Actualizar
    button.btn-outline(type="button" @click="prewarm" :disabled="prewarming")
      span(v-if="prewarming") Preparando...
      span(v-else) Preparar sandboxes antes del evento

  p.success(v-if="prewarmSummary") {{ prewarmSummary }}

  p.error(v-if="error") {{ error }}
  p.empty-text(v-else-if="!loading && pods.length === 0") No hay sandboxes activos en este momento.

  .pods-list(v-else)
    .pod-card(v-for="pod in pods" :key="pod.podName")
      .pod-header
        span.pod-name {{ pod.podName }}
        span.pod-variant {{ pod.variant === 'cli' ? 'CLI (Neovim)' : 'Web (code-server)' }}
        span.pod-ready(:class="{ ready: pod.ready }") {{ pod.ready ? 'Listo' : pod.phase }}
      .seats-list
        .seat-row(v-for="seat in pod.seats" :key="seat.seatIndex")
          span.seat-user(v-if="seat.userUuid") {{ seat.userUuid }}
          span.seat-empty(v-else) (asiento libre)
          button.btn-sm.btn-primary-sm(v-if="seat.userUuid" @click="openEditor(seat.userUuid)") Ver archivos

  WorkspaceFileEditor(
    v-if="editorUserUuid"
    :conference-id="conferenceId"
    :user-uuid="editorUserUuid"
    @close="editorUserUuid = null"
  )
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listSandboxStatus, getConference, prewarmSandboxPool } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'
import WorkspaceFileEditor from '@/components/moderator/WorkspaceFileEditor.vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import type { SandboxStatusEntry } from '@/services/api/types'

export default {
  name: 'ModerationIdePage',
  components: { WorkspaceFileEditor, DashboardBreadcrumb, ConferenceToolsNav },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth = useAuthStore()
    const pods = ref<SandboxStatusEntry[]>([])
    const loading = ref(false)
    const error = ref('')
    const prewarming = ref(false)
    const prewarmSummary = ref('')
    const conferenceName = ref('')
    const editorUserUuid = ref<string | null>(null)

    async function load() {
      if (!props.conferenceId) return
      loading.value = true
      error.value = ''
      try {
        pods.value = await listSandboxStatus(props.conferenceId, auth.state.token as string)
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo cargar el estado de los sandboxes'
      } finally {
        loading.value = false
      }
    }

    async function prewarm() {
      if (!props.conferenceId) return
      prewarming.value = true
      error.value = ''
      prewarmSummary.value = ''
      try {
        const result = await prewarmSandboxPool(props.conferenceId, auth.state.token as string)
        const web = result.variants.find(v => v.variant === 'web')
        const cli = result.variants.find(v => v.variant === 'cli')
        prewarmSummary.value = `Pool solicitado: Web ${web?.createdPods || 0}/${web?.desiredPods || 0} Pods nuevos; CLI ${cli?.createdPods || 0}/${cli?.desiredPods || 0} Pods nuevos. La fase de arranque continúa en segundo plano.`
        await load()
      } catch (e: any) {
        error.value = e.response?.data?.error?.message || 'No se pudo preparar el pool de sandboxes'
      } finally {
        prewarming.value = false
      }
    }

    function openEditor(userUuid: string) { editorUserUuid.value = userUuid }

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
      { label: conferenceName.value || props.conferenceId || '', to: `/dashboard/conferences/${props.conferenceId}/moderation/words`, loading: !conferenceName.value },
      { label: 'Editor Monaco' }
    ])

    return { pods, loading, error, prewarming, prewarmSummary, conferenceName, editorUserUuid, breadcrumbItems, load, prewarm, openEditor }
  }
}
</script>

<style scoped>
.mod-ide-page { padding: 24px; max-width: 900px; }
h2 { color: #1e1b4b; margin-bottom: 8px; margin-top: 0; }
.field-hint { color: #6b7280; font-size: 0.85rem; margin: 0 0 16px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.btn-outline { display: inline-block; padding: 6px 14px; border: 1px solid #4f46e5; color: #4f46e5; border-radius: 8px; background: none; font-size: 0.85rem; cursor: pointer; }
.btn-outline:hover { background: #eef2ff; }
.btn-outline:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #dc2626; font-size: 0.9rem; }
.success { color: #166534; font-size: 0.9rem; }
.empty-text { color: var(--color-text-muted); font-size: 0.9rem; }

.pods-list { display: flex; flex-direction: column; gap: 14px; }
.pod-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 16px; }
.pod-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.pod-name { font-family: monospace; font-size: 0.85rem; color: #1e1b4b; font-weight: 600; }
.pod-variant { font-size: 0.75rem; background: #e0e7ff; color: #4338ca; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.pod-ready { font-size: 0.75rem; color: var(--color-text-muted); }
.pod-ready.ready { color: #166534; font-weight: 600; }

.seats-list { display: flex; flex-direction: column; gap: 6px; }
.seat-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 10px; background: #f9fafb; border-radius: 8px; }
.seat-user { font-family: monospace; font-size: 0.8rem; color: #374151; }
.seat-empty { font-size: 0.8rem; color: var(--color-text-muted); font-style: italic; }
.btn-sm { padding: 4px 12px; border: none; border-radius: 6px; cursor: pointer; font-size: 0.8rem; }
.btn-primary-sm { background: #4f46e5; color: #fff; }
.btn-primary-sm:hover { background: #4338ca; }
</style>
