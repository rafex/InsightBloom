<template lang="pug">
.certificate-editor-page
  DashboardBreadcrumb(:items="[{ label: 'Eventos', to: '/dashboard/conferences' }, { label: 'Certificado' }]")
  .page-heading
    div
      h1 Certificado del evento
      p Elige un diseño base y enriquécelo con los datos disponibles del participante, evento y plataforma.
    FeedbackMessage(v-if="saved" message="Guardado" tone="success")
  .editor-grid(v-if="loaded")
    .panel.catalog-panel
      h2 Diseños base
      .catalog-card(v-for="item in catalog.templates" :key="item.key" :class="{ selected: item.key === form.templateKey }" @click="applyTemplate(item)")
        strong {{ item.name }}
        small {{ item.description }}
      h2 Variables disponibles
      p.hint Haz clic para insertarla en el bloque de texto seleccionado.
      .variables
        button.variable(v-for="variable in catalog.variables" :key="variable.key" type="button" @click="insertVariable(variable.key)")
          code {{ '{' + '{' + variable.key + '}' + '}' }}
          span {{ variable.label }}
      BaseButton(:loading="saving" type="button" @click="save") Guardar certificado
      FeedbackMessage(v-if="error" :message="error" tone="error")
    .panel.workspace
      .workspace-toolbar
        label Nombre
          input(v-model="form.templateName" maxlength="80")
        BaseButton(variant="secondary" size="sm" type="button" @click="addTextBlock") + Texto
        BaseButton(variant="secondary" size="sm" type="button" @click="addShapeBlock") + Borde
      .asset-toolbar
        label.asset-field Logotipo
          input(type="file" accept="image/png,image/jpeg" @change="handleLogoUpload")
        BaseButton(variant="secondary" size="sm" v-if="logoBlock" type="button" @click="removeLogo") Quitar logotipo
        label.asset-field Imagen de fondo
          input(type="file" accept="image/png,image/jpeg" @change="handleBackgroundUpload")
        BaseButton(variant="secondary" size="sm" v-if="document.page.backgroundImage" type="button" @click="removeBackground") Quitar fondo
        small.asset-hint PNG/JPEG, máximo 2 MB por imagen. El fondo cubre toda la hoja y el logotipo se puede mover como un bloque.
      .certificate-preview(ref="previewHost")
        .certificate-page-wrap(:style="{ width: `${1056 * previewScale}px`, height: `${816 * previewScale}px` }")
          .certificate-page(:style="pageStyle()")
            .preview-block(v-for="(block, index) in document.blocks" :key="index" :class="{ active: selectedIndex === index }" :style="blockStyle(block)" @click.stop="selectedIndex = index")
              span(v-if="block.type === 'text'") {{ interpolate(block.text || '') }}
              img(v-else-if="block.type === 'image' && block.src" :src="block.src" alt="")
      .block-editor(v-if="selectedBlock")
        h2 Editar bloque
        .block-fields
          label Texto
            textarea(v-if="selectedBlock.type === 'text'" v-model="selectedBlock.text" rows="2")
            span.muted(v-else) {{ selectedBlock.type === 'shape' ? 'Borde decorativo' : 'Imagen' }}
          label X
            input(type="number" v-model.number="selectedBlock.x")
          label Y
            input(type="number" v-model.number="selectedBlock.y")
          label Ancho
            input(type="number" v-model.number="selectedBlock.width")
          label Alto
            input(type="number" v-model.number="selectedBlock.height")
          label Tamaño de letra
            input(type="number" v-model.number="selectedBlock.style.fontSize" min="8" max="120" :disabled="selectedBlock.type !== 'text'")
          label Color
            input(type="color" v-model="selectedBlock.style.color" :disabled="selectedBlock.type !== 'text'")
          label Alineación
            select(v-model="selectedBlock.style.textAlign" :disabled="selectedBlock.type !== 'text'")
              option(value="left") Izquierda
              option(value="center") Centro
              option(value="right") Derecha
        BaseButton(variant="danger" size="sm" type="button" @click="removeSelected") Eliminar bloque
  LoadingState(v-else message="Cargando editor…")
</template>

<script lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import { useAuthStore } from '@/features/auth/authStore'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import { getCertificateTemplateCatalog, getEventCertificateTemplate, saveEventCertificateTemplate, setCertificateEngine } from '@/services/api/usersApi'
import type { CertificateEngine, CertificateTemplateCatalog, CertificateTemplateCatalogItem } from '@/services/api/types'

