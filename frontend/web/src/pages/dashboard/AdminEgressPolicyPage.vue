<template lang="pug">
.egress-policy-page
  h2 Control de red (egress)
  p.field-hint Dominios que el IDE Web/CLI puede alcanzar cuando un evento tiene internet habilitado.
    |  Esta lista es GLOBAL: se suma a la de cada evento (nunca la reemplaza) y la lista negra
    |  siempre gana, tanto la global como la del evento — un evento no puede "desbloquear" un
    |  dominio que la plataforma prohíbe.

  .loading-text(v-if="loading") Cargando...
  .settings-card(v-else)
    .form-group
      label Lista blanca (permitidos)
      p.field-hint Un dominio por línea o separados por coma. Usa {{ '*.dominio.com' }} para incluir subdominios.
      textarea(v-model="allowedHosts" rows="8" placeholder="github.com&#10;*.npmjs.org")

    .form-group
      label Lista negra (bloqueados)
      p.field-hint Siempre gana sobre la lista blanca, aquí y en cada evento.
      textarea(v-model="blockedHosts" rows="4" placeholder="localhost&#10;169.254.169.254")

    BaseButton(:loading="saving" @click="save") Guardar cambios
    p.success(v-if="saved") Cambios guardados.
    p.error(v-if="error") {{ error }}
</template>

<script lang="ts">
import { ref, onMounted } from 'vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import { getGlobalEgressPolicy, setGlobalEgressPolicy } from '@/services/api/usersApi'
import { useAuthStore } from '@/features/auth/authStore'

export default {
  name: 'AdminEgressPolicyPage',
  components: { BaseButton },
  setup() {
    const auth = useAuthStore()
    const loading = ref(true)
    const allowedHosts = ref('')
    const blockedHosts = ref('')
    const saving = ref(false)
    const saved = ref(false)
    const error = ref('')

    onMounted(async () => {
      try {
        const policy = await getGlobalEgressPolicy(auth.state.token as string)
        allowedHosts.value = toLines(policy.allowedHosts)
        blockedHosts.value = toLines(policy.blockedHosts)
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
        saved.value = true
      } catch (err: any) {
        error.value = err.response?.data?.error?.message || 'No se pudo guardar el cambio'
      } finally {
        saving.value = false
      }
    }

    return { loading, allowedHosts, blockedHosts, saving, saved, error, save }
  }
}
</script>

<style scoped>
.egress-policy-page { padding: 24px; max-width: 720px; margin: 0 auto; }
h2 { color: var(--color-heading); margin-bottom: 8px; }
.field-hint { margin: 0 0 16px; font-size: 0.85rem; }
.settings-card { background: var(--color-surface); border-radius: 12px; padding: 20px; border: 1px solid var(--color-border-subtle); }
.form-group { display: flex; flex-direction: column; gap: 4px; margin-bottom: 20px; }
.form-group label { font-weight: 600; font-size: 0.9rem; color: var(--color-text-secondary); }
.form-group textarea {
  padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.85rem;
  font-family: monospace; resize: vertical;
}
.success { color: var(--color-success); font-size: 0.85rem; margin-top: 10px; }
.error { color: var(--color-danger); font-size: 0.85rem; margin-top: 10px; }
</style>
