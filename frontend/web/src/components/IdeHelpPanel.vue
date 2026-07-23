<template lang="pug">
.ide-help
  button.help-fab(type="button" @click="open = !open" :aria-expanded="open" title="Ayuda de Neovim")
    span(v-if="!open") 📖
    span(v-else) ✕

  transition(name="slide")
    aside.help-panel(v-if="open")
      nav.help-nav
        h3 Ayuda
        button.help-nav-item(
          v-for="topic in topics"
          :key="topic.id"
          type="button"
          :class="{ active: topic.id === activeId }"
          @click="activeId = topic.id"
        ) {{ topic.title }}
      .help-content(v-html="renderedActive")
</template>

<script lang="ts">
import { ref, computed } from 'vue'
import { marked } from 'marked'
import neovimBasico from '@/assets/ide-help/00-neovim-basico.md?raw'
import helloJava from '@/assets/ide-help/10-hello-world-java.md?raw'
import helloPython from '@/assets/ide-help/11-hello-world-python.md?raw'
import helloJs from '@/assets/ide-help/12-hello-world-javascript-typescript.md?raw'
import helloBash from '@/assets/ide-help/13-hello-world-bash.md?raw'
import publishWebPage from '@/assets/ide-help/14-publicar-pagina-web.md?raw'

// Orden fijo a proposito (no alfabetico): lo basico de Neovim primero, despues un "hello world"
// por lenguaje en el mismo orden en que la imagen del sandbox los instala (ver
// Dockerfile.code-ide-neovim) -- alguien que nunca uso Neovim necesita el modo de uso ANTES que
// un ejemplo de codigo especifico.
const TOPICS = [
  { id: 'neovim', title: '⌨️ Neovim básico', markdown: neovimBasico },
  { id: 'java', title: '☕ Hello World: Java', markdown: helloJava },
  { id: 'python', title: '🐍 Hello World: Python', markdown: helloPython },
  { id: 'js', title: '📜 Hello World: JS/TS', markdown: helloJs },
  { id: 'bash', title: '💲 Hello World: Bash', markdown: helloBash },
  { id: 'publish-web', title: '🌐 Publicar página web', markdown: publishWebPage }
]

export default {
  name: 'IdeHelpPanel',
  setup() {
    const open = ref(false)
    const activeId = ref(TOPICS[0].id)

    const renderedActive = computed(() => {
      const topic = TOPICS.find(t => t.id === activeId.value)
      return topic ? marked.parse(topic.markdown, { async: false }) : ''
    })

    return { open, activeId, topics: TOPICS, renderedActive }
  }
}
</script>

<style scoped>
.help-fab {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 2000;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: #4f46e5;
  color: white;
  font-size: 1.4rem;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, background 0.15s ease;
}

.help-fab:hover {
  background: #4338ca;
  transform: scale(1.06);
}

.help-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: min(480px, 100vw);
  z-index: 1900;
  background: #ffffff;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.help-nav {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid #e5e7eb;
}

.help-nav h3 {
  width: 100%;
  margin: 0 0 8px;
  font-size: 0.95rem;
  color: #1e1b4b;
}

.help-nav-item {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1.5px solid #e5e7eb;
  background: white;
  color: #374151;
  font-size: 0.82rem;
  cursor: pointer;
  white-space: nowrap;
}

.help-nav-item:hover {
  border-color: #a5b4fc;
  background: #eef2ff;
}

.help-nav-item.active {
  border-color: #4f46e5;
  background: #4f46e5;
  color: white;
}

.help-content {
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 20px 24px 40px;
  font-size: 0.92rem;
  line-height: 1.6;
  color: #1f2937;
}

.help-content :deep(h1) {
  font-size: 1.3rem;
  margin: 0 0 16px;
  color: #1e1b4b;
}

.help-content :deep(h2) {
  font-size: 1.05rem;
  margin: 28px 0 10px;
  color: #312e81;
}

.help-content :deep(p) {
  margin: 10px 0;
}

.help-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0 20px;
  font-size: 0.85rem;
}

.help-content :deep(th),
.help-content :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 6px 10px;
  text-align: left;
}

.help-content :deep(th) {
  background: #f9fafb;
}

.help-content :deep(code) {
  background: #f3f4f6;
  padding: 1px 5px;
  border-radius: 4px;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 0.85em;
}

.help-content :deep(pre) {
  background: #1e1b4b;
  color: #e0e7ff;
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.help-content :deep(pre code) {
  background: none;
  padding: 0;
  color: inherit;
}

.slide-enter-active,
.slide-leave-active {
  transition: transform 0.2s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateX(100%);
}

@media (max-width: 600px) {
  .help-panel {
    width: 100vw;
  }
}
</style>