type Block = { type: 'text' | 'image' | 'shape'; role?: 'logo'; x: number; y: number; width: number; height: number; text?: string; src?: string; style: Record<string, any> }
type DocumentModel = { page: Record<string, any>; blocks: Block[] }
const MAX_IMAGE_BYTES = 2 * 1024 * 1024

const sampleData: Record<string, string> = {
  'participant.displayName': 'Ana Pérez', 'participant.firstName': 'Ana', 'participant.lastName': 'Pérez',
  'participant.email': 'ana@example.com', 'participant.username': 'ana.perez', 'participant.uuid': 'participante-uuid',
  'event.name': 'Taller de ejemplo', 'event.displayName': 'Taller de ejemplo', 'event.friendlyId': 'taller-ejemplo',
  'event.uuid': 'evento-uuid', 'event.date': '22/07/2026', 'event.startTime': '09:00', 'event.endTime': '13:00',
  'event.venue': 'Auditorio principal', 'event.timezone': 'America/Mexico_City', 'platform.name': 'InsightBloom',
  'platform.website': 'https://insightbloom.v1.rafex.cloud', 'platform.email': 'rafex@rafex.dev',
  'platform.github': 'https://github.com/rafex', 'platform.linkedin': 'LinkedIn', 'platform.telegram': 'Telegram',
  'certificate.issuedDate': '22/07/2026', 'certificate.id': 'certificado-uuid'
}

