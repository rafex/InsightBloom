<template lang="pug">
.ide-help
  button#ide-help-toggle.help-fab(type="button" :style="fabStyle" @pointerdown="startFabDrag" @click="toggleOpen" :aria-expanded="open" aria-controls="ide-help-panel" :aria-label="open ? 'Cerrar ayuda de InsightBloom' : 'Abrir ayuda de InsightBloom'" :title="open ? 'Cerrar ayuda de InsightBloom' : 'Arrastra para mover o abrir la ayuda'")
    span(v-if="!open") 📖
    span(v-else) ✕

  transition(name="slide")
    aside#ide-help-panel.help-panel(v-if="open" :class="`side-${helpSide}`" role="complementary" aria-label="Ayuda de InsightBloom" :style="panelStyle")
      .help-resize-handle(@pointerdown="startResize" title="Arrastrá para cambiar el ancho")
      nav.help-nav(aria-label="Temas de ayuda")
        h3 Ayuda
        .help-side-picker(role="group" aria-label="Posición del panel de ayuda")
          span.help-side-label Posición:
          button.help-side-btn(
            v-for="side in panelSides"
            :key="side.id"
            type="button"
            :aria-pressed="helpSide === side.id"
            :class="{ active: helpSide === side.id }"
            @click="setHelpSide(side.id)"
          ) {{ side.label }}
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
        FeedbackMessage.mentor-error(v-if="mentorError" :message="mentorError" tone="error")
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
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { Marked } from 'marked'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
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
const HELP_FAB_POSITION_STORAGE_KEY = 'insightbloom-ide-help-fab-position'
const HELP_PANEL_SIDE_STORAGE_KEY = 'insightbloom-ide-help-side'
const HELP_PANEL_MIN_WIDTH = 320
const HELP_PANEL_RIGHT_MARGIN = 40
const HELP_FAB_SIZE = 52
const HELP_FAB_MARGIN = 16

type HelpSide = 'left' | 'right' | 'top' | 'bottom'
type FabPosition = { left: number, top: number }

const PANEL_SIDES: Array<{ id: HelpSide, label: string }> = [
  { id: 'left', label: 'Izquierda' },
  { id: 'right', label: 'Derecha' },
  { id: 'top', label: 'Arriba' },
  { id: 'bottom', label: 'Abajo' }
]

function readStoredFabPosition(): FabPosition | null {
  try {
    const value = JSON.parse(localStorage.getItem(HELP_FAB_POSITION_STORAGE_KEY) || 'null')
    if (value && Number.isFinite(value.left) && Number.isFinite(value.top)) {
      return { left: value.left, top: value.top }
    }
  } catch {
    // El almacenamiento puede estar deshabilitado en una ventana privada.
  }
  return null
}

function readStoredHelpSide(): HelpSide {
  try {
    const value = localStorage.getItem(HELP_PANEL_SIDE_STORAGE_KEY)
    if (PANEL_SIDES.some(side => side.id === value)) return value as HelpSide
  } catch {
    // El almacenamiento puede estar deshabilitado en una ventana privada.
  }
  return 'right'
}

