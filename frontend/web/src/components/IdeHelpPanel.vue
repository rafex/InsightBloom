<template lang="pug">
.ide-help
  button#ide-help-toggle.help-fab(type="button" @click="open = !open" :aria-expanded="open" aria-controls="ide-help-panel" :aria-label="open ? 'Cerrar ayuda de Neovim' : 'Abrir ayuda de Neovim'" title="Ayuda de Neovim")
    span(v-if="!open") 📖
    span(v-else) ✕

  transition(name="slide")
    aside#ide-help-panel.help-panel(v-if="open" role="complementary" aria-label="Ayuda de Neovim" :style="{ width: panelWidth + 'px' }")
      .help-resize-handle(@pointerdown="startResize" title="Arrastrá para cambiar el ancho")
      nav.help-nav(aria-label="Temas de ayuda")
        h3 Ayuda
        button.help-nav-item(
          v-for="topic in availableTopics"
          :key="topic.id"
          type="button"
          :class="{ active: topic.id === activeId }"
          :aria-pressed="topic.id === activeId"
          @click="activeId = topic.id"
        ) {{ topic.title }}
      .help-content(v-if="activeId !== 'mentor'" v-html="renderedActive" @click="onHelpContentClick")
      .mentor-content(v-else)
        h1 🤖 Tutor IA
        p.mentor-intro {{ mentorEnabled ? 'Pregunta sobre tu código o sobre la charla. El tutor te guiará con pistas, no te dará la solución completa.' : 'El tutor IA está deshabilitado para este evento.' }}
        .mentor-messages(v-if="mentorMessages.length")
          .mentor-message(v-for="(item, index) in mentorMessages" :key="index" :class="item.role")
            strong {{ item.role === 'user' ? 'Tú' : 'Tutor' }}
            p {{ item.content }}
        p.mentor-error(v-if="mentorError") {{ mentorError }}
        template(v-if="mentorEnabled")
          label Código o selección (opcional)
          textarea(v-model="mentorCodeContext" rows="5" placeholder="Pega aquí el fragmento que quieres revisar")
          label Tu pregunta
          textarea(v-model="mentorInput" rows="3" @keydown.ctrl.enter="sendMentorMessage" placeholder="¿Qué intentaste y qué resultado obtuviste?")
          button.mentor-send(type="button" @click="sendMentorMessage" :disabled="mentorSending || !mentorInput.trim()")
            span(v-if="mentorSending") Pensando...
            span(v-else) Preguntar al tutor
</template>

<script lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { Marked } from 'marked'
import neovimBasico from '@/assets/ide-help/00-neovim-basico.md?raw'
import helloJava from '@/assets/ide-help/10-hello-world-java.md?raw'
import helloPython from '@/assets/ide-help/11-hello-world-python.md?raw'
import helloJs from '@/assets/ide-help/12-hello-world-javascript-typescript.md?raw'
import helloBash from '@/assets/ide-help/13-hello-world-bash.md?raw'
import publishWebPage from '@/assets/ide-help/14-publicar-pagina-web.md?raw'
import deployPortal from '@/assets/ide-help/15-desplegar-portal-web.md?raw'
import deployApi from '@/assets/ide-help/16-desplegar-api-rest.md?raw'
import { getAiMentorConfig, chatAiMentor } from '@/services/api/surveyApi'
import type { AiMentorChatMessage } from '@/services/api/surveyApi'

// Orden fijo a proposito (no alfabetico): lo basico de Neovim primero, despues un "hello world"
// por lenguaje en el mismo orden en que la imagen del sandbox los instala (ver
// Dockerfile.code-ide-neovim) -- alguien que nunca uso Neovim necesita el modo de uso ANTES que
// un ejemplo de codigo especifico.
const TOPICS = [
  { id: 'neovim', title: '⌨️ Neovim básico', markdown: neovimBasico },
  { id: 'java', title: '☕ Hello World: Java', markdown: helloJava },
  { id: 'python', title: '🐍 Hello World: Python', markdown: helloPython },
  { id: 'js', title: '📜 Hello World: JS/TS', markdown: helloJs },
  { id: 'bash', title: '💲 Hello World: Bash', markdown: helloBash },
  { id: 'publish-web', title: '🌐 Publicar página web', markdown: publishWebPage },
  { id: 'deploy-portal', title: '🖥️ Desplegar portal web', markdown: deployPortal },
  { id: 'deploy-api', title: '🚀 Desplegar API REST', markdown: deployApi }
]

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
}

