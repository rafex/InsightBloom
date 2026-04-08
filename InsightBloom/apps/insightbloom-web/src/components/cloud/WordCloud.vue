<template lang="pug">
.word-cloud-container
  svg(ref="svgRef" :width="width" :height="height")
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import * as d3 from 'd3'
import cloud from 'd3-cloud'

// Paleta de 10 colores vivos para la nube
const PALETTE = [
  '#4f46e5', // indigo
  '#0ea5e9', // sky
  '#10b981', // emerald
  '#f59e0b', // amber
  '#ef4444', // red
  '#8b5cf6', // violet
  '#06b6d4', // cyan
  '#f97316', // orange
  '#84cc16', // lime
  '#ec4899', // pink
]

export default {
  name: 'WordCloud',
  props: {
    words: { type: Array, default: () => [] },
    width: { type: Number, default: 800 },
    height: { type: Number, default: 500 },
  },
  emits: ['word-click'],
  setup(props, { emit }) {
    const svgRef = ref(null)
    let layoutInstance = null

    function render() {
      if (!svgRef.value || !props.words.length) return

      // Ordenar: score desc → count desc → alphabetical
      const sorted = [...props.words]
        .sort((a, b) => {
          const sd = (b.relevanceScore || 0) - (a.relevanceScore || 0)
          if (sd !== 0) return sd
          const cd = (b.messageCount || 0) - (a.messageCount || 0)
          if (cd !== 0) return cd
          return (a.wordCanonical || '').localeCompare(b.wordCanonical || '')
        })
        .slice(0, 60)

      const maxScore = d3.max(sorted, d => d.relevanceScore || 0) || 0
      const maxCount = d3.max(sorted, d => d.messageCount || 0) || 0
      const n = sorted.length

      // Escala de tamaño de fuente
      const fontScale = d3.scaleLinear().range([14, 64]).clamp(true)
      if (maxScore > 0)      fontScale.domain([0, maxScore])
      else if (maxCount > 0) fontScale.domain([0, maxCount])
      else                   fontScale.domain([n - 1, 0])

      // Asignar tamaño y color por rank para que d3-cloud los use
      const entries = sorted.map((word, i) => {
        let size
        if (maxScore > 0)      size = fontScale(word.relevanceScore || 0)
        else if (maxCount > 0) size = fontScale(word.messageCount || 0)
        else                   size = fontScale(i)

        return {
          text: word.wordCanonical || word.wordNormalized || '',
          size,
          color: PALETTE[i % PALETTE.length],
          weight: size > 30 ? '700' : size > 20 ? '500' : '400',
          _word: word,
        }
      })

      // Cancelar layout previo si existe
      if (layoutInstance) layoutInstance.stop()

      layoutInstance = cloud()
        .size([props.width, props.height])
        .words(entries)
        .padding(6)
        .rotate(() => (Math.random() < 0.15 ? 90 : 0))  // 85% horizontal, 15% vertical
        .font('system-ui, sans-serif')
        .fontWeight(d => d.weight)
        .fontSize(d => d.size)
        .on('end', draw)
        .start()
    }

    function draw(words) {
      if (!svgRef.value) return
      const svg = d3.select(svgRef.value)
      svg.selectAll('*').remove()

      const g = svg.append('g')
        .attr('transform', `translate(${props.width / 2},${props.height / 2})`)

      g.selectAll('text')
        .data(words)
        .enter()
        .append('text')
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('font-size', d => `${d.size}px`)
          .attr('font-family', 'system-ui, sans-serif')
          .attr('font-weight', d => d.weight)
          .attr('fill', d => d.color)
          .attr('cursor', 'pointer')
          .attr('transform', d => `translate(${d.x},${d.y}) rotate(${d.rotate})`)
          .style('user-select', 'none')
          .text(d => d.text)
          .on('click', (event, d) => emit('word-click', d._word))
          .on('mouseover', function() { d3.select(this).attr('opacity', 0.65) })
          .on('mouseout',  function() { d3.select(this).attr('opacity', 1) })
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
