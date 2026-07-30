<template lang="pug">
.email-compose-editor
  .format-toolbar
    .format-tabs
      button.format-tab(v-for="option in formatOptions" :key="option.value" type="button" :class="{ active: format === option.value }" @click="selectFormat(option.value)")
        | {{ option.label }}
      button.ai-toggle(type="button" :class="{ active: showAiAssistant }" @click="toggleAi")
        | ✨ Asistente IA
    .preview-toggle
      label
        input(type="checkbox" v-model="showPreview")
        span Vista previa
  textarea(
    ref="textareaEl"
    :value="modelValue"
    @input="onInput"
    rows="6"
    :placeholder="placeholder"
  )
  .preview-pane(v-if="showPreview")
    .preview-header Previsualización
    .preview-body(v-html="renderedPreview")
</template>

<script lang="ts">
import { computed, ref } from 'vue'
import { Marked } from 'marked'

const FORMAT_OPTIONS = [
  { value: 'markdown' as const, label: 'Markdown' },
  { value: 'html' as const, label: 'HTML' },
  { value: 'text' as const, label: 'Texto plano' }
]

const ALLOWED_TAGS_FOR_STRIP = /<\/?(?:html|head|body|script|style|iframe|link|meta|title|base|form|input|button|select|option|textarea|object|embed|param|applet|frame|frameset|noscript)(?:\s[^>]*)?>/gi
const STRIP_ATTRS = /\s(?:on\w+|style|id|class)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi

const previewMarked = new Marked()

function sanitizeHtmlForPreview(html: string): string {
  if (!html) return ''
  return html.replace(ALLOWED_TAGS_FOR_STRIP, '').replace(STRIP_ATTRS, '')
}

function renderPlainText(text: string): string {
  if (!text) return ''
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
    .replace(/\n/g, '<br>')
}

export default {
  name: 'EmailComposeEditor',
  props: {
    modelValue: { type: String, default: '' },
    format: { type: String as () => 'markdown' | 'html' | 'text', default: 'markdown' },
    showAiAssistant: { type: Boolean, default: false }
  },
  emits: ['update:modelValue', 'update:format', 'update:showAiAssistant', 'askAi'],
  setup(props: { modelValue: string, format: string, showAiAssistant: boolean }, context: any) {
    const showPreview = ref(false)
    const textareaEl = ref<HTMLTextAreaElement | null>(null)

    const placeholder = computed(() => {
      switch (props.format) {
        case 'markdown': return 'Usa **negritas**, listas, encabezados ## ...'
        case 'html': return 'Escribe HTML: <p>, <strong>, <em>, <ul>, <ol>, <li>, <h3>...'
        default: return 'Escribí el mensaje para los inscritos...'
      }
    })

    function selectFormat(value: 'markdown' | 'html' | 'text') {
      context.emit('update:format', value)
    }

    function onInput(event: Event) {
      const target = event.target as HTMLTextAreaElement
      context.emit('update:modelValue', target.value)
    }

    function toggleAi() {
      context.emit('update:showAiAssistant', !props.showAiAssistant)
    }

    const renderedPreview = computed(() => {
      const value = props.modelValue
      if (!value) return ''
      switch (props.format) {
        case 'markdown': {
          const html = previewMarked.parse(value, { async: false }) as string
          return sanitizeHtmlForPreview(html)
        }
        case 'html':
          return sanitizeHtmlForPreview(value)
        default:
          return renderPlainText(value)
      }
    })

    return {
      formatOptions: FORMAT_OPTIONS, showPreview, textareaEl, placeholder,
      selectFormat, onInput, toggleAi, renderedPreview
    }
  }
}
</script>

<style scoped>
.email-compose-editor {
  display: flex; flex-direction: column; gap: 10px;
}
.format-toolbar {
  display: flex; justify-content: space-between; align-items: center;
  flex-wrap: wrap; gap: 8px;
}
.format-tabs {
  display: flex; gap: 4px;
}
.format-tab {
  padding: 5px 12px; font-size: .8rem; border: 1px solid var(--color-border);
  border-radius: 6px; background: var(--color-surface); color: var(--color-text-secondary);
  cursor: pointer;
}
.format-tab.active {
  background: var(--color-primary); color: var(--color-text-inverse); border-color: var(--color-primary);
}
.ai-toggle {
  padding: 5px 12px; font-size: .8rem; border: 1px solid var(--color-border);
  border-radius: 6px; background: var(--color-surface); color: var(--color-text-secondary);
  cursor: pointer; margin-left: 8px;
}
.ai-toggle.active {
  background: var(--color-primary-soft); border-color: var(--color-primary);
  color: var(--color-primary-dark);
}
.preview-toggle {
  display: flex; align-items: center;
}
.preview-toggle label {
  display: flex; align-items: center; gap: 6px; font-size: .8rem;
  color: var(--color-text-muted); cursor: pointer;
}
.preview-toggle input { width: auto; min-width: auto; flex: none; margin: 0; }
textarea {
  width: 100%; box-sizing: border-box; padding: 10px; border: 1px solid var(--color-border);
  border-radius: 8px; font: inherit; resize: vertical;
}
.preview-pane {
  border: 1px solid var(--color-border-subtle); border-radius: 8px;
  background: var(--color-surface); padding: 12px; max-height: 240px; overflow-y: auto;
}
.preview-header {
  font-size: .7rem; text-transform: uppercase; letter-spacing: .04em;
  color: var(--color-text-muted); margin-bottom: 8px;
}
.preview-body {
  font-size: .88rem; line-height: 1.6; color: var(--color-text);
}
.preview-body :deep(h3) { margin: 0 0 4px; font-size: 1rem; }
.preview-body :deep(p) { margin: 0 0 6px; }
.preview-body :deep(strong) { font-weight: 700; }
.preview-body :deep(em) { font-style: italic; }
.preview-body :deep(ul), .preview-body :deep(ol) { margin: 0 0 6px; padding-left: 18px; }
.preview-body :deep(a) { color: var(--color-primary); }
.preview-body :deep(blockquote) { margin: 0 0 6px; padding: 4px 12px; border-left: 3px solid var(--color-primary); background: var(--color-surface-muted); }
.preview-body :deep(code) { background: var(--color-surface-muted); padding: 1px 4px; border-radius: 3px; font-size: .82rem; }
.preview-body :deep(pre) { background: var(--color-surface-muted); padding: 8px; border-radius: 6px; overflow-x: auto; }
.preview-body :deep(hr) { border: none; border-top: 1px solid var(--color-border); margin: 8px 0; }
</style>
