<template lang="pug">
.wfe-overlay(@click.self="close")
  .wfe-dialog
    .wfe-header
      h3 Archivos del workspace
      button.wfe-close(type="button" @click="close" title="Cerrar" aria-label="Cerrar editor de workspace") ✕
    .wfe-body
      .wfe-sidebar
        p.wfe-hint(v-if="loadingFiles") Cargando árbol de archivos...
        p.wfe-error(v-if="filesError") {{ filesError }}
        ul.wfe-file-list(v-if="!loadingFiles && !filesError")
          li(v-if="!files.length") Sin archivos.
          li.wfe-file-entry(
            v-for="entry in sortedFiles"
            :key="entry.path"
            :class="{ active: entry.path === selectedPath, dir: entry.isDirectory }"
            @click="!entry.isDirectory && openFile(entry.path)"
          )
            span {{ entry.isDirectory ? '📁' : '📄' }} {{ entry.path }}
      .wfe-main
        template(v-if="!selectedPath")
          p.wfe-hint Elegí un archivo de la izquierda para verlo/editarlo.
        template(v-else)
          .wfe-toolbar
            span.wfe-filename {{ selectedPath }}
            span.wfe-loading(v-if="loadingFile") Cargando...
            SaveState.wfe-save-state(:state="saveState")
            BaseButton(type="button" @click="save" :disabled="savingFile || loadingFile || saveState === 'clean' || saveState === 'saved'" :loading="savingFile") Guardar
          p.wfe-conflict(v-if="conflict")
            | Este archivo cambió desde que se abrió (¿el alumno lo está editando?).
            BaseButton(variant="secondary" type="button" @click="forceSave") Guardar de todas formas
          p.wfe-error(v-if="saveError") {{ saveError }}
          .wfe-editor(ref="editorContainer")
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { listWorkspaceFiles, readWorkspaceFile, writeWorkspaceFile } from '@/services/api/usersApi'
import type { WorkspaceFileEntry } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import SaveState from '@/components/ui/SaveState.vue'

// Extension -> id de lenguaje de Monaco. Autocompletado basico (resaltado + sugerencias por
// indentacion/llaves) viene incluido gratis en las "basic-languages" de monaco-editor para
// estos tres -- sin Language Server, a proposito ("visor super ligero", no pretende igualar
// la experiencia LSP completa de code-server/redhat.java).
const LANGUAGE_BY_EXTENSION: Record<string, string> = {
  java: 'java', py: 'python', js: 'javascript', jsx: 'javascript',
  ts: 'typescript', tsx: 'typescript', json: 'json', md: 'markdown',
  html: 'html', css: 'css', sh: 'shell', yml: 'yaml', yaml: 'yaml'
}

function languageForPath(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase() || ''
  return LANGUAGE_BY_EXTENSION[ext] || 'plaintext'
}

