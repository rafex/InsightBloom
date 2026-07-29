<template lang="pug">
.word-cloud-container
  svg(ref="svgRef" :width="width" :height="height")
</template>

<script lang="ts">
import { ref, onMounted, watch, type PropType } from 'vue'
import * as d3 from 'd3'
import cloud from 'd3-cloud'

interface CloudWord {
  relevanceScore?: number
  messageCount?: number
  wordCanonical?: string
  wordNormalized?: string
  [key: string]: unknown
}

interface LayoutEntry {
  text: string
  size: number
  color: string
  weight: string
  _word: CloudWord
  x?: number
  y?: number
  rotate?: number
}

// Paleta de 10 colores vivos para la nube
const PALETTE = [
  'var(--color-cloud-indigo)',
  'var(--color-cloud-sky)',
  'var(--color-cloud-emerald)',
  'var(--color-cloud-amber)',
  'var(--color-cloud-red)',
  'var(--color-cloud-violet)',
  'var(--color-cloud-cyan)',
  'var(--color-cloud-orange)',
  'var(--color-cloud-lime)',
  'var(--color-cloud-pink)',
]

export default {
  name: 'WordCloud',
  props: {
    words: { type: Array as PropType<CloudWord[]>, default: () => [] },
    width: { type: Number, default: 800 },
    height: { type: Number, default: 500 },
  },
  emits: ['word-click'],
  setup(props: { words: CloudWord[], width: number, height: number }, { emit }: { emit: (event: 'word-click', word: CloudWord) => void }) {
    const svgRef = ref<SVGSVGElement | null>(null)
    let layoutInstance: ReturnType<typeof cloud> | null = null

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

      const maxScore = d3.max(sorted, (d: CloudWord) => d.relevanceScore || 0) || 0
      const maxCount = d3.max(sorted, (d: CloudWord) => d.messageCount || 0) || 0
      const n = sorted.length

      // Escala de tamaño de fuente
      const fontScale = d3.scaleLinear().range([14, 64]).clamp(true)
      if (maxScore > 0)      fontScale.domain([0, maxScore])
      else if (maxCount > 0) fontScale.domain([0, maxCount])
      else                   fontScale.domain([n - 1, 0])

      // Asignar tamaño y color por rank para que d3-cloud los use
      const entries: LayoutEntry[] = sorted.map((word, i) => {
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
        .words(entries as any)
        .padding(6)
        .rotate(() => (Math.random() < 0.15 ? 90 : 0))  // 85% horizontal, 15% vertical
        .font('system-ui, sans-serif')
        .fontWeight((d: any) => d.weight)
        .fontSize((d: any) => d.size)
        .on('end', draw as any)
        .start()
    }

    function draw(words: LayoutEntry[]) {
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
          .attr('font-size', (d: any) => `${d.size}px`)
          .attr('font-family', 'system-ui, sans-serif')
          .attr('font-weight', (d: any) => d.weight)
          .attr('fill', (d: any) => d.color)
          .attr('cursor', 'pointer')
          .attr('transform', (d: any) => `translate(${d.x},${d.y}) rotate(${d.rotate})`)
          .style('user-select', 'none')
          .text((d: any) => d.text)
          .on('click', (event: any, d: any) => emit('word-click', d._word))
          .on('mouseover', function (this: SVGTextElement) { d3.select(this).attr('opacity', 0.65) })
          .on('mouseout',  function (this: SVGTextElement) { d3.select(this).attr('opacity', 1) })
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
