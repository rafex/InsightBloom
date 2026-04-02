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

      const maxScore = d3.max(props.words, d => d.relevanceScore) || 1
      const minScore = d3.min(props.words, d => d.relevanceScore) || 0
      const fontScale = d3.scaleLinear()
        .domain([minScore, maxScore])
        .range([14, 64])
        .clamp(true)

      const colorScale = d3.scaleSequential()
        .domain([minScore, maxScore])
        .interpolator(d3.interpolateBlues)

      const cx = props.width / 2
      const cy = props.height / 2
      const g = svg.append('g').attr('transform', `translate(${cx},${cy})`)

      // Simple spiral placement
      props.words.slice(0, 60).forEach((word, i) => {
        const fontSize = fontScale(word.relevanceScore)
        const angle = i * 137.5 * (Math.PI / 180) // golden angle
        const r = Math.sqrt(i) * 35
        const x = r * Math.cos(angle)
        const y = r * Math.sin(angle)

        g.append('text')
          .attr('x', x)
          .attr('y', y)
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('font-size', `${fontSize}px`)
          .attr('font-family', 'system-ui, sans-serif')
          .attr('font-weight', fontSize > 30 ? 'bold' : 'normal')
          .attr('fill', colorScale(word.relevanceScore))
          .attr('cursor', 'pointer')
          .attr('class', 'animate__animated animate__fadeIn')
          .style('user-select', 'none')
          .text(word.wordCanonical || word.wordNormalized)
          .on('click', () => emit('word-click', word))
          .on('mouseover', function() {
            d3.select(this).attr('opacity', 0.7)
          })
          .on('mouseout', function() {
            d3.select(this).attr('opacity', 1)
          })
      })
    }

    onMounted(render)
    watch(() => props.words, render, { deep: true })

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
svg text {
  transition: opacity 0.2s ease;
}
</style>
