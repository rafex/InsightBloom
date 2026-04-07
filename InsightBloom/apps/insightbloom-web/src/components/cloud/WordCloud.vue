<template lang="pug">
.word-cloud-container
  svg(ref="svgRef" :width="width" :height="height")
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import * as d3 from 'd3'

export default {
  name: 'WordCloud',
  props: {
    words: { type: Array, default: () => [] },
    width: { type: Number, default: 800 },
    height: { type: Number, default: 500 },
    color: { type: String, default: '#4f46e5' }
  },
  emits: ['word-click'],
  setup(props, { emit }) {
    const svgRef = ref(null)

    function render() {
      if (!svgRef.value || !props.words.length) return

      const svg = d3.select(svgRef.value)
      svg.selectAll('*').remove()

      // Ordenar por relevanceScore; si todos son 0, usar messageCount como fallback
      const sorted = [...props.words].sort((a, b) => {
        const scoreDiff = (b.relevanceScore || 0) - (a.relevanceScore || 0)
        if (scoreDiff !== 0) return scoreDiff
        return (b.messageCount || 0) - (a.messageCount || 0)
      })

      const maxScore = d3.max(sorted, d => d.relevanceScore || 0) || 0
      const maxCount = d3.max(sorted, d => d.messageCount || 0) || 0

      // Usar rank (posición inversa) cuando no hay scores reales
      const useRank = maxScore === 0 && maxCount === 0
      const n = sorted.length

      // Escala de tamaño: por score, por count, o por rank
      const fontScale = d3.scaleLinear().range([13, 60]).clamp(true)
      if (maxScore > 0) {
        fontScale.domain([0, maxScore])
      } else if (maxCount > 0) {
        fontScale.domain([0, maxCount])
      } else {
        // todos iguales: rango por posición (primero = más grande)
        fontScale.domain([n - 1, 0])
      }

      // Colores: indigo fijo con opacidad por rank cuando no hay datos
      const colorScale = maxScore > 0
        ? d3.scaleSequential().domain([0, maxScore]).interpolator(d3.interpolateBlues)
        : null

      const cx = props.width / 2
      const cy = props.height / 2
      const g = svg.append('g').attr('transform', `translate(${cx},${cy})`)

      sorted.slice(0, 60).forEach((word, i) => {
        let fontSize
        if (maxScore > 0) {
          fontSize = fontScale(word.relevanceScore || 0)
        } else if (maxCount > 0) {
          fontSize = fontScale(word.messageCount || 0)
        } else {
          fontSize = fontScale(i) // rank inverso: i=0 es el primero → más grande
        }

        const angle = i * 137.5 * (Math.PI / 180)
        const r = Math.sqrt(i) * 32
        const x = r * Math.cos(angle)
        const y = r * Math.sin(angle)

        // Color: escala de azules si hay scores; si no, indigo con opacidad decreciente
        let fill
        if (colorScale && maxScore > 0) {
          const c = d3.color(colorScale(word.relevanceScore || 0))
          // interpolateBlues puede dar colores muy claros; oscurecer el mínimo
          fill = d3.interpolateBlues(0.3 + 0.7 * ((word.relevanceScore || 0) / maxScore))
        } else {
          // Indigo con opacidad 1.0 → 0.35 según rank
          const opacity = Math.max(0.35, 1 - (i / Math.max(n, 1)) * 0.65)
          fill = `rgba(79,70,229,${opacity.toFixed(2)})`
        }

        g.append('text')
          .attr('x', x)
          .attr('y', y)
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('font-size', `${fontSize}px`)
          .attr('font-family', 'system-ui, sans-serif')
          .attr('font-weight', fontSize > 28 ? '700' : fontSize > 18 ? '500' : '400')
          .attr('fill', fill)
          .attr('cursor', 'pointer')
          .style('user-select', 'none')
          .text(word.wordCanonical || word.wordNormalized)
          .on('click', () => emit('word-click', word))
          .on('mouseover', function() { d3.select(this).attr('opacity', 0.65) })
          .on('mouseout',  function() { d3.select(this).attr('opacity', 1) })
      })
    }

    onMounted(render)
    watch(() => props.words, render, { deep: true })
    watch(() => [props.width, props.height], render)

    return { svgRef }
  }
}
</script>

<style scoped>
.word-cloud-container {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  overflow: hidden;
}
svg text { transition: opacity 0.15s ease; }
</style>