// Instancia propia (no el singleton "marked" global que tambien usa PublicEventDetailPage.vue)
// para que el renderer de codigo con boton de copiar no afecte a otras paginas.
const helpMarked = new Marked({
  renderer: {
    code(token: { text: string, lang?: string }) {
      const lang = (token.lang || '').trim().split(/\s+/)[0]
      const langClass = lang ? ` class="language-${escapeHtml(lang)}"` : ''
      return `<div class="code-block"><button type="button" class="copy-code-btn" aria-label="Copiar bloque de código">Copiar</button>`
        + `<pre><code${langClass}>${escapeHtml(token.text)}</code></pre></div>`
    }
  }
})

const HELP_WIDTH_STORAGE_KEY = 'insightbloom-ide-help-width'
const HELP_PANEL_MIN_WIDTH = 320
const HELP_PANEL_RIGHT_MARGIN = 40

export default {
  name: 'IdeHelpPanel',
  props: {
    conferenceId: { type: String, default: '' },
    token: { type: String, default: '' }
  },
  setup(props: { conferenceId: string, token: string }) {
    const open = ref(false)
    const activeId = ref(TOPICS[0].id)
    const mentorEnabled = ref(false)
    const mentorInput = ref('')
    const mentorCodeContext = ref('')
    const mentorMessages = ref<AiMentorChatMessage[]>([])
    const mentorSending = ref(false)
    const mentorError = ref('')

    const renderedActive = computed(() => {
      const topic = TOPICS.find(t => t.id === activeId.value)
      return topic ? helpMarked.parse(topic.markdown, { async: false }) : ''
    })

    function onHelpContentClick(event: MouseEvent) {
      const target = event.target as HTMLElement | null
      const button = target?.closest('.copy-code-btn') as HTMLButtonElement | null
      if (!button) return
      const code = button.closest('.code-block')?.querySelector('code')
      const text = code?.textContent || ''
      navigator.clipboard?.writeText(text).then(() => {
        const original = button.textContent
        button.textContent = '¡Copiado!'
        button.classList.add('copied')
        setTimeout(() => { button.textContent = original; button.classList.remove('copied') }, 1500)
      }).catch(() => {
        button.textContent = 'No se pudo copiar'
      })
    }

    // Ancho del panel: persistido en localStorage para que el usuario no tenga que
    // reajustarlo cada vez que abre la ayuda.
    const panelWidth = ref(Number(localStorage.getItem(HELP_WIDTH_STORAGE_KEY)) || 480)

    function clampPanelWidth(width: number): number {
      const max = Math.max(HELP_PANEL_MIN_WIDTH, window.innerWidth - HELP_PANEL_RIGHT_MARGIN)
      return Math.min(max, Math.max(HELP_PANEL_MIN_WIDTH, width))
    }

    function startResize(event: PointerEvent) {
      event.preventDefault()
      let resizing = true
      const onMove = (moveEvent: PointerEvent) => {
        if (!resizing) return
        panelWidth.value = clampPanelWidth(window.innerWidth - moveEvent.clientX)
      }
      const onUp = () => {
        resizing = false
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
        localStorage.setItem(HELP_WIDTH_STORAGE_KEY, String(panelWidth.value))
      }
      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    }

    const availableTopics = computed(() => mentorEnabled.value
      ? [...TOPICS, { id: 'mentor', title: '🤖 Tutor IA', markdown: '' }]
      : TOPICS)

    async function loadMentorConfig() {
      if (!props.conferenceId || !props.token) return
      try {
        const result = await getAiMentorConfig(props.conferenceId, props.token)
        mentorEnabled.value = result.data.enabled
        if (!mentorEnabled.value && activeId.value === 'mentor') activeId.value = TOPICS[0].id
      } catch {
        mentorEnabled.value = false
      }
    }

    async function sendMentorMessage() {
      const message = mentorInput.value.trim()
      if (!message || mentorSending.value || !mentorEnabled.value) return
      mentorSending.value = true
      mentorError.value = ''
      const history = mentorMessages.value.slice(-8)
      mentorMessages.value.push({ role: 'user', content: message })
      mentorInput.value = ''
      try {
        const result = await chatAiMentor(props.conferenceId, {
          message,
          codeContext: mentorCodeContext.value.trim() || undefined,
          history
        }, props.token)
        mentorMessages.value.push({ role: 'assistant', content: result.data.reply })
      } catch (error: any) {
        mentorMessages.value.pop()
        mentorInput.value = message
        mentorError.value = error.response?.data?.error?.message || 'No se pudo consultar al tutor IA.'
      } finally {
        mentorSending.value = false
      }
    }

    onMounted(loadMentorConfig)
    watch(open, value => { if (value) void loadMentorConfig() })

    return {
      open, activeId, availableTopics, renderedActive, mentorEnabled, mentorInput,
      mentorCodeContext, mentorMessages, mentorSending, mentorError, sendMentorMessage,
      onHelpContentClick, panelWidth, startResize
    }
  }
}
</script>

