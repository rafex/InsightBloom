<template lang="pug">
.dropdown-menu(ref="rootEl")
  button.dropdown-trigger(ref="triggerEl" type="button" @click="toggle" @keydown="handleTriggerKeydown" :aria-expanded="open" aria-haspopup="menu")
    | {{ label }}
    span.dropdown-caret ▾
  Teleport(to="body")
    .dropdown-panel.dropdown-panel-fixed(v-if="open" ref="panelEl" role="menu" :aria-label="label" :style="panelStyle" @keydown="handlePanelKeydown" @click="closeMenu()")
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
    const triggerEl = ref<HTMLButtonElement | null>(null)
    const panelEl = ref<HTMLElement | null>(null)
    const panelStyle = ref<Record<string, string>>({})

    function menuItems(): HTMLElement[] {
      const items = Array.from(panelEl.value?.querySelectorAll<HTMLElement>('a, button, [tabindex]:not([tabindex="-1"])') || [])
        .filter((item) => !item.hasAttribute('disabled') && item.getAttribute('aria-disabled') !== 'true')
      items.forEach((item) => item.setAttribute('role', 'menuitem'))
      return items
    }

    function focusMenuItem(index: number) {
      const items = menuItems()
      if (!items.length) return
      const target = items[(index + items.length) % items.length]
      target.focus()
    }

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

    function openMenu(focusIndex: number | null = null) {
      open.value = true
      const trigger = rootEl.value?.getBoundingClientRect()
      if (trigger) {
        panelStyle.value = {
          top: `${trigger.bottom + 4}px`,
          left: `${trigger.left}px`
        }
      }
      nextTick(() => {
        positionPanel()
        if (focusIndex !== null) focusMenuItem(focusIndex)
      })
    }

    function closeMenu(returnFocus = false) {
      open.value = false
      if (returnFocus) nextTick(() => triggerEl.value?.focus())
    }

    function toggle() {
      if (open.value) closeMenu(true)
      else openMenu()
    }

    function handleTriggerKeydown(event: KeyboardEvent) {
      if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        openMenu(0)
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        openMenu(-1)
      } else if (event.key === 'Escape' && open.value) {
        event.preventDefault()
        closeMenu(true)
      }
    }

    function handlePanelKeydown(event: KeyboardEvent) {
      const items = menuItems()
      const currentIndex = items.indexOf(document.activeElement as HTMLElement)
      if (event.key === 'ArrowDown') {
        event.preventDefault(); focusMenuItem(currentIndex + 1)
      } else if (event.key === 'ArrowUp') {
        event.preventDefault(); focusMenuItem(currentIndex - 1)
      } else if (event.key === 'Home') {
        event.preventDefault(); focusMenuItem(0)
      } else if (event.key === 'End') {
        event.preventDefault(); focusMenuItem(items.length - 1)
      } else if (event.key === 'Escape') {
        event.preventDefault(); closeMenu(true)
      } else if (event.key === 'Tab') {
        closeMenu()
      }
    }

    function onDocumentClick(e: MouseEvent) {
      if (rootEl.value && !rootEl.value.contains(e.target as Node)
        && !panelEl.value?.contains(e.target as Node)) {
        closeMenu()
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

    return { open, rootEl, triggerEl, panelEl, panelStyle, toggle, closeMenu, handleTriggerKeydown, handlePanelKeydown }
  }
}
</script>

<style scoped>
.dropdown-menu { position: relative; display: inline-block; }

.dropdown-trigger {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; color: var(--color-text-muted); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-md);
  font-size: 0.78rem; background: var(--color-surface); cursor: pointer;
}
.dropdown-trigger:hover { background: var(--color-surface-muted); color: var(--color-text-secondary); }
.dropdown-trigger:focus-visible { outline: 3px solid var(--color-focus); outline-offset: 2px; }
.dropdown-caret { font-size: 0.7rem; }

.dropdown-panel {
  min-width: 160px; background: var(--color-surface); border: 1px solid var(--color-border-subtle); border-radius: var(--radius-md);
  max-width: min(320px, calc(100vw - 16px)); box-shadow: var(--shadow-dropdown); padding: 4px; display: flex; flex-direction: column;
}
.dropdown-panel-fixed { position: fixed; z-index: 1000; }

.dropdown-panel :deep(a), .dropdown-panel :deep(button) {
  display: block; width: 100%; text-align: left; padding: 8px 10px; border-radius: 6px;
  color: var(--color-text-secondary); text-decoration: none; font-size: 0.85rem; border: none; background: none; cursor: pointer;
}
.dropdown-panel :deep(a:hover), .dropdown-panel :deep(button:hover) { background: var(--color-surface-muted); }
.dropdown-panel :deep(a:focus-visible), .dropdown-panel :deep(button:focus-visible) { outline: 2px solid var(--color-focus); outline-offset: -2px; }
.dropdown-panel :deep(.menu-item-danger) { color: var(--color-danger); }
.dropdown-panel :deep(button.menu-item-danger:hover) { background: var(--color-danger-soft); color: var(--color-danger-dark); }
.dropdown-panel :deep(button:disabled) { opacity: 0.55; cursor: not-allowed; }
</style>
