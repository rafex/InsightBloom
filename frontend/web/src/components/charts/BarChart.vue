<template lang="pug">
svg.bar-chart(:viewBox="`0 0 ${width} ${height}`" preserveAspectRatio="xMidYMid meet")
  g(v-for="(bar, i) in bars" :key="bar.label")
    rect.bar(
      :x="bar.x" :y="bar.y" :width="barWidth" :height="bar.barHeight"
      :fill="color"
    )
    text.bar-value(:x="bar.x + barWidth / 2" :y="bar.y - 6" text-anchor="middle") {{ bar.value }}
    text.bar-label(:x="bar.x + barWidth / 2" :y="height - 4" text-anchor="middle") {{ bar.label }}
</template>

<script lang="ts">
import { computed, type PropType } from 'vue'

interface BarDatum {
  label: string
  value: number
}

export default {
  name: 'BarChart',
  props: {
    data: { type: Array as PropType<BarDatum[]>, required: true },
    width: { type: Number, default: 320 },
    height: { type: Number, default: 140 },
    color: { type: String, default: 'var(--color-primary)' }
  },
  setup(props: { data: BarDatum[], width: number, height: number, color: string }) {
    const chartHeight = props.height - 24
    const barWidth = computed(() => Math.max(16, (props.width / Math.max(props.data.length, 1)) - 12))

    const bars = computed(() => {
      const maxValue = Math.max(1, ...props.data.map((d) => d.value))
      const gap = props.width / Math.max(props.data.length, 1)
      return props.data.map((d, i) => {
        const barHeight = (d.value / maxValue) * chartHeight
        return {
          label: d.label,
          value: d.value,
          x: i * gap + (gap - barWidth.value) / 2,
          y: chartHeight - barHeight + 16,
          barHeight: Math.max(barHeight, 1)
        }
      })
    })

    return { bars, barWidth }
  }
}
</script>

<style scoped>
.bar-chart { width: 100%; height: 140px; }
.bar { rx: 3; }
.bar-value { font-size: 10px; fill: var(--color-text-secondary); font-weight: 600; }
.bar-label { font-size: 10px; fill: var(--color-text-muted); }
</style>