<style scoped>
.help-fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: var(--color-primary);
  color: var(--color-text-inverse);
  font-size: 1.4rem;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, background 0.15s ease;
}

.help-fab:hover {
  background: var(--color-primary-dark);
  transform: scale(1.06);
}

.help-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  max-width: 100vw;
  z-index: 1900;
  background: var(--color-surface);
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.help-resize-handle {
  position: absolute;
  top: 0;
  left: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  touch-action: none;
}

.help-resize-handle:hover,
.help-resize-handle:active {
  background: rgba(79, 70, 229, 0.25);
}

.help-nav {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--color-border-subtle);
}

.help-nav h3 {
  width: 100%;
  margin: 0 0 8px;
  font-size: 0.95rem;
  color: var(--color-heading);
}

.help-nav-item {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1.5px solid var(--color-border-subtle);
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.82rem;
  cursor: pointer;
  white-space: nowrap;
}

.help-nav-item:hover {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
}

.help-nav-item.active {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: var(--color-text-inverse);
}

.help-content {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 20px 24px 40px;
  font-size: 0.92rem;
  line-height: 1.6;
  color: var(--color-text);
}

.help-content :deep(h1) {
  font-size: 1.3rem;
  margin: 0 0 16px;
  color: var(--color-heading);
}

.help-content :deep(h2) {
  font-size: 1.05rem;
  margin: 28px 0 10px;
  color: var(--color-primary-dark);
}

.help-content :deep(p) {
  margin: 10px 0;
}

.help-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0 20px;
  font-size: 0.85rem;
}

.help-content :deep(th),
.help-content :deep(td) {
  border: 1px solid var(--color-border-subtle);
  padding: 6px 10px;
  text-align: left;
}

.help-content :deep(th) {
  background: var(--color-surface-muted);
}

.help-content :deep(code) {
  background: var(--color-surface-muted);
  padding: 1px 5px;
  border-radius: 4px;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 0.85em;
}

.help-content :deep(pre) {
  background: var(--color-heading);
  color: var(--color-primary-soft);
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0;
}

.help-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.help-content :deep(.code-block) {
  position: relative;
  margin: 12px 0;
}

.help-content :deep(.copy-code-btn) {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255, 255, 255, 0.12);
  color: var(--color-primary-soft);
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 6px;
  padding: 3px 9px;
  font-size: 0.72rem;
  cursor: pointer;
  z-index: 1;
}

.help-content :deep(.copy-code-btn:hover) {
  background: rgba(255, 255, 255, 0.22);
}

.help-content :deep(.copy-code-btn.copied) {
  background: var(--color-success);
  border-color: var(--color-success);
  color: var(--color-text-inverse);
}

.mentor-content {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 20px 24px 40px;
  color: var(--color-text);
}

.mentor-content h1 { margin: 0 0 10px; color: var(--color-heading); font-size: 1.3rem; }
.mentor-intro { color: var(--color-text-secondary); line-height: 1.5; }
.mentor-content label { display: block; margin: 16px 0 6px; font-size: 0.85rem; font-weight: 600; color: var(--color-text-secondary); }
.mentor-content textarea { width: 100%; box-sizing: border-box; resize: vertical; padding: 9px 10px; border: 1px solid var(--color-border); border-radius: 7px; font: inherit; }
.mentor-content textarea:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }
.mentor-messages { display: flex; flex-direction: column; gap: 8px; margin: 16px 0; }
.mentor-message { padding: 10px 12px; border-radius: 9px; background: var(--color-surface-muted); white-space: pre-wrap; }
.mentor-message.user { background: var(--color-primary-soft); }
.mentor-message p { margin: 4px 0 0; line-height: 1.45; }
.mentor-error { color: var(--color-danger-dark); font-size: 0.85rem; }
.mentor-send { margin-top: 10px; width: 100%; padding: 10px 14px; border: 0; border-radius: 7px; background: var(--color-primary); color: var(--color-text-inverse); cursor: pointer; font-weight: 600; }
.mentor-send:disabled { opacity: 0.6; cursor: wait; }

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

@media (max-width: 600px) {
  .help-panel {
    width: 100vw !important;
  }

  .help-resize-handle {
    display: none;
  }
}
</style>