export default {
  name: 'CertificateEditorPage',
  components: { DashboardBreadcrumb, BaseButton, FeedbackMessage, LoadingState },
  props: { conferenceId: { type: String, required: true } },
  setup(props: { conferenceId: string }) {
    const auth = useAuthStore()
    const loaded = ref(false); const saving = ref(false); const saved = ref(false); const error = ref('')
    const catalog = reactive<CertificateTemplateCatalog>({ templates: [], variables: [] })
    const form = reactive<{ templateKey: string; templateName: string; engine: CertificateEngine }>({
      templateKey: 'classic', templateName: 'Clásico', engine: 'HTML_CHROME'
    })
    const document = reactive<DocumentModel>({ page: {}, blocks: [] })
    const selectedIndex = ref(0)
    const selectedBlock = computed(() => document.blocks[selectedIndex.value] || null)
    const logoBlock = computed(() => document.blocks.find(block => block.type === 'image' && (block as any).role === 'logo') || null)
    const previewHost = ref<HTMLElement | null>(null)
    const previewScale = ref(1)
    let previewObserver: ResizeObserver | null = null

    function updatePreviewScale() {
      const width = previewHost.value?.clientWidth || 1056
      previewScale.value = Math.min(1, Math.max(0.35, (width - 44) / 1056))
    }

    function loadDocument(json: string) {
      const parsed = JSON.parse(json) as DocumentModel
      document.page = parsed.page || {}; document.blocks.splice(0, document.blocks.length, ...(parsed.blocks || []))
      selectedIndex.value = 0
    }
    function applyTemplate(item: CertificateTemplateCatalogItem) {
      form.templateKey = item.key; form.templateName = item.name; form.engine = item.engine
      loadDocument(item.documentJson); saved.value = false
    }
    function interpolate(text: string): string {
      return text.replace(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g, (_m, key) => sampleData[key] || '')
    }
    function blockStyle(block: Block): Record<string, string> {
      const s = block.style || {}
      return { left: `${block.x || 0}px`, top: `${block.y || 0}px`, width: `${block.width || 1}px`, height: `${block.height || 1}px`,
        color: s.color || '#111827', background: s.background || 'transparent', border: s.border || 'none', borderRadius: `${s.borderRadius || 0}px`,
        fontSize: `${s.fontSize || 16}px`, fontWeight: `${s.fontWeight || 400}`, textAlign: s.textAlign || 'left', padding: `${s.padding || 0}px` }
    }
    function pageStyle(): Record<string, string> {
      const style: Record<string, string> = {
        transform: `scale(${previewScale.value})`,
        backgroundColor: document.page.background || '#ffffff'
      }
      if (typeof document.page.backgroundImage === 'string' && document.page.backgroundImage) {
        style.backgroundImage = `url("${document.page.backgroundImage}")`
        style.backgroundSize = 'cover'
        style.backgroundPosition = 'center'
      }
      return style
    }
    function insertVariable(key: string) {
      const block = selectedBlock.value
      if (!block || block.type !== 'text') return
      block.text = `${block.text || ''}{{${key}}}`; saved.value = false
    }
    function addTextBlock() {
      document.blocks.push({ type: 'text', x: 120, y: 500, width: 816, height: 36, text: 'Nuevo texto', style: { fontSize: 20, color: '#111827', textAlign: 'center' } })
      selectedIndex.value = document.blocks.length - 1; saved.value = false
    }
    function addShapeBlock() {
      document.blocks.push({ type: 'shape', x: 18, y: 18, width: 1020, height: 780, style: { border: '2px solid #4f46e5', borderRadius: 18 } })
      selectedIndex.value = document.blocks.length - 1; saved.value = false
    }
    function addLogoBlock(src: string) {
      document.blocks.splice(0, document.blocks.length, ...document.blocks.filter(block => block.role !== 'logo'))
      document.blocks.push({ type: 'image', role: 'logo', x: 72, y: 54, width: 180, height: 90, src, style: { objectFit: 'contain' } })
      selectedIndex.value = document.blocks.length - 1; saved.value = false
    }
    function readImageFile(event: Event): Promise<string> {
      const file = (event.target as HTMLInputElement).files?.[0]
      if (!file) return Promise.reject(new Error('No se seleccionó una imagen'))
      if (!['image/png', 'image/jpeg'].includes(file.type)) return Promise.reject(new Error('Solo se aceptan imágenes PNG o JPEG'))
      if (file.size > MAX_IMAGE_BYTES) return Promise.reject(new Error('La imagen no puede exceder 2 MB'))
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onerror = () => reject(new Error('No se pudo leer la imagen'))
        reader.onload = () => typeof reader.result === 'string' ? resolve(reader.result) : reject(new Error('Imagen inválida'))
        reader.readAsDataURL(file)
      })
    }
    async function handleLogoUpload(event: Event) {
      try { addLogoBlock(await readImageFile(event)); error.value = '' } catch (e: any) { error.value = e.message || 'No se pudo cargar el logotipo' }
      ;(event.target as HTMLInputElement).value = ''
    }
    async function handleBackgroundUpload(event: Event) {
      try { document.page.backgroundImage = await readImageFile(event); saved.value = false; error.value = '' } catch (e: any) { error.value = e.message || 'No se pudo cargar el fondo' }
      ;(event.target as HTMLInputElement).value = ''
    }
    function removeLogo() {
      document.blocks.splice(0, document.blocks.length, ...document.blocks.filter(block => block.role !== 'logo'))
      selectedIndex.value = Math.min(selectedIndex.value, Math.max(0, document.blocks.length - 1)); saved.value = false
    }
    function removeBackground() { delete document.page.backgroundImage; saved.value = false }
    function removeSelected() { if (selectedIndex.value >= 0) document.blocks.splice(selectedIndex.value, 1); selectedIndex.value = Math.max(0, selectedIndex.value - 1); saved.value = false }
    async function save() {
      if (!auth.state.token) return
      saving.value = true; error.value = ''; saved.value = false
      try {
        // El editor es otra entrada válida al flujo. Sincronizar explícitamente el motor antes
        // de guardar el JSON evita que una plantilla HTML quede asociada a un evento INHOUSE.
        await setCertificateEngine(props.conferenceId, form.engine, auth.state.token)
        await saveEventCertificateTemplate(props.conferenceId, { templateKey: form.templateKey, templateName: form.templateName, engine: form.engine, documentJson: JSON.stringify(document) }, auth.state.token)
        saved.value = true
      } catch (e: any) { error.value = e?.response?.data?.error?.message || 'No se pudo guardar la plantilla' }
      finally { saving.value = false }
    }
    onMounted(async () => {
      if (!auth.state.token) return
      try {
        const [items, current] = await Promise.all([getCertificateTemplateCatalog(auth.state.token), getEventCertificateTemplate(props.conferenceId, auth.state.token)])
        catalog.templates = items.templates; catalog.variables = items.variables
        form.templateKey = current.templateKey; form.templateName = current.templateName; form.engine = current.engine; loadDocument(current.documentJson)
      } catch (e: any) { error.value = 'No se pudo cargar el editor' }
      finally {
        loaded.value = true
        await nextTick()
        updatePreviewScale()
        if (previewHost.value && typeof ResizeObserver !== 'undefined') {
          previewObserver = new ResizeObserver(updatePreviewScale)
          previewObserver.observe(previewHost.value)
        }
      }
    })
    watch(loaded, async (value) => { if (value) { await nextTick(); updatePreviewScale() } })
    onBeforeUnmount(() => { previewObserver?.disconnect() })
    return { loaded, saving, saved, error, catalog, form, document, selectedIndex, selectedBlock, logoBlock, previewHost, previewScale, applyTemplate, interpolate, blockStyle, pageStyle, insertVariable, addTextBlock, addShapeBlock, handleLogoUpload, handleBackgroundUpload, removeLogo, removeBackground, removeSelected, save }
  }
}
</script>

