<template lang="pug">
.conference-map-wrap
  svg.conference-map(ref="svgRef" :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="xMidYMid meet")
    g(ref="gRef")
  .map-controls
    button.zoom-btn(@click="zoomIn" title="Acercar") +
    button.zoom-btn(@click="zoomOut" title="Alejar") −
    button.zoom-btn(@click="resetZoom" title="Restablecer") ⌂
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import * as d3 from 'd3'
import * as topojson from 'topojson-client'
import worldData from 'world-atlas/countries-110m.json'

export default {
  name: 'ConferenceMap',
  props: {
    latitude: { type: Number, default: null },
    longitude: { type: Number, default: null },
    label: { type: String, default: '' }
  },
  setup(props) {
    const W = 960
    const H = 500
    const svgRef = ref(null)
    const gRef = ref(null)

    let zoom = null
    let svg = null
    let g = null
    let projection = null

    function buildMap() {
      svg = d3.select(svgRef.value)
      g = d3.select(gRef.value)

      projection = d3.geoNaturalEarth1()
        .scale(153)
        .translate([W / 2, H / 2])

      const path = d3.geoPath().projection(projection)
      const countries = topojson.feature(worldData, worldData.objects.countries)
      const borders = topojson.mesh(worldData, worldData.objects.countries, (a, b) => a !== b)

      g.selectAll('.country')
        .data(countries.features)
        .join('path')
        .attr('class', 'country')
        .attr('d', path)

      g.append('path')
        .datum(borders)
        .attr('class', 'borders')
        .attr('d', path)

      // Graticule
      const graticule = d3.geoGraticule()
      g.insert('path', '.country')
        .datum(graticule())
        .attr('class', 'graticule')
        .attr('d', path)

      // Sphere outline
      g.insert('path', '.graticule')
        .datum({ type: 'Sphere' })
        .attr('class', 'sphere')
        .attr('d', path)

      zoom = d3.zoom()
        .scaleExtent([1, 12])
        .translateExtent([[0, 0], [W, H]])
        .on('zoom', (event) => {
          g.attr('transform', event.transform)
        })

      svg.call(zoom)

      placeMarker()
    }

    function placeMarker() {
      g.selectAll('.conf-marker').remove()
      g.selectAll('.conf-label').remove()

      if (props.latitude == null || props.longitude == null) return

      const [x, y] = projection([props.longitude, props.latitude])
      if (isNaN(x) || isNaN(y)) return

      const marker = g.append('g')
        .attr('class', 'conf-marker')
        .attr('transform', `translate(${x},${y})`)

      // Outer pulsing ring
      marker.append('circle')
        .attr('class', 'pulse-ring')
        .attr('r', 14)

      // Inner dot
      marker.append('circle')
        .attr('class', 'marker-dot')
        .attr('r', 6)

      // Pin stem
      marker.append('line')
        .attr('class', 'marker-stem')
        .attr('x1', 0).attr('y1', 6)
        .attr('x2', 0).attr('y2', 16)

      // Label
      if (props.label) {
        marker.append('text')
          .attr('class', 'conf-label')
          .attr('x', 0).attr('y', -18)
          .attr('text-anchor', 'middle')
          .text(props.label)
      }

      zoomToMarker()
    }

    function zoomToMarker() {
      if (props.latitude == null || props.longitude == null || !zoom) return
      const [x, y] = projection([props.longitude, props.latitude])
      if (isNaN(x) || isNaN(y)) return

      const scale = 5
      svg.transition().duration(1000).call(
        zoom.transform,
        d3.zoomIdentity
          .translate(W / 2, H / 2)
          .scale(scale)
          .translate(-x, -y)
      )
    }

    function zoomIn() {
      svg.transition().duration(300).call(zoom.scaleBy, 1.5)
    }

    function zoomOut() {
      svg.transition().duration(300).call(zoom.scaleBy, 1 / 1.5)
    }

    function resetZoom() {
      svg.transition().duration(500).call(zoom.transform, d3.zoomIdentity)
      setTimeout(zoomToMarker, 520)
    }

    onMounted(buildMap)
    watch(() => [props.latitude, props.longitude, props.label], placeMarker)

    return { svgRef, gRef, W, H, zoomIn, zoomOut, resetZoom }
  }
}
</script>

<style scoped>
.conference-map-wrap {
  position: relative;
  width: 100%;
  border-radius: 12px;
  overflow: hidden;
  background: #1e3a5f;
  box-shadow: 0 4px 24px rgba(0,0,0,0.18);
}
.conference-map {
  display: block;
  width: 100%;
  height: auto;
  cursor: grab;
}
.conference-map:active { cursor: grabbing; }

:deep(.sphere) {
  fill: #1a3a5c;
}
:deep(.graticule) {
  fill: none;
  stroke: #2a4a70;
  stroke-width: 0.4;
}
:deep(.country) {
  fill: #2d6a4f;
  stroke: #1b4332;
  stroke-width: 0.3;
}
:deep(.borders) {
  fill: none;
  stroke: #1b4332;
  stroke-width: 0.5;
}
:deep(.conf-marker .pulse-ring) {
  fill: rgba(251, 191, 36, 0.25);
  stroke: #fbbf24;
  stroke-width: 1.5;
  animation: pulse 2s ease-out infinite;
}
:deep(.conf-marker .marker-dot) {
  fill: #fbbf24;
  stroke: #fff;
  stroke-width: 1.5;
}
:deep(.conf-marker .marker-stem) {
  stroke: #fbbf24;
  stroke-width: 2;
}
:deep(.conf-label) {
  fill: #fff;
  font-size: 11px;
  font-weight: 700;
  font-family: system-ui, sans-serif;
  text-shadow: 0 1px 3px rgba(0,0,0,0.8);
  pointer-events: none;
}

@keyframes pulse {
  0%   { r: 8; opacity: 0.9; }
  100% { r: 22; opacity: 0; }
}

.map-controls {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.zoom-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  background: rgba(255,255,255,0.15);
  color: #fff;
  font-size: 1.1rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
  transition: background 0.15s;
}
.zoom-btn:hover { background: rgba(255,255,255,0.3); }
</style>
