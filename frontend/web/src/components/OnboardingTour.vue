<template lang="pug">
.tour-overlay(v-if="visible" @click.self="skip")
  .tour-highlight(v-if="targetRect" :style="highlightStyle")
  .tour-popover(:style="popoverStyle")
    p.tour-text {{ activeSteps[stepIndex].text }}
    .tour-actions
      span.tour-progress {{ stepIndex + 1 }} / {{ activeSteps.length }}
      button.tour-skip(type="button" @click="skip") Saltar
      button.tour-next(type="button" @click="next")
        span(v-if="stepIndex < activeSteps.length - 1") Siguiente
        span(v-else) Entendido
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, type PropType } from 'vue'

interface TourStep {
  selector: string
  text: string
}

export default {
  name: 'OnboardingTour',
  props: {
    storageKey: { type: String, required: true },
    steps: { type: Array as PropType<TourStep[]>, required: true }
  },
  setup(props: { storageKey: string, steps: TourStep[] }) {
    const visible = ref(false)
    const stepIndex = ref(0)
    const targetRect = ref<DOMRect | null>(null)
    const activeSteps = ref<TourStep[]>([])

    function measure() {
      const step = activeSteps.value[stepIndex.value]
      if (!step) return
      const el = document.querySelector(step.selector)
      targetRect.value = el ? el.getBoundingClientRect() : null
    }

    function updatePosition() { measure() }

    async function tryStart(attemptsLeft: number) {
      await nextTick()
      const found = props.steps.some(step => document.querySelector(step.selector))
      if (found) {
        // A conference can hide tools according to its capabilities and canvas
        // configuration. The tour must describe only the controls the attendee
        // can actually use instead of showing empty steps.
        await new Promise(resolve => setTimeout(resolve, 150))
        activeSteps.value = props.steps.filter(step => document.querySelector(step.selector))
        stepIndex.value = 0
        if (activeSteps.value.length === 0) return
        visible.value = true
        measure()
        window.addEventListener('resize', updatePosition)
        window.addEventListener('scroll', updatePosition, true)
        return
      }
      if (attemptsLeft > 0) {
        setTimeout(() => tryStart(attemptsLeft - 1), 400)
      }
    }

    function finish() {
      visible.value = false
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
      localStorage.setItem(props.storageKey, '1')
    }

    function next() {
      if (stepIndex.value < activeSteps.value.length - 1) {
        stepIndex.value += 1
        measure()
      } else {
        finish()
      }
    }

    function skip() {
      finish()
    }

    onMounted(() => {
      if (localStorage.getItem(props.storageKey)) return
      tryStart(8)
    })

    onBeforeUnmount(() => {
      window.removeEventListener('resize', updatePosition)
      window.removeEventListener('scroll', updatePosition, true)
    })

    const highlightStyle = computed(() => {
      if (!targetRect.value) return {}
      const r = targetRect.value
      return {
        top: `${r.top - 6}px`,
        left: `${r.left - 6}px`,
        width: `${r.width + 12}px`,
        height: `${r.height + 12}px`
      }
    })

    const popoverStyle = computed(() => {
      if (!targetRect.value) {
        return { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }
      }
      const r = targetRect.value
      const top = Math.min(r.bottom + 14, window.innerHeight - 160)
      const left = Math.min(Math.max(r.left, 12), window.innerWidth - 320)
      return { top: `${top}px`, left: `${left}px` }
    })

    return { visible, stepIndex, activeSteps, targetRect, next, skip, highlightStyle, popoverStyle }
  }
}
</script>

<style scoped>
.tour-overlay {
  position: fixed;
  inset: 0;
  background: var(--color-onboarding-overlay);
  z-index: 3000;
}
.tour-highlight {
  position: fixed;
  border: 2px solid var(--color-primary);
  border-radius: 10px;
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.25);
  pointer-events: none;
  transition: all 0.2s ease;
}
.tour-popover {
  position: fixed;
  max-width: 300px;
  background: var(--color-surface);
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: var(--shadow-onboarding);
}
.tour-text { margin: 0 0 14px; color: var(--color-heading); font-size: 0.92rem; line-height: 1.4; }
.tour-actions { display: flex; align-items: center; gap: 10px; }
.tour-progress { font-size: 0.75rem; color: var(--color-text-muted); margin-right: auto; }
.tour-skip {
  background: none; border: none; color: var(--color-text-muted); font-size: 0.85rem; cursor: pointer;
}
.tour-next {
  padding: 6px 16px; background: var(--color-primary); color: var(--color-text-inverse); border: none; border-radius: 8px;
  font-weight: 600; font-size: 0.85rem; cursor: pointer;
}
.tour-next:hover { background: var(--color-primary-dark); }
</style>