<style scoped>
.certificate-editor-page { padding: 28px 24px; max-width: 1400px; margin: 0 auto; }
.page-heading { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; margin:18px 0 24px; }
h1 { margin:0; color:var(--color-heading); } .page-heading p { color:var(--color-text-muted); margin:8px 0 0; }
.editor-grid { display:grid; grid-template-columns:320px minmax(0,1fr); gap:20px; align-items:start; }
.panel { background:var(--color-surface); border:1px solid var(--color-border-subtle); border-radius:14px; padding:18px; box-shadow:0 4px 18px rgba(30,27,75,.06); }
h2 { font-size:1rem; color:var(--color-heading); margin:0 0 12px; } .catalog-card { padding:12px; border:1px solid var(--color-border-subtle); border-radius:10px; margin-bottom:8px; cursor:pointer; } .catalog-card.selected { border-color:var(--color-primary); background:var(--color-primary-soft); } .catalog-card strong,.catalog-card small { display:block; } .catalog-card small,.hint,.muted { color:var(--color-text-muted); font-size:.82rem; margin-top:4px; }
.variables { max-height:340px; overflow:auto; margin-bottom:16px; } .variable { display:flex; flex-direction:column; align-items:flex-start; width:100%; border:0; border-bottom:1px solid var(--color-surface-muted); background:transparent; padding:7px 2px; cursor:pointer; text-align:left; } .variable:hover { background:var(--color-surface-muted); } code { color:var(--color-primary); font-size:.74rem; } .variable span { color:var(--color-text-muted); font-size:.75rem; }
.workspace-toolbar { display:flex; gap:12px; align-items:center; flex-wrap:wrap; margin-bottom:16px; } .workspace-toolbar label { color:var(--color-text-muted); font-size:.82rem; flex:1; } input,textarea,select { display:block; width:100%; box-sizing:border-box; border:1px solid var(--color-border); border-radius:7px; padding:8px; margin-top:4px; background:var(--color-surface); } .workspace-toolbar input { max-width:300px; }
.asset-toolbar { display:grid; grid-template-columns:minmax(150px, 1fr) auto minmax(150px, 1fr) auto; gap:10px; align-items:end; margin:-4px 0 16px; padding:12px; border:1px solid var(--color-border-subtle); border-radius:10px; background:var(--color-surface-muted); }
.asset-field { color:var(--color-text-muted); font-size:.78rem; } .asset-field input { font-size:.75rem; padding:6px; } .asset-hint { grid-column:1 / -1; color:var(--color-text-muted); font-size:.75rem; }
.certificate-preview { background:var(--color-border-subtle); padding:22px; overflow:auto; border-radius:10px; } .certificate-page-wrap { position:relative; margin:0 auto; } .certificate-page { position:absolute; left:0; top:0; width:1056px; height:816px; background:var(--color-surface); transform-origin:top left; } .preview-block { position:absolute; box-sizing:border-box; overflow:hidden; white-space:pre-wrap; cursor:pointer; } .preview-block.active { outline:2px solid var(--color-primary); outline-offset:2px; } .preview-block img { width:100%; height:100%; object-fit:contain; }
.block-editor { border-top:1px solid var(--color-border-subtle); margin-top:18px; padding-top:18px; } .block-fields { display:grid; grid-template-columns:repeat(4,1fr); gap:10px; } .block-fields label { color:var(--color-text-muted); font-size:.78rem; }
@media (max-width: 850px) { .editor-grid { grid-template-columns:1fr; } .catalog-panel { order:2; } .workspace { order:1; } .block-fields { grid-template-columns:repeat(2,1fr); } .asset-toolbar { grid-template-columns:1fr auto; } .asset-hint { grid-column:1 / -1; } }
</style>