export default {
  name: 'WorkspaceFileEditor',
  components: { BaseButton, SaveState },
  props: {
    conferenceId: { type: String, required: true },
    userUuid: { type: String, required: true }
  },
  emits: ['close'],
  setup(props, { emit }) {
    const auth = useAuthStore()
    const files = ref<WorkspaceFileEntry[]>([])
    const loadingFiles = ref(true)
    const filesError = ref('')
    const selectedPath = ref<string | null>(null)
    const fileMtime = ref<number | null>(null)
    const loadingFile = ref(false)
    const savingFile = ref(false)
    const saveError = ref('')
    const conflict = ref(false)
    const editorContainer = ref<HTMLElement | null>(null)
    const initialContent = ref('')
    const fileSaved = ref(false)
    const editorRevision = ref(0)

    const sortedFiles = computed(() =>
      [...files.value].sort((a, b) => a.path.localeCompare(b.path)))

    // Carga perezosa a proposito: monaco-editor es pesado (varios cientos de KB), no debe
    // entrar en el bundle principal -- Vite lo separa en su propio chunk automaticamente al
    // verlo como un import() dinamico dentro de un handler (mismo mecanismo que ya usa el
    // router para todas las rutas), nunca al nivel de modulo/mount del componente.
    let monacoModule: typeof import('monaco-editor') | null = null
    let editorInstance: import('monaco-editor').editor.IStandaloneCodeEditor | null = null
    let editorChangeListener: { dispose: () => void } | null = null

    async function ensureMonacoLoaded() {
      if (!monacoModule) {
        monacoModule = await import('monaco-editor')
      }
      return monacoModule
    }

    async function loadFiles() {
      loadingFiles.value = true; filesError.value = ''
      try {
        files.value = await listWorkspaceFiles(props.conferenceId, props.userUuid, '', auth.state.token as string)
      } catch (e: any) {
        filesError.value = e.response?.data?.error?.message || 'No se pudo cargar el árbol de archivos'
      } finally {
        loadingFiles.value = false
      }
    }

    async function openFile(path: string) {
      loadingFile.value = true; saveError.value = ''; conflict.value = false
      fileSaved.value = false; initialContent.value = ''; editorRevision.value += 1
      try {
        const result = await readWorkspaceFile(props.conferenceId, props.userUuid, path, auth.state.token as string)
        selectedPath.value = path
        fileMtime.value = result.mtime
        await nextTick()
        const monaco = await ensureMonacoLoaded()
        if (!editorContainer.value) return
        if (!editorInstance) {
          editorInstance = monaco.editor.create(editorContainer.value, {
            value: result.content,
            language: languageForPath(path),
            automaticLayout: true,
            minimap: { enabled: false }
          })
          editorChangeListener = editorInstance.onDidChangeModelContent(() => {
            editorRevision.value += 1
            fileSaved.value = false
          })
        } else {
          const model = editorInstance.getModel()
          if (model) {
            monaco.editor.setModelLanguage(model, languageForPath(path))
            model.setValue(result.content)
          }
        }
        initialContent.value = result.content
      } catch (e: any) {
        saveError.value = e.response?.data?.error?.message || 'No se pudo abrir el archivo'
      } finally {
        loadingFile.value = false
      }
    }

    async function doSave(force: boolean) {
      if (!editorInstance || !selectedPath.value) return
      savingFile.value = true; saveError.value = ''
      try {
        const content = editorInstance.getValue()
        const result = await writeWorkspaceFile(
          props.conferenceId, props.userUuid, selectedPath.value, content,
          force ? null : fileMtime.value, force, auth.state.token as string
        )
        fileMtime.value = result.mtime
        initialContent.value = content
        fileSaved.value = true
        editorRevision.value += 1
        conflict.value = false
      } catch (e: any) {
        if (e.response?.status === 409) {
          conflict.value = true
        } else {
          saveError.value = e.response?.data?.error?.message || 'No se pudo guardar el archivo'
        }
      } finally {
        savingFile.value = false
      }
    }

    function save() { doSave(false) }
    function forceSave() { doSave(true) }

    const saveState = computed(() => {
      void editorRevision.value
      if (!selectedPath.value || loadingFile.value) return 'clean'
      if (savingFile.value) return 'saving'
      if (!editorInstance || editorInstance.getValue() !== initialContent.value) return 'dirty'
      return fileSaved.value ? 'saved' : 'clean'
    })

    function close() {
      emit('close')
    }

    onMounted(() => {
      loadFiles()
    })

    onBeforeUnmount(() => {
      editorChangeListener?.dispose()
      editorInstance?.dispose()
      editorInstance = null
    })

    return {
      files, loadingFiles, filesError, sortedFiles, selectedPath, loadingFile, savingFile,
      saveError, conflict, editorContainer, saveState, openFile, save, forceSave, close
    }
  }
}
</script>

<style scoped>
.wfe-overlay {
  position: fixed; inset: 0; background: var(--color-overlay);
  display: flex; align-items: center; justify-content: center; z-index: 200;
}

.wfe-dialog {
  background: var(--color-surface); border-radius: 12px; width: min(1000px, 92vw); height: min(700px, 88vh);
  display: flex; flex-direction: column; box-shadow: var(--shadow-overlay);
  overflow: hidden;
}

.wfe-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-bottom: 1px solid var(--color-border-subtle);
}

.wfe-header h3 { margin: 0; color: var(--color-heading); font-size: 1.05rem; }

.wfe-close {
  border: none; background: transparent; font-size: 1.1rem; cursor: pointer; color: var(--color-text-muted);
}

.wfe-body { flex: 1; display: flex; min-height: 0; }

.wfe-sidebar {
  width: 260px; border-right: 1px solid var(--color-border-subtle); overflow-y: auto; padding: 12px;
}

.wfe-file-list { list-style: none; margin: 0; padding: 0; }

.wfe-file-entry {
  padding: 6px 8px; border-radius: 6px; cursor: pointer; font-size: 0.85rem;
  word-break: break-all; color: var(--color-text-secondary);
}

.wfe-file-entry:hover { background: var(--color-surface-muted); }
.wfe-file-entry.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 600; }
.wfe-file-entry.dir { cursor: default; color: var(--color-text-muted); }

.wfe-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.wfe-toolbar {
  display: flex; align-items: center; gap: 12px; padding: 10px 16px; border-bottom: 1px solid var(--color-border-subtle);
}

.wfe-filename { font-family: var(--font-family-mono); font-size: 0.85rem; color: var(--color-text-secondary); flex: 1; }
.wfe-loading { font-size: 0.8rem; color: var(--color-text-muted); }
.wfe-save-state { margin: 0; }

.wfe-editor { flex: 1; min-height: 0; }

.wfe-hint { padding: 16px; color: var(--color-text-muted); font-size: 0.9rem; }
.wfe-error { padding: 8px 16px; color: var(--color-danger-dark); font-size: 0.85rem; }

.wfe-conflict {
  margin: 0; padding: 10px 16px; background: var(--color-warning-soft); color: var(--color-warning); font-size: 0.85rem;
  display: flex; align-items: center; gap: 12px;
}
</style>
