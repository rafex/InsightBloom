<template lang="pug">
.image-policy-page
  h2 Imágenes de contenedor permitidas
  p.field-hint Imágenes base que un evento puede usar en el {{ ' ' }}
    code FROM
    |  de un Containerfile/Dockerfile al publicar un contenedor desde el IDE. Esta lista es
    |  GLOBAL: se suma a la de cada evento (nunca la reemplaza) y la lista negra siempre gana,
    |  tanto la global como la del evento. Independiente de esta lista, solo se permiten imágenes
    |  de #[code docker.io] o #[code ghcr.io] — cualquier otro registro se rechaza siempre.

  LoadingState(v-if="loading" message="Cargando política de imágenes…")
  .settings-card(v-else)
    SaveState(:state="saveState")
    FormField(label="Lista blanca (permitidas)" hint="Un prefijo de nombre de imagen por línea o separados por coma (ej. \"python\", \"node\"). Sin esta lista, cualquier imagen de docker.io/ghcr.io que no esté bloqueada queda permitida.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="allowedImages" rows="8" placeholder="python\nnode\ngolang")

    FormField(label="Lista negra (bloqueadas)" hint="Siempre gana sobre la lista blanca, aquí y en cada evento.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="blockedImages" rows="4" placeholder="alpine:edge\ndebian:unstable")

    BaseButton(:loading="saving" :disabled="saving || saveState === 'clean' || saveState === 'saved'" @click="save") Guardar cambios
    FeedbackMessage(v-if="saved" message="Cambios guardados." tone="success")
    FeedbackMessage(v-if="error" :message="error" tone="error")
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import FormField from '@/components/ui/FormField.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import SaveState from '@/components/ui/SaveState.vue'
import { getGlobalImagePolicy, setGlobalImagePolicy } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'AdminImagePolicyPage',
  components: { BaseButton, FeedbackMessage, FormField, LoadingState, SaveState },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const allowedImages = ref('')
    const blockedImages = ref('')
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')
    const initialPolicy = ref('')

    const saveState = computed(() => {
      if (saving.value) return 'saving'
      if (JSON.stringify({ allowedImages: allowedImages.value, blockedImages: blockedImages.value }) !== initialPolicy.value) return 'dirty'
      if (saved.value) return 'saved'
      return 'clean'
    })

    onMounted(async () => {
      try {
        const policy = await getGlobalImagePolicy(auth.state.token as string)
        allowedImages.value = toLines(policy.allowedImages)
        blockedImages.value = toLines(policy.blockedImages)
        initialPolicy.value = JSON.stringify({ allowedImages: allowedImages.value, blockedImages: blockedImages.value })
      } catch (err: any) {
        error.value = 'No fue posible cargar la política de imágenes.'
      } finally {
        loading.value = false
      }
    })

    function toLines(csv: string | null): string {
      if (!csv) return ''
      return csv.split(',').map((h) => h.trim()).filter(Boolean).join('\n')
    }

    function toCsv(lines: string): string {
      return lines.split(/[\n,]/).map((h) => h.trim()).filter(Boolean).join(',')
    }

    async function save() {
      saving.value = true; saved.value = false; error.value = ''
      try {
        const policy = await setGlobalImagePolicy(
          toCsv(allowedImages.value), toCsv(blockedImages.value), auth.state.token as string
        )
        allowedImages.value = toLines(policy.allowedImages)
        blockedImages.value = toLines(policy.blockedImages)
        initialPolicy.value = JSON.stringify({ allowedImages: allowedImages.value, blockedImages: blockedImages.value })
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    return { loading, allowedImages, blockedImages, saving, saved, error, saveState, save }
  }
}
</script>

<style scoped>
.image-policy-page { padding: 24px; max-width: 720px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 8px; }
.field-hint { margin: 0 0 16px; font-size: 0.85rem; }
.settings-card { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); }
textarea {
  font-size: 0.85rem;
  font-family: monospace; resize: vertical;
}
</style>
