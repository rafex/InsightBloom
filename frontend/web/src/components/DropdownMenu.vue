<template lang="pug">
.dropdown-menu(ref="rootEl")
  button.dropdown-trigger(type="button" @click="toggle" @keydown.esc="open = false" :aria-expanded="open" aria-haspopup="menu")
    | {{ label }}
    span.dropdown-caret ▾
  Teleport(to="body")
    .dropdown-panel.dropdown-panel-fixed(v-if="open" ref="panelEl" role="menu" :style="panelStyle" @click="open = false")
      slot
</template>

<script lang="ts">
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'

export default {
  name: 'DropdownMenu',
  props: {
    label: { type: String, required: true }
  },
  setup() {
    const open = ref(false)
    const rootEl = ref<HTMLElement | null>(null)
    const panelEl = ref<HTMLElement | null>(null)
    const panelStyle = ref<Record<string, string>>({})

    function positionPanel() {
      if (!open.value || !rootEl.value || !panelEl.value) return
      const trigger = rootEl.value.getBoundingClientRect()
      const panel = panelEl.value.getBoundingClientRect()
      const gap = 4
      const margin = 8
      const top = trigger.bottom + gap + panel.height <= window.innerHeight
        ? trigger.bottom + gap
        : Math.max(margin, trigger.top - panel.height - gap)
      const left = Math.min(
        Math.max(margin, trigger.left),
        Math.max(margin, window.innerWidth - panel.width - margin)
      )
      panelStyle.value = {
        top: `${top}px`,
        left: `${left}px`
      }
    }

    function toggle() {
      open.value = !open.value
      if (open.value) {
        const trigger = rootEl.value?.getBoundingClientRect()
        if (trigger) {
          panelStyle.value = {
            top: `${trigger.bottom + 4}px`,
            left: `${trigger.left}px`
          }
        }
        nextTick(positionPanel)
      }
    }

    function onDocumentClick(e: MouseEvent) {
      if (rootEl.value && !rootEl.value.contains(e.target as Node)
        && !panelEl.value?.contains(e.target as Node)) {
        open.value = false
      }
    }

    onMounted(() => document.addEventListener('click', onDocumentClick))
    onMounted(() => {
      window.addEventListener('resize', positionPanel)
      window.addEventListener('scroll', positionPanel, true)
    })
    onBeforeUnmount(() => {
      document.removeEventListener('click', onDocumentClick)
      window.removeEventListener('resize', positionPanel)
      window.removeEventListener('scroll', positionPanel, true)
    })

    return { open, rootEl, panelEl, panelStyle, toggle }
  }
}
</script>

<style scoped>
.dropdown-menu { position: relative; display: inline-block; }

.dropdown-trigger {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; color: #6b7280; border: 1px solid #e5e7eb; border-radius: 8px;
  font-size: 0.78rem; background: #fff; cursor: pointer;
}
.dropdown-trigger:hover { background: #f3f4f6; color: #374151; }
.dropdown-trigger:focus-visible { outline: 3px solid var(--color-focus, #818cf8); outline-offset: 2px; }
.dropdown-caret { font-size: 0.7rem; }

.dropdown-panel {
  min-width: 160px; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.1); padding: 4px; display: flex; flex-direction: column;
}
.dropdown-panel-fixed { position: fixed; z-index: 1000; }

.dropdown-panel :deep(a), .dropdown-panel :deep(button) {
  display: block; width: 100%; text-align: left; padding: 8px 10px; border-radius: 6px;
  color: #374151; text-decoration: none; font-size: 0.85rem; border: none; background: none; cursor: pointer;
}
.dropdown-panel :deep(a:hover), .dropdown-panel :deep(button:hover) { background: #f3f4f6; }
.dropdown-panel :deep(a:focus-visible), .dropdown-panel :deep(button:focus-visible) { outline: 2px solid var(--color-focus, #818cf8); outline-offset: -2px; }
.dropdown-panel :deep(.menu-item-danger) { color: var(--color-danger); }
.dropdown-panel :deep(button.menu-item-danger:hover) { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.dropdown-panel :deep(button:disabled) { opacity: 0.55; cursor: not-allowed; }
</style>
