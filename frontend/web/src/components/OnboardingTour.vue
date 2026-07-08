<template lang="pug">
.tour-overlay(v-if="visible" @click.self="skip")
  .tour-highlight(v-if="targetRect" :style="highlightStyle")
  .tour-popover(:style="popoverStyle")
    p.tour-text {{ steps[stepIndex].text }}
    .tour-actions
      span.tour-progress {{ stepIndex + 1 }} / {{ steps.length }}
      button.tour-skip(type="button" @click="skip") Saltar
      button.tour-next(type="button" @click="next")
        span(v-if="stepIndex < steps.length - 1") Siguiente
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
    const targetRect = ref(null)

    function measure() {
      const step = props.steps[stepIndex.value]
      if (!step) return
      const el = document.querySelector(step.selector)
      targetRect.value = el ? el.getBoundingClientRect() : null
    }

    function updatePosition() { measure() }

    async function tryStart(attemptsLeft) {
      await nextTick()
      const firstSelector = props.steps[0]?.selector
      const found = firstSelector && document.querySelector(firstSelector)
      if (found) {
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
      if (stepIndex.value < props.steps.length - 1) {
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

    return { visible, stepIndex, targetRect, next, skip, highlightStyle, popoverStyle }
  }
}
</script>

<style scoped>
.tour-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 12, 41, 0.55);
  z-index: 3000;
}
.tour-highlight {
  position: fixed;
  border: 2px solid #4f46e5;
  border-radius: 10px;
  box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.25);
  pointer-events: none;
  transition: all 0.2s ease;
}
.tour-popover {
  position: fixed;
  max-width: 300px;
  background: #fff;
  border-radius: 12px;
  padding: 16px 18px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.3);
}
.tour-text { margin: 0 0 14px; color: #1e1b4b; font-size: 0.92rem; line-height: 1.4; }
.tour-actions { display: flex; align-items: center; gap: 10px; }
.tour-progress { font-size: 0.75rem; color: #9ca3af; margin-right: auto; }
.tour-skip {
  background: none; border: none; color: #6b7280; font-size: 0.85rem; cursor: pointer;
}
.tour-next {
  padding: 6px 16px; background: #4f46e5; color: #fff; border: none; border-radius: 8px;
  font-weight: 600; font-size: 0.85rem; cursor: pointer;
}
.tour-next:hover { background: #4338ca; }
</style>
