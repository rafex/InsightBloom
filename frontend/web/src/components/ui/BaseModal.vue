<template lang="pug">
teleport(to="body")
  .modal-overlay(@click.self="onDismiss" @keydown.esc="onDismiss")
    .modal-dialog(
      ref="dialogRef"
      role="dialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      tabindex="-1"
    )
      h4.modal-title(:id="titleId")
        slot(name="title") {{ title }}
      .modal-body
        slot
      .modal-actions
        slot(name="actions")
          BaseButton(variant="ghost" @click="onDismiss") Cancelar
          BaseButton(:variant="confirmVariant" :loading="loading" @click="$emit('confirm')") {{ confirmLabel }}
</template>

<script lang="ts">
// Modal canónico (auditoría UX 2026-07-26): reemplaza tanto los window.confirm() nativos como
// los .confirm-overlay ad-hoc repetidos por página. A11y que ninguno de los dos tenía completa:
// role=dialog + aria-modal, cierre con Escape, foco inicial dentro del diálogo y devolución del
// foco al elemento que lo abrió.
import { ref, onMounted, onBeforeUnmount } from 'vue'
import BaseButton from './BaseButton.vue'

let modalSeq = 0

export default {
  name: 'BaseModal',
  components: { BaseButton },
  props: {
    title: { type: String, default: '' },
    confirmLabel: { type: String, default: 'Confirmar' },
    confirmVariant: { type: String, default: 'primary' }, // primary | danger
    loading: { type: Boolean, default: false },
    // Si es true, clic afuera / Escape no cierran (para confirmaciones destructivas en curso).
    persistent: { type: Boolean, default: false }
  },
  emits: ['confirm', 'close'],
  setup(props, { emit }) {
    const dialogRef = ref<HTMLElement | null>(null)
    const titleId = `base-modal-title-${++modalSeq}`
    let previouslyFocused: HTMLElement | null = null

    const onDismiss = () => {
      if (!props.persistent && !props.loading) emit('close')
    }

    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { onDismiss(); return }
      if (e.key !== 'Tab' || !dialogRef.value) return
      // Focus trap simple: Tab cicla dentro del diálogo.
      const focusables = dialogRef.value.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
      )
      if (!focusables.length) return
      const first = focusables[0]
      const last = focusables[focusables.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }

    onMounted(() => {
      previouslyFocused = document.activeElement as HTMLElement | null
      document.addEventListener('keydown', onKeydown)
      dialogRef.value?.focus()
    })
    onBeforeUnmount(() => {
      document.removeEventListener('keydown', onKeydown)
      previouslyFocused?.focus()
    })

    return { dialogRef, titleId, onDismiss }
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  z-index: 1000;
}
.modal-dialog {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-modal);
  padding: var(--space-6);
  width: 100%;
  max-width: 440px;
  outline: none;
}
.modal-title { margin: 0 0 var(--space-3); font-size: 1.1rem; color: var(--color-text); }
.modal-body { color: var(--color-text-secondary); font-size: 0.95rem; }
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-5);
}
</style>
