<template lang="pug">
.mod-ide-page
  DashboardBreadcrumb(:items="breadcrumbItems")
  ConferenceToolsNav(:conferenceId="conferenceId")

  h2 Editor Monaco
  p.field-hint Archivos de cada alumno conectado a un sandbox — abrí el editor para revisar su avance y apoyarlo en vivo.

  .toolbar
    BaseButton(variant="secondary" size="sm" type="button" @click="load" :disabled="loading" :loading="loading")
      span(v-if="loading") Cargando...
      span(v-else) Actualizar
    BaseButton(variant="secondary" size="sm" type="button" @click="prewarm" :disabled="prewarming" :loading="prewarming")
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
          BaseButton(variant="primary" size="sm" v-if="seat.userUuid" @click="openEditor(seat.userUuid)") Ver archivos

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
import BaseButton from '@/components/ui/BaseButton.vue'
import type { SandboxStatusEntry } from '@/services/api/types'

export default {
  name: 'ModerationIdePage',
  components: { WorkspaceFileEditor, DashboardBreadcrumb, ConferenceToolsNav, BaseButton },
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
h2 { color: var(--color-heading); margin-bottom: 8px; margin-top: 0; }
.field-hint { color: var(--color-text-muted); font-size: 0.85rem; margin: 0 0 16px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.error { color: var(--color-danger); font-size: 0.9rem; }
.success { color: var(--color-success); font-size: 0.9rem; }
.empty-text { color: var(--color-text-muted); font-size: 0.9rem; }

.pods-list { display: flex; flex-direction: column; gap: 14px; }
.pod-card { background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: 12px; padding: 16px; }
.pod-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.pod-name { font-family: monospace; font-size: 0.85rem; color: var(--color-heading); font-weight: 600; }
.pod-variant { font-size: 0.75rem; background: var(--color-primary-soft); color: var(--color-primary-dark); padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.pod-ready { font-size: 0.75rem; color: var(--color-text-muted); }
.pod-ready.ready { color: var(--color-success); font-weight: 600; }

.seats-list { display: flex; flex-direction: column; gap: 6px; }
.seat-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 6px 10px; background: var(--color-surface-muted); border-radius: 8px; }
.seat-user { font-family: monospace; font-size: 0.8rem; color: var(--color-text-secondary); }
.seat-empty { font-size: 0.8rem; color: var(--color-text-muted); font-style: italic; }
</style>
