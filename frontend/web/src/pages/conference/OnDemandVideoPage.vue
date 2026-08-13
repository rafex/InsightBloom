<template lang="pug">
.on-demand-page
  //- El iframe real (via <Teleport>) aterriza aca -- ver OnDemandFloatingVideo.vue, montado de
  //- forma persistente en ConferencePage.vue. SIEMPRE presente en el DOM, sin importar
  //- accessGranted/embedUrl (que llegan async y pueden resolver en un tick distinto al que usa
  //- OnDemandFloatingVideo para calcular su teleportTarget) -- si este div no existe todavia
  //- cuando el Teleport intenta apuntar a #ondemand-full-slot, y despues aparece, Vue NO
  //- reintenta el teleport porque el string del ":to" no cambio de valor, dejando el video
  //- "perdido" y el proximo patch del arbol revienta al tocar un anchor node que nunca se
  //- ancló bien (bug reportado 2026-08-12: video desaparecia y saltaba "can't access property
  //- nextSibling" en consola al recargar ya parado en esta pestaña). Reservar el slot siempre,
  //- vacio, elimina la carrera de raiz.
  #ondemand-full-slot
  EmptyState(v-if="!accessGranted" message="Regístrate y canjea tu boleto para ver el video de este evento.")
  EmptyState(v-else-if="!embedUrl" message="El organizador todavía no configuró un video para este evento.")
  template(v-else)
    .cue-list(v-if="sortedCuePoints.length")
      p.hint Sugerencias por momento del video:
      a.cue-list-item(v-for="cue in sortedCuePoints" :key="cue.atSeconds" :href="`/c/${friendlyId}/${cue.toolPath}`" :target="cue.toolPath === 'ide' ? '_blank' : undefined" :rel="cue.toolPath === 'ide' ? 'noopener' : undefined")
        span.cue-time {{ formatTime(cue.atSeconds) }}
        span.cue-tool {{ toolLabel(cue.toolPath) }}
        span.cue-label {{ cue.label }}
</template>

<script lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import type { OnDemandCuePoint } from '@/services/api/types'
import { toEmbedUrl, toolLabelForPath } from '@/features/conferences/onDemandVideo'
import EmptyState from '@/components/ui/EmptyState.vue'

export default {
  name: 'OnDemandVideoPage',
  components: { EmptyState },
  props: {
    conferenceId: String,
    onDemandVideoProvider: { type: String, default: null },
    onDemandVideoUrl: { type: String, default: null },
    onDemandCuePoints: { type: Array as () => OnDemandCuePoint[], default: () => [] },
    accessGranted: { type: Boolean, default: false }
  },
  setup(props: {
    conferenceId?: string
    onDemandVideoProvider?: 'YOUTUBE' | 'PEERTUBE' | null
    onDemandVideoUrl?: string | null
    onDemandCuePoints?: OnDemandCuePoint[]
    accessGranted?: boolean
  }) {
    const route = useRoute()
    const friendlyId = route.params.friendlyId as string
    const embedUrl = computed(() => toEmbedUrl(props.onDemandVideoProvider || null, props.onDemandVideoUrl || null))
    const sortedCuePoints = computed(() => [...(props.onDemandCuePoints || [])].sort((a, b) => a.atSeconds - b.atSeconds))

    function formatTime(totalSeconds: number): string {
      const minutes = Math.floor(totalSeconds / 60)
      const seconds = totalSeconds % 60
      return `${minutes}:${String(seconds).padStart(2, '0')}`
    }

    return { friendlyId, embedUrl, sortedCuePoints, formatTime, toolLabel: toolLabelForPath }
  }
}
</script>

<style scoped>
.on-demand-page {
  padding: 24px;
  display: flex;
  flex-direction: column;
}
.on-demand-header { margin-bottom: 16px; }
h2 { margin: 0; color: var(--color-heading); }
#ondemand-full-slot {
  min-height: 1px;
  order: 1;
  margin-bottom: 24px;
}
.cue-list {
  margin-top: 0;
  order: 2;
}
.cue-list .hint { color: var(--color-text-muted); font-size: 0.85rem; margin-bottom: 8px; }
.cue-list-item {
  display: flex; align-items: baseline; gap: 10px;
  padding: 8px 12px; border-radius: 8px; text-decoration: none;
  color: var(--color-text-secondary); font-size: 0.88rem; margin-bottom: 4px;
}
.cue-list-item:hover { background: var(--color-surface-muted); color: var(--color-primary); }
.cue-time { font-family: monospace; color: var(--color-text-muted); flex-shrink: 0; }
.cue-tool {
  font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em;
  color: var(--color-primary); flex-shrink: 0;
}

@media (max-width: 640px) {
  .on-demand-page { padding: 14px; }
}
</style>
