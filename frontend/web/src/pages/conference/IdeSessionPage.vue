<template lang="pug">
.ide-session-page
  .unavailable(v-if="!targetUrl")
    p ⚠️ No se especificó un IDE para abrir.
    p.hint Volvé a la página del evento y abrí el IDE desde ahí.
  template(v-else)
    iframe.ide-frame(:src="targetUrl" title="IDE" allow="clipboard-write")
    ide-help-panel
</template>

<script lang="ts">
import { useRoute } from 'vue-router'
import IdeHelpPanel from '@/components/IdeHelpPanel.vue'

// Pagina propia (no navegacion directa al gateway) para poder superponer el boton/panel de
// ayuda de Neovim SOBRE la terminal -- se abre igual en pestana nueva (ver IdePage.vue,
// target="_blank"), pero la pestana nueva es esta pagina de nuestra app, que embebe el IDE real
// en un iframe. Confirmado que ni ttyd ni code-server mandan X-Frame-Options/frame-ancestors
// que bloqueen esto (2026-07-19).
export default {
  name: 'IdeSessionPage',
  components: { IdeHelpPanel },
  setup() {
    const route = useRoute()
    const targetUrl = (route.query.target as string) || ''
    return { targetUrl }
  }
}
</script>

<style scoped>
.ide-session-page {
  position: fixed;
  inset: 0;
  background: #1e1e1e;
}

.ide-frame {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: none;
}

.unavailable {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #d1d5db;
  text-align: center;
  padding: 24px;
}

.unavailable .hint {
  color: #9ca3af;
  font-size: 0.9rem;
}
</style>
