<template lang="pug">
section
  DashboardBreadcrumb(:items="[{label:'Eventos',to:'/dashboard/events'},{label:'IA del evento'}]")
  EventLlmSettings(:event-id="conferenceId")
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import EventLlmSettings from '@/components/EventLlmSettings.vue'
export default defineComponent({ components: { DashboardBreadcrumb, EventLlmSettings }, props: { conferenceId: { type: String, required: true } } })
</script>