export default {
  name: 'IdeHelpPanel',
  components: { FeedbackMessage },
  props: {
    conferenceId: { type: String, default: '' },
    token: { type: String, default: '' }
  },
  setup(props: { conferenceId: string, token: string }) {
    const open = ref(false)
    const helpSide = ref<HelpSide>(readStoredHelpSide())
    const panelSides = PANEL_SIDES
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

    function clampFabPosition(position: FabPosition): FabPosition {
      const maxLeft = Math.max(HELP_FAB_MARGIN, window.innerWidth - HELP_FAB_SIZE - HELP_FAB_MARGIN)
      const maxTop = Math.max(HELP_FAB_MARGIN, window.innerHeight - HELP_FAB_SIZE - HELP_FAB_MARGIN)
      return {
        left: Math.min(maxLeft, Math.max(HELP_FAB_MARGIN, position.left)),
        top: Math.min(maxTop, Math.max(HELP_FAB_MARGIN, position.top))
      }
    }

    const defaultFabPosition: FabPosition = {
      left: typeof window === 'undefined' ? HELP_FAB_MARGIN : window.innerWidth - HELP_FAB_SIZE - HELP_FAB_MARGIN,
      top: typeof window === 'undefined' ? HELP_FAB_MARGIN : window.innerHeight - HELP_FAB_SIZE - HELP_FAB_MARGIN
    }
    const fabPosition = ref<FabPosition>(readStoredFabPosition() || defaultFabPosition)
    const suppressNextClick = ref(false)
    const fabStyle = computed(() => ({
      left: `${fabPosition.value.left}px`,
      top: `${fabPosition.value.top}px`
    }))
    const panelStyle = computed(() => {
      if (helpSide.value === 'left' || helpSide.value === 'right') {
        return { width: `${panelWidth.value}px` }
      }
      return { width: '100%' }
    })

    function persistFabPosition() {
      try {
        localStorage.setItem(HELP_FAB_POSITION_STORAGE_KEY, JSON.stringify(fabPosition.value))
      } catch {
        // La ayuda sigue funcionando aunque el almacenamiento no esté disponible.
      }
    }

    function keepFabInViewport() {
      const next = clampFabPosition(fabPosition.value)
      if (next.left !== fabPosition.value.left || next.top !== fabPosition.value.top) {
        fabPosition.value = next
        persistFabPosition()
      }
    }

    function startFabDrag(event: PointerEvent) {
      const start = { x: event.clientX, y: event.clientY }
      const origin = { ...fabPosition.value }
      let moved = false

      const onMove = (moveEvent: PointerEvent) => {
        const next = clampFabPosition({
          left: origin.left + moveEvent.clientX - start.x,
          top: origin.top + moveEvent.clientY - start.y
        })
        moved = moved || Math.abs(moveEvent.clientX - start.x) > 4 || Math.abs(moveEvent.clientY - start.y) > 4
        fabPosition.value = next
      }

      const onUp = () => {
        window.removeEventListener('pointermove', onMove)
        window.removeEventListener('pointerup', onUp)
        if (moved) {
          suppressNextClick.value = true
          persistFabPosition()
        }
      }

      window.addEventListener('pointermove', onMove)
      window.addEventListener('pointerup', onUp)
    }

    function toggleOpen() {
      if (suppressNextClick.value) {
        suppressNextClick.value = false
        return
      }
      open.value = !open.value
    }

    function setHelpSide(side: HelpSide) {
      if (!PANEL_SIDES.some(item => item.id === side)) return
      helpSide.value = side
      try {
        localStorage.setItem(HELP_PANEL_SIDE_STORAGE_KEY, side)
      } catch {
        // La posición solo deja de persistir; el cambio actual sí se aplica.
      }
    }

    function clampPanelWidth(width: number): number {
      const max = Math.max(HELP_PANEL_MIN_WIDTH, window.innerWidth - HELP_PANEL_RIGHT_MARGIN)
      return Math.min(max, Math.max(HELP_PANEL_MIN_WIDTH, width))
    }

    function startResize(event: PointerEvent) {
      event.preventDefault()
      let resizing = true
      const onMove = (moveEvent: PointerEvent) => {
        if (!resizing) return
        const width = helpSide.value === 'left'
          ? moveEvent.clientX
          : window.innerWidth - moveEvent.clientX
        panelWidth.value = clampPanelWidth(width)
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

    onMounted(() => {
      fabPosition.value = clampFabPosition(fabPosition.value)
      window.addEventListener('resize', keepFabInViewport)
      void loadMentorConfig()
    })
    onBeforeUnmount(() => window.removeEventListener('resize', keepFabInViewport))
    watch(open, value => { if (value) void loadMentorConfig() })

    return {
      open, activeId, availableTopics, renderedActive, mentorEnabled, mentorInput,
      mentorCodeContext, mentorMessages, mentorSending, mentorError, sendMentorMessage,
      onHelpContentClick, panelWidth, startResize, helpSide, panelSides, setHelpSide,
      panelStyle, fabStyle, startFabDrag, toggleOpen
    }
  }
}
</script>

<style scoped>
.help-fab {
  position: fixed;
  z-index: 2000;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: 2px solid var(--color-warning);
  background: var(--color-heading);
  color: var(--color-text-inverse);
  font-size: 1.5rem;
  cursor: grab;
  touch-action: none;
  user-select: none;
  box-shadow: 0 0 0 3px var(--color-primary), 0 8px 24px rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, background 0.15s ease;
}

.help-fab:hover {
  background: var(--color-primary-dark);
  transform: scale(1.06);
}

.help-fab:active {
  cursor: grabbing;
}

.help-fab:focus-visible {
  outline: 3px solid var(--color-warning);
  outline-offset: 4px;
}

.help-panel {
  position: fixed;
  max-width: 100vw;
  z-index: 1900;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.help-panel.side-right {
  top: 0;
  right: 0;
  bottom: 0;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.18);
}

.help-panel.side-left {
  top: 0;
  left: 0;
  bottom: 0;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.18);
}

.help-panel.side-top {
  top: 0;
  left: 0;
  right: 0;
  height: min(70vh, calc(100vh - 24px));
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.18);
}

.help-panel.side-bottom {
  right: 0;
  bottom: 0;
  left: 0;
  height: min(70vh, calc(100vh - 24px));
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.18);
}

.help-resize-handle {
  position: absolute;
  top: 0;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  touch-action: none;
}

.help-panel.side-right .help-resize-handle {
  left: -4px;
}

.help-panel.side-left .help-resize-handle {
  right: -4px;
}

.help-panel.side-top .help-resize-handle,
.help-panel.side-bottom .help-resize-handle {
  display: none;
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

.help-side-picker {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-bottom: 4px;
}

.help-side-label {
  margin-right: 2px;
  color: var(--color-text-secondary);
  font-size: 0.78rem;
  font-weight: 600;
}

.help-side-btn {
  padding: 4px 8px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  cursor: pointer;
}

.help-side-btn:hover,
.help-side-btn:focus-visible {
  border-color: var(--color-primary-border);
  background: var(--color-primary-soft);
}

.help-side-btn.active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary-dark);
  font-weight: 700;
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
.mentor-send { margin-top: 10px; width: 100%; padding: 10px 14px; border: 0; border-radius: 7px; background: var(--color-primary); color: var(--color-text-inverse); cursor: pointer; font-weight: 600; }
.mentor-send:disabled { opacity: 0.6; cursor: wait; }

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s ease;
}

.slide-enter-from.side-right,
.slide-leave-to.side-right {
  transform: translateX(100%);
}

.slide-enter-from.side-left,
.slide-leave-to.side-left {
  transform: translateX(-100%);
}

.slide-enter-from.side-top,
.slide-leave-to.side-top {
  transform: translateY(-100%);
}

.slide-enter-from.side-bottom,
.slide-leave-to.side-bottom {
  transform: translateY(100%);
}

@media (max-width: 600px) {
  .help-panel.side-left,
  .help-panel.side-right {
    width: 100vw !important;
  }

  .help-panel.side-top,
  .help-panel.side-bottom {
    height: min(80vh, calc(100vh - 24px));
  }

  .help-panel .help-resize-handle {
    display: none;
  }
}
</style>
