<template lang="pug">
.referrals-page
  DashboardBreadcrumb(:items="[{ label: 'Cuenta' }, { label: 'Recomendaciones beta' }]")
  h1 Recomendar organizadores
  p.page-hint Recomienda a alguien que quiera crear eventos. El administrador revisará la recomendación antes de enviarle una invitación.
  LoadingState(v-if="loading" message="Cargando tus recomendaciones…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  template(v-else)
    .quota-card
      StatusBadge(:status="quota.enabled ? 'ACTIVE' : 'INACTIVE'" :label="quota.enabled ? 'Recomendaciones habilitadas' : 'Aún no tienes permiso para recomendar'")
      p.page-hint(v-if="quota.enabled") Te quedan #[strong {{ quota.remaining }}] de {{ quota.limit }} recomendaciones en los últimos 30 días.
      p.page-hint(v-else) El administrador puede habilitar este permiso más adelante. Puedes seguir usando tus eventos normalmente.
    form.recommend-form(v-if="quota.enabled" @submit.prevent="submit")
      label.field(for="recommend-email")
        span Correo de la persona que recomiendas
        input#recommend-email(v-model.trim="email" type="email" maxlength="254" autocomplete="email" required placeholder="persona@ejemplo.com")
      BaseButton(type="submit" :loading="saving" :disabled="saving || quota.remaining <= 0") Enviar recomendación
      p.help-text No se enviará ningún correo hasta que el administrador apruebe la recomendación.
    FeedbackMessage(v-if="message" :message="message" :tone="messageTone")
    h2 Tus recomendaciones
    .table-scroll(v-if="quota.items.length")
      table.data-table
        thead
          tr
            th Correo
            th Estado
            th Fecha
        tbody
          tr(v-for="item in quota.items" :key="item.uuid")
            td(data-label="Correo") {{ item.email }}
            td(data-label="Estado")
              StatusBadge(:status="item.status" :label="referralLabel(item.status)")
            td(data-label="Fecha") {{ dateLabel(item.createdAt) }}
    EmptyState(v-else message="Todavía no has enviado recomendaciones.")
</template>

<script lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/features/auth/authStore'
import { getMyOrganizerReferrals, submitOrganizerReferral } from '@/services/api/organizerInvitationsApi'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'

export default {
  name: 'MyOrganizerReferralsPage',
  components: { DashboardBreadcrumb, BaseButton, StatusBadge, EmptyState, FeedbackMessage, LoadingState },
  setup() {
    const auth=useAuthStore();const loading=ref(true);const saving=ref(false);const error=ref('');const message=ref('');const messageTone=ref<'success'|'error'>('success');const email=ref('')
    const quota=ref({items:[],remaining:0,limit:3,enabled:false} as {items:any[],remaining:number,limit:number,enabled:boolean})
    async function load(){loading.value=true;error.value='';try{quota.value=await getMyOrganizerReferrals(auth.state.token as string)}catch(e:any){error.value='No se pudieron cargar tus recomendaciones.'}finally{loading.value=false}}
    async function submit(){saving.value=true;message.value='';try{await submitOrganizerReferral(auth.state.token as string,email.value);email.value='';message.value='Recomendación enviada para revisión. No se mandó un correo todavía.';messageTone.value='success';await load()}catch(e:any){message.value='No se pudo enviar. Revisa el correo, tu cupo y que no lo hayas recomendado antes.';messageTone.value='error'}finally{saving.value=false}}
    function referralLabel(status:string){return ({PENDING_REVIEW:'En revisión',INVITATION_SENT:'Aprobada; invitación enviada',DELIVERY_FAILED:'Aprobada; correo sin entregar',ACCEPTED:'Invitación aceptada',EXPIRED:'Invitación vencida',REVOKED:'Invitación revocada',REJECTED:'No aprobada'} as Record<string,string>)[status]||status}
    function dateLabel(value:string){return new Intl.DateTimeFormat('es-MX',{dateStyle:'medium',timeStyle:'short'}).format(new Date(value))}
    onMounted(load);return{loading,saving,error,message,messageTone,email,quota,submit,referralLabel,dateLabel}
  }
}
</script>

<style scoped>
.referrals-page { max-width:900px; margin:0 auto; }
h1,h2 { color:var(--color-heading); }
.quota-card { background:var(--color-surface); border:1px solid var(--color-border-subtle); border-radius:12px; padding:16px; margin:18px 0; }
.recommend-form { background:var(--color-surface); border:1px solid var(--color-border-subtle); border-radius:12px; padding:16px; display:flex; align-items:end; flex-wrap:wrap; gap:12px; }
.field { display:flex; flex-direction:column; gap:6px; min-width:min(100%,300px); color:var(--color-text-secondary); font-size:.86rem; font-weight:600; }
.field input { min-height:42px; }
.help-text { width:100%; margin:0; color:var(--color-text-muted); font-size:.82rem; }
.data-table { width:100%; border-collapse:collapse; background:var(--color-surface); }
.data-table th,.data-table td { padding:12px; border-bottom:1px solid var(--color-border-subtle); text-align:left; }
@media(max-width:680px){.data-table thead{display:none}.data-table,.data-table tbody,.data-table tr,.data-table td{display:block;width:100%}.data-table tr{border:1px solid var(--color-border-subtle);border-radius:10px;margin-bottom:10px;padding:6px}.data-table td{border:0;padding:8px}.data-table td::before{content:attr(data-label);display:block;font-size:.68rem;text-transform:uppercase;color:var(--color-text-muted);font-weight:700;margin-bottom:4px}}
</style>
