<template lang="pug">
.egress-policy-page
  h2 Control de red (egress)
  p.field-hint Dominios que el IDE Web/CLI puede alcanzar cuando un evento tiene internet habilitado.
    |  Esta lista es GLOBAL: se suma a la de cada evento (nunca la reemplaza) y la lista negra
    |  siempre gana, tanto la global como la del evento — un evento no puede "desbloquear" un
    |  dominio que la plataforma prohíbe.

  LoadingState(v-if="loading" message="Cargando política de red…")
  .settings-card(v-else)
    SaveState(:state="saveState")
    FormField(label="Lista blanca (permitidos)" hint="Un dominio por línea o separados por coma. Usa *.dominio.com para incluir subdominios.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="allowedHosts" rows="8" placeholder="github.com&#10;*.npmjs.org")

    FormField(label="Lista negra (bloqueados)" hint="Siempre gana sobre la lista blanca, aquí y en cada evento.")
      template(#default="{ id, describedBy }")
        textarea(:id="id" :aria-describedby="describedBy" v-model="blockedHosts" rows="4" placeholder="localhost&#10;169.254.169.254")

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
import { getGlobalEgressPolicy, setGlobalEgressPolicy } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'AdminEgressPolicyPage',
  components: { BaseButton, FeedbackMessage, FormField, LoadingState, SaveState },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const allowedHosts = ref('')
    const blockedHosts = ref('')
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')
    const initialPolicy = ref('')

    const saveState = computed(() => {
      if (saving.value) return 'saving'
      if (JSON.stringify({ allowedHosts: allowedHosts.value, blockedHosts: blockedHosts.value }) !== initialPolicy.value) return 'dirty'
      if (saved.value) return 'saved'
      return 'clean'
    })

    onMounted(async () => {
      try {
        const policy = await getGlobalEgressPolicy(auth.state.token as string)
        allowedHosts.value = toLines(policy.allowedHosts)
        blockedHosts.value = toLines(policy.blockedHosts)
        initialPolicy.value = JSON.stringify({ allowedHosts: allowedHosts.value, blockedHosts: blockedHosts.value })
      } catch (err: any) {
        error.value = 'No fue posible cargar la política de red.'
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
        const policy = await setGlobalEgressPolicy(
          toCsv(allowedHosts.value), toCsv(blockedHosts.value), auth.state.token as string
        )
        allowedHosts.value = toLines(policy.allowedHosts)
        blockedHosts.value = toLines(policy.blockedHosts)
        initialPolicy.value = JSON.stringify({ allowedHosts: allowedHosts.value, blockedHosts: blockedHosts.value })
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    return { loading, allowedHosts, blockedHosts, saving, saved, error, saveState, save }
  }
}
</script>

<style scoped>
.egress-policy-page { padding: 24px; max-width: 720px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 8px; }
.field-hint { margin: 0 0 16px; font-size: 0.85rem; }
.settings-card { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); }
textarea {
  font-size: 0.85rem;
  font-family: monospace; resize: vertical;
}
</style>
