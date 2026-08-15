<template lang="pug">
.ide-session-page
  .unavailable(v-if="!targetUrl")
    FeedbackMessage(:message="'No se especificó un IDE para abrir.'" tone="error")
    p.hint Volvé a la página del evento y abrí el IDE desde ahí.
  template(v-else)
    iframe.ide-frame(:key="frameKey" :src="targetUrl" title="IDE" allow="clipboard-read; clipboard-write; fullscreen" @load="handleFrameLoad")
    .ide-loading(v-if="!frameLoaded" role="status" aria-live="polite")
      .ide-loading-card
        p(v-if="!frameTimedOut") Cargando IDE Web…
        template(v-else)
          p El IDE está tardando más de lo esperado.
          BaseButton.ide-retry(variant="secondary" size="sm" type="button" @click="retryFrame") Reintentar
    ide-help-panel(:conference-id="conferenceId" :token="sessionToken")
</template>

<script lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import IdeHelpPanel from '@/components/IdeHelpPanel.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import { useAuthStore } from '@/features/auth/authStore'

// Pagina propia (no navegacion directa al gateway) para poder superponer el boton/panel de
// ayuda de Neovim SOBRE la terminal -- se abre igual en pestana nueva (ver IdePage.vue,
// target="_blank"), pero la pestana nueva es esta pagina de nuestra app, que embebe el IDE real
// en un iframe. Confirmado que ni ttyd ni code-server mandan X-Frame-Options/frame-ancestors
// que bloqueen esto (2026-07-19).
export default {
  name: 'IdeSessionPage',
  components: { BaseButton, FeedbackMessage, IdeHelpPanel },
  setup() {
    const route = useRoute()
    const auth = useAuthStore()
    const targetUrl = (route.query.target as string) || ''
    let conferenceId = ''
    let targetToken = ''
    try {
      const parsed = new URL(targetUrl)
      conferenceId = parsed.searchParams.get('conferenceId') || ''
      targetToken = parsed.searchParams.get('ib_token') || ''
    } catch {
      // La vista de error ya informa que no hay destino válido.
    }
    const frameKey = ref(0)
    const frameLoaded = ref(false)
    const frameTimedOut = ref(false)
    let frameTimer: number | null = null

    function armFrameTimeout() {
      frameLoaded.value = false
      frameTimedOut.value = false
      if (frameTimer) window.clearTimeout(frameTimer)
      frameTimer = window.setTimeout(() => {
        if (!frameLoaded.value) frameTimedOut.value = true
      }, 15000)
    }

    function handleFrameLoad() {
      frameLoaded.value = true
      frameTimedOut.value = false
      if (frameTimer) window.clearTimeout(frameTimer)
      frameTimer = null
    }

    function retryFrame() {
      frameKey.value += 1
      armFrameTimeout()
    }

    onMounted(() => {
      if (targetUrl) armFrameTimeout()
    })
    onBeforeUnmount(() => {
      if (frameTimer) window.clearTimeout(frameTimer)
    })

    return { targetUrl, conferenceId, sessionToken: auth.state.token || targetToken, frameKey, frameLoaded, frameTimedOut, handleFrameLoad, retryFrame }
  }
}
</script>

<style scoped>
.ide-session-page {
  position: fixed;
  inset: 0;
  background: var(--color-heading);
}

.ide-frame {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
}

.ide-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  background: rgba(30, 27, 75, 0.94);
  color: var(--color-text-inverse);
  text-align: center;
}

.ide-loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  max-width: 360px;
  padding: 24px;
}

.ide-loading-card p { margin: 0; }
.ide-retry { pointer-events: auto; }

.unavailable {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--color-border);
  text-align: center;
  padding: 24px;
}

.unavailable .hint {
  color: var(--color-text-muted);
  font-size: 0.9rem;
}
</style>
