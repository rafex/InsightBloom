<template lang="pug">
.conf-config-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  h2 Configuración del evento

  nav.sub-links(v-if="conferenceId")
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/edit`") Editor
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/config`") Configuración

  .loading-text(v-if="loading") Cargando conferencia...
  .error(v-else-if="error") {{ error }}
  .form(v-else)
    .form-group(v-if="eventTypes.length")
      label Tipo de evento
      select(v-model="eventTypeKey")
        option(v-for="t in eventTypes" :key="t.key" :value="t.key") {{ t.name }}
      p.field-hint Determina qué herramientas están disponibles (boletos, encuestas, videollamada...).
      button.btn-outline(type="button" @click="saveEventType" :disabled="savingEventType")
        span(v-if="savingEventType") Guardando...
        span(v-else) Guardar tipo de evento
      p.success(v-if="eventTypeSaved") Tipo de evento actualizado.
      p.error(v-if="eventTypeError") {{ eventTypeError }}

    .form-group.tickets-group
      label Boletos y aforo
      p.field-hint Elige cómo se registran los asistentes: sin control (solo unirse), con aforo, o con mapa de asientos.
      select(v-model="seatingMode")
        option(value="NONE") Ninguno (solo unirse)
        option(value="GENERAL") Aforo (cupo limitado, sin asiento)
        option(value="SEATED") Con asientos (mapa del recinto)
      .coord-field
        span.coord-label Aforo máximo
        input(v-model.number="capacity" type="number" min="1" placeholder="10")
      p.field-hint Cuántas personas van a tener acceso al evento y sus herramientas (IDE, encuestas...), sin importar el modo de boletos elegido — la infraestructura tiene recursos limitados. Recomendado hasta {{ recommendedMaxCapacity }}.
      p.capacity-alert(v-if="capacityAlert" :class="capacityAlert.level") {{ capacityAlert.text }}
      button.btn-outline(type="button" @click="saveSeating" :disabled="savingSeating")
        span(v-if="savingSeating") Guardando...
        span(v-else) Guardar configuración de boletos
      p.success(v-if="seatingSaved") Configuración de boletos guardada.
      p.error(v-if="seatingError") {{ seatingError }}
      .ticket-links(v-if="seatingMode !== 'NONE'")
        router-link.btn-outline(:to="`/dashboard/conferences/${conferenceId}/check-in`") Ir al check-in
        router-link.btn-outline(v-if="seatingMode === 'SEATED'" :to="`/dashboard/conferences/${conferenceId}/venue-map`") Editar mapa de asientos

    .form-group.sandbox-group
      label IDE de código
      p.field-hint Configura el ambiente de desarrollo que reciben los asistentes en la pestaña "IDE". El ambiente incluye Java, Node.js y Python en el mismo sandbox — no hace falta elegir un lenguaje. Los alumnos eligen ellos mismos entre Web (code-server, un sandbox por alumno) y CLI (terminal con Neovim, se reutiliza entre alumnos) — abajo se configura el tamaño de cada pool por separado.
      .coord-field
        span.coord-label Sandboxes Web concurrentes (code-server)
        input(v-model.number="sandboxPoolSize" type="number" min="1" placeholder="1")
      .coord-field
        span.coord-label Sandboxes CLI concurrentes (Neovim)
        input(v-model.number="sandboxCliPoolSize" type="number" min="1" placeholder="1")
      .coord-field(v-if="cliEnabled")
        span.coord-label Alumnos por sandbox CLI
        input(v-model.number="sandboxSeatsPerPod" type="number" min="1" max="10" placeholder="4 (por defecto)")
      p.field-hint(v-if="cliEnabled") En modo CLI, varios alumnos pueden compartir el mismo sandbox — cada uno con su propio usuario y espacio de trabajo aislado dentro del mismo contenedor. El modo Web (code-server) no admite esto: siempre es un sandbox por alumno.
      .coord-field
        span.coord-label Paquetes adicionales (opcional)
        input(v-model="sandboxExtraPackages" type="text" placeholder="numpy pandas")
      .coord-field
        span.coord-label Repositorio git remoto (opcional)
        input(v-model="sandboxRemoteGitUrl" type="text" placeholder="https://github.com/...")
      .coord-field
        span.coord-label Memoria máxima de Java por sandbox (MB, opcional)
        input(v-model.number="sandboxJvmHeapMb" type="number" min="64" placeholder="256 (por defecto)")
      p.field-hint Límite de memoria (-Xmx) de las JVMs dentro del sandbox — el Language Server de Java y cualquier programa que corran los asistentes. Por defecto son chicas (256 MB), pensadas para cursos: no toman toda la memoria disponible del sandbox aunque puedan. No puede exceder el límite de memoria del contenedor configurado en la infraestructura; si lo excedés, el servidor rechaza el guardado.
      button.btn-outline(type="button" @click="saveSandboxConfig" :disabled="savingSandboxConfig")
        span(v-if="savingSandboxConfig") Guardando...
        span(v-else) Guardar configuración del IDE
      p.success(v-if="sandboxConfigSaved") Configuración del IDE guardada.
      p.error(v-if="sandboxConfigError") {{ sandboxConfigError }}
      label.toggle-row
        input(type="checkbox" v-model="sandboxInternetEnabled" @change="saveSandboxInternet" :disabled="savingSandboxInternet")
        span Permitir acceso a internet desde los sandboxes
      p.field-hint Por defecto los sandboxes no tienen salida a internet (solo el workspace local). Actívalo si el ejercicio necesita instalar paquetes o clonar repositorios en vivo.

      .sandbox-status
        .coord-field
          span.coord-label Estado de las máquinas
          button.btn-outline(type="button" @click="loadSandboxStatus" :disabled="loadingSandboxStatus")
            span(v-if="loadingSandboxStatus") Cargando...
            span(v-else) Ver estado de sandboxes
        p.field-hint Pods activos de este evento -- quién los ocupa, en qué modo (Web/CLI) y si ya están listos para usarse. Para revisar y editar los archivos de un alumno, usá el "Editor de código" en Moderación.
        p.error(v-if="sandboxStatusError") {{ sandboxStatusError }}
        table.incidents-table(v-if="sandboxStatusLoaded")
          thead
            tr
              th Pod
              th Modo
              th Fase
              th Listo
              th Asientos
          tbody
            tr(v-if="!sandboxStatus.length")
              td(colspan="5") No hay sandboxes activos.
            tr(v-for="pod in sandboxStatus" :key="pod.podName")
              td {{ pod.podName }}
              td {{ pod.variant === 'cli' ? 'CLI' : 'Web' }}
              td {{ pod.phase }}
              td {{ pod.ready ? '✓' : '—' }}
              td.seats-cell
                span.seat-badge(v-for="seat in pod.seats" :key="seat.seatIndex")
                  span.seat-user(v-if="seat.userUuid") {{ seat.userUuid }}
                  span.seat-empty(v-else) (libre)

      .sandbox-incidents(v-if="cliEnabled")
        .coord-field
          span.coord-label Incidentes de recursos
          button.btn-outline(type="button" @click="loadSandboxIncidents" :disabled="loadingSandboxIncidents")
            span(v-if="loadingSandboxIncidents") Cargando...
            span(v-else) Ver incidentes
        p.field-hint Cuando un sandbox compartido detecta que un alumno acapara CPU/memoria (por error o a propósito), lo reinicia automáticamente y lo registra acá — para que sepas quién está causando problemas.
        p.error(v-if="sandboxIncidentsError") {{ sandboxIncidentsError }}
        table.incidents-table(v-if="sandboxIncidentsLoaded")
          thead
            tr
              th Cuándo
              th Alumno
              th Tipo
              th Detalle
          tbody
            tr(v-if="!sandboxIncidents.length")
              td(colspan="4") Sin incidentes registrados.
            tr(v-for="incident in sandboxIncidents" :key="incident.uuid")
              td {{ new Date(incident.occurredAt).toLocaleString() }}
              td {{ incident.userUuid || '(desconocido)' }}
              td {{ incidentTypeLabel(incident.type) }}
              td {{ incident.detail }}

    .form-group.device-access-group
      label Acceso por dispositivo
      p.field-hint Controla cuántos dispositivos puede usar a la vez un mismo asistente en Videollamada e IDE, y bloquea automáticamente un dispositivo que se loguea con demasiadas cuentas distintas (podés revisar y desbloquear desde "Bloqueos", en Moderación).
      .coord-field
        span.coord-label Máx. dispositivos activos por usuario
        input(v-model.number="maxDevicesPerUser" type="number" min="1" max="10" placeholder="2 (por defecto)")
      .coord-field
        span.coord-label Máx. cuentas distintas por dispositivo antes de bloquear
        input(v-model.number="maxAccountsPerDevice" type="number" min="1" max="50" placeholder="3 (por defecto)")
      button.btn-outline(type="button" @click="saveDeviceAccessConfig" :disabled="savingDeviceAccessConfig")
        span(v-if="savingDeviceAccessConfig") Guardando...
        span(v-else) Guardar acceso por dispositivo
      p.success(v-if="deviceAccessConfigSaved") Configuración de acceso por dispositivo guardada.
      p.error(v-if="deviceAccessConfigError") {{ deviceAccessConfigError }}

    .form-group.roles-group(v-if="canManageRoles")
      label Roles del evento
      p.field-hint Asigna moderadores, staff de acceso u otros roles a personas solo para este evento.
      .roles-list(v-if="eventRoles.length")
        .role-row(v-for="r in eventRoles" :key="r.userUuid")
          span.role-person {{ r.displayName || r.email || r.userUuid }}
          span.role-badge {{ roleName(r.roleKey) }}
          button.btn-remove(type="button" @click="removeRole(r.userUuid)") Quitar
      .assign-row
        input(v-model="assignIdentifier" type="text" placeholder="Email o usuario")
        select(v-model="assignRoleKey")
          option(v-for="role in assignableRoles" :key="role.key" :value="role.key") {{ role.name }}
        button.btn-outline(type="button" @click="assignRole" :disabled="assigning") Asignar
      p.success(v-if="roleAssigned") Rol asignado.
      p.error(v-if="roleError") {{ roleError }}
</template>

<script lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getConference, setSeatingMode, getActiveEventTypes, setEventType,
  getEventRoles, getActiveRoles, assignEventRole, removeEventRole, setSandboxConfig, setSandboxInternet,
  listSandboxIncidents, listSandboxStatus, setDeviceAccessConfig
} from '@/services/api/usersApi'
import type { Conference, SeatingMode, EventType, EventRoleAssignment, Role, SandboxIncident, SandboxStatusEntry } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { capacityWarning, RECOMMENDED_MAX_CAPACITY } from '@/utils/capacityWarning'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'

export default {
  name: 'ConferenceConfigPage',
  components: { DashboardBreadcrumb },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth        = useAuthStore()
    const conference   = ref<Conference | null>(null)
    const loading      = ref(true)
    const error        = ref('')

    const seatingMode  = ref<SeatingMode>('NONE')
    const capacity     = ref<number | null>(null)
    const recommendedMaxCapacity = RECOMMENDED_MAX_CAPACITY
    const capacityAlert = computed(() => capacityWarning(capacity.value))
    const savingSeating = ref(false)
    const seatingSaved  = ref(false)
    const seatingError  = ref('')
    // Vestigial (pools Web/CLI independientes, ver AssignSandboxUseCase) -- ya no selecciona "la"
    // variante, se mantiene solo por compatibilidad de lectura de conferencias viejas. La UI ya
    // no lo edita directamente: los dos pools de abajo son la config real.
    const sandboxVariant = ref('')
    const sandboxPoolSize = ref<number | null>(1)
    // Pool "cli" (Neovim, reusable/multi-alumno) -- independiente del pool "web" de arriba.
    const sandboxCliPoolSize = ref<number | null>(1)
    const cliEnabled = computed(() => (sandboxCliPoolSize.value ?? 0) > 0)
    const sandboxExtraPackages = ref('')
    const sandboxRemoteGitUrl = ref('')
    // Heap maximo (-Xmx, en MB) de las JVMs del sandbox -- null = usa el default chico del
    // backend (256Mi, ver KubernetesPodClient). El backend rechaza (400) valores que excedan el
    // limite de memoria del contenedor configurado en el chart de despliegue -- no se valida ese
    // techo exacto aca en el frontend porque depende de infra (values.yaml), no de este repo.
    const sandboxJvmHeapMb = ref<number | null>(null)
    // Alumnos que comparten un mismo Pod "cli" (usuario Linux propio por alumno dentro del
    // mismo contenedor) -- null = usa el default del backend (4). Solo aplica al pool CLI; el
    // pool Web (code-server) no tiene efecto (no se puede compartir).
    const sandboxSeatsPerPod = ref<number | null>(null)
    const sandboxInternetEnabled = ref(false)
    const savingSandboxConfig = ref(false)
    const sandboxConfigSaved = ref(false)
    const sandboxConfigError = ref('')
    const savingSandboxInternet = ref(false)
    const sandboxIncidents = ref<SandboxIncident[]>([])
    const sandboxIncidentsLoaded = ref(false)
    const loadingSandboxIncidents = ref(false)
    const sandboxIncidentsError = ref('')
    const sandboxStatus = ref<SandboxStatusEntry[]>([])
    const sandboxStatusLoaded = ref(false)
    const loadingSandboxStatus = ref(false)
    const sandboxStatusError = ref('')
    const maxDevicesPerUser = ref<number | null>(null)
    const maxAccountsPerDevice = ref<number | null>(null)
    const savingDeviceAccessConfig = ref(false)
    const deviceAccessConfigSaved = ref(false)
    const deviceAccessConfigError = ref('')
    const eventTypes    = ref<EventType[]>([])
    const eventTypeKey  = ref('conference')
    const savingEventType = ref(false)
    const eventTypeSaved  = ref(false)
    const eventTypeError  = ref('')
    const eventRoles      = ref<EventRoleAssignment[]>([])
    const assignableRoles = ref<Role[]>([])
    const canManageRoles  = ref(false)
    const assignIdentifier = ref('')
    const assignRoleKey   = ref('')
    const assigning       = ref(false)
    const roleAssigned    = ref(false)
    const roleError       = ref('')

    onMounted(async () => {
      try {
        const [conf, types] = await Promise.all([
          getConference(props.conferenceId as string, auth.state.token as string),
          getActiveEventTypes()
        ])
        conference.value = conf
        eventTypes.value = types
        eventTypeKey.value = conference.value.eventTypeKey || 'conference'
        seatingMode.value = (conference.value.seatingMode as SeatingMode) || 'NONE'
        capacity.value = conference.value.capacity ?? null
        sandboxVariant.value = conference.value.sandboxVariant === 'terminal-nvim' ? 'terminal-nvim' : ''
        sandboxPoolSize.value = conference.value.sandboxPoolSize ?? 1
        sandboxCliPoolSize.value = conference.value.sandboxCliPoolSize ?? 1
        sandboxExtraPackages.value = conference.value.sandboxExtraPackages || ''
        sandboxRemoteGitUrl.value = conference.value.sandboxRemoteGitUrl || ''
        sandboxJvmHeapMb.value = conference.value.sandboxJvmHeapMb ?? null
        sandboxSeatsPerPod.value = conference.value.sandboxSeatsPerPod ?? null
        sandboxInternetEnabled.value = conference.value.sandboxInternetEnabled === 1
        maxDevicesPerUser.value = conference.value.maxDevicesPerUser ?? null
        maxAccountsPerDevice.value = conference.value.maxAccountsPerDevice ?? null
      } catch (e: any) {
        error.value = 'No se pudo cargar la conferencia.'
      } finally {
        loading.value = false
      }

      // La seccion de roles solo aparece si el backend confirma que el usuario actual
      // tiene ASSIGN_EVENT_ROLES sobre este evento (403 = no la muestra, sin error visible).
      try {
        const [roles, activeEventRoles] = await Promise.all([
          getEventRoles(props.conferenceId as string, auth.state.token as string),
          getActiveRoles('EVENT')
        ])
        eventRoles.value = roles
        assignableRoles.value = activeEventRoles
        assignRoleKey.value = activeEventRoles[0]?.key || ''
        canManageRoles.value = true
      } catch (e: any) {
        canManageRoles.value = false
      }
    })

    function roleName(key: string): string {
      return assignableRoles.value.find((r) => r.key === key)?.name || key
    }

    async function assignRole() {
      if (!assignIdentifier.value.trim() || !assignRoleKey.value) return
      assigning.value = true; roleError.value = ''; roleAssigned.value = false
      try {
        await assignEventRole(props.conferenceId as string, assignIdentifier.value.trim(),
          assignRoleKey.value, auth.state.token as string)
        eventRoles.value = await getEventRoles(props.conferenceId as string, auth.state.token as string)
        assignIdentifier.value = ''
        roleAssigned.value = true
      } catch (e: any) {
        roleError.value = e.response?.data?.error?.message || 'No se pudo asignar el rol'
      } finally {
        assigning.value = false
      }
    }

    async function removeRole(userUuid: string) {
      try {
        await removeEventRole(props.conferenceId as string, userUuid, auth.state.token as string)
        eventRoles.value = eventRoles.value.filter((r) => r.userUuid !== userUuid)
      } catch (e: any) {
        roleError.value = e.response?.data?.error?.message || 'No se pudo quitar el rol'
      }
    }

    async function saveSeating() {
      savingSeating.value = true; seatingError.value = ''; seatingSaved.value = false
      try {
        conference.value = await setSeatingMode(
          props.conferenceId as string, seatingMode.value, capacity.value,
          auth.state.token as string
        )
        seatingSaved.value = true
      } catch (e: any) {
        seatingError.value = e.response?.data?.error?.message || 'No se pudo guardar la configuración de boletos'
      } finally {
        savingSeating.value = false
      }
    }

    async function saveSandboxConfig() {
      savingSandboxConfig.value = true; sandboxConfigError.value = ''; sandboxConfigSaved.value = false
      try {
        conference.value = await setSandboxConfig(
          props.conferenceId as string, sandboxVariant.value, sandboxPoolSize.value,
          sandboxExtraPackages.value.trim() || null, sandboxRemoteGitUrl.value.trim() || null,
          sandboxJvmHeapMb.value, sandboxSeatsPerPod.value, sandboxCliPoolSize.value,
          auth.state.token as string
        )
        sandboxConfigSaved.value = true
      } catch (e: any) {
        sandboxConfigError.value = e.response?.data?.error?.message || 'No se pudo guardar la configuración del IDE'
      } finally {
        savingSandboxConfig.value = false
      }
    }

    async function saveSandboxInternet() {
      savingSandboxInternet.value = true
      try {
        conference.value = await setSandboxInternet(
          props.conferenceId as string, sandboxInternetEnabled.value, auth.state.token as string
        )
      } catch (e: any) {
        sandboxInternetEnabled.value = !sandboxInternetEnabled.value
        sandboxConfigError.value = e.response?.data?.error?.message || 'No se pudo cambiar el acceso a internet'
      } finally {
        savingSandboxInternet.value = false
      }
    }

    async function saveDeviceAccessConfig() {
      savingDeviceAccessConfig.value = true; deviceAccessConfigError.value = ''; deviceAccessConfigSaved.value = false
      try {
        conference.value = await setDeviceAccessConfig(
          props.conferenceId as string, maxDevicesPerUser.value, maxAccountsPerDevice.value,
          auth.state.token as string
        )
        deviceAccessConfigSaved.value = true
      } catch (e: any) {
        deviceAccessConfigError.value = e.response?.data?.error?.message || 'No se pudo guardar el acceso por dispositivo'
      } finally {
        savingDeviceAccessConfig.value = false
      }
    }

    async function loadSandboxIncidents() {
      loadingSandboxIncidents.value = true; sandboxIncidentsError.value = ''
      try {
        sandboxIncidents.value = await listSandboxIncidents(props.conferenceId as string, auth.state.token as string)
        sandboxIncidentsLoaded.value = true
      } catch (e: any) {
        sandboxIncidentsError.value = e.response?.data?.error?.message || 'No se pudieron cargar los incidentes'
      } finally {
        loadingSandboxIncidents.value = false
      }
    }

    async function loadSandboxStatus() {
      loadingSandboxStatus.value = true; sandboxStatusError.value = ''
      try {
        sandboxStatus.value = await listSandboxStatus(props.conferenceId as string, auth.state.token as string)
        sandboxStatusLoaded.value = true
      } catch (e: any) {
        sandboxStatusError.value = e.response?.data?.error?.message || 'No se pudo cargar el estado de los sandboxes'
      } finally {
        loadingSandboxStatus.value = false
      }
    }

    function incidentTypeLabel(type: string): string {
      const labels: Record<string, string> = {
        cpu_abuse: 'Uso excesivo de CPU',
        memory_abuse: 'Uso excesivo de memoria',
        fork_bomb_suspected: 'Posible fork-bomb',
      }
      return labels[type] || type
    }

    async function saveEventType() {
      savingEventType.value = true; eventTypeError.value = ''; eventTypeSaved.value = false
      try {
        conference.value = await setEventType(props.conferenceId as string, eventTypeKey.value, auth.state.token as string)
        eventTypeSaved.value = true
      } catch (e: any) {
        eventTypeError.value = e.response?.data?.error?.message || 'No se pudo guardar el tipo de evento'
      } finally {
        savingEventType.value = false
      }
    }

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conference.value?.name || props.conferenceId || '', loading: loading.value && !conference.value },
      { label: 'Configuración' }
    ])

    return { conference, loading, error,
             seatingMode, capacity, recommendedMaxCapacity, capacityAlert, savingSeating, seatingSaved, seatingError, saveSeating,
             sandboxVariant, sandboxPoolSize, sandboxCliPoolSize, cliEnabled,
             sandboxExtraPackages, sandboxRemoteGitUrl, sandboxJvmHeapMb,
             sandboxSeatsPerPod, sandboxInternetEnabled,
             savingSandboxConfig, sandboxConfigSaved, sandboxConfigError, savingSandboxInternet,
             saveSandboxConfig, saveSandboxInternet,
             sandboxIncidents, sandboxIncidentsLoaded, loadingSandboxIncidents, sandboxIncidentsError,
             loadSandboxIncidents, incidentTypeLabel,
             sandboxStatus, sandboxStatusLoaded, loadingSandboxStatus, sandboxStatusError, loadSandboxStatus,
             maxDevicesPerUser, maxAccountsPerDevice, savingDeviceAccessConfig,
             deviceAccessConfigSaved, deviceAccessConfigError, saveDeviceAccessConfig,
             eventTypes, eventTypeKey, savingEventType, eventTypeSaved, eventTypeError, saveEventType,
             eventRoles, assignableRoles, canManageRoles, assignIdentifier, assignRoleKey, assigning,
             roleAssigned, roleError, roleName, assignRole, removeRole, breadcrumbItems }
  }
}
</script>

<style scoped>
.conf-config-page { max-width: 680px; }
h2 { color: #1e1b4b; margin-bottom: 8px; margin-top: 0; }
.sub-links { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 24px; }
.sub-link {
  padding: 6px 14px; border: 1.5px solid #e5e7eb; border-radius: 20px; text-decoration: none;
  color: #374151; font-size: 0.82rem; font-weight: 500; transition: all 0.15s;
}
.sub-link:hover { border-color: #a5b4fc; color: #4f46e5; }
.sub-link.router-link-active { background: #4f46e5; color: #fff; border-color: #4f46e5; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="text"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
input:focus { outline: none; border-color: #4f46e5; }
.toggle-row { display: flex; align-items: center; gap: 10px; font-weight: 500; cursor: pointer; margin-top: 4px; }
.toggle-row input { width: auto; }

.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: #9ca3af; }

.tickets-group select { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; margin-bottom: 10px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
.ticket-links { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 4px; }

.capacity-alert { margin: 6px 0 0; font-size: 0.82rem; font-weight: 600; padding: 6px 10px; border-radius: 6px; }
.capacity-alert.warning { background: #fef3c7; color: #92400e; }
.capacity-alert.risk { background: #ffedd5; color: #9a3412; }
.capacity-alert.critical { background: #fee2e2; color: #991b1b; }

.sandbox-status { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb; }
.sandbox-incidents { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb; }
.incidents-table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 0.82rem; }
.incidents-table th { text-align: left; padding: 6px 10px; background: #f9fafb; color: #6b7280; font-weight: 600; }
.incidents-table td { padding: 6px 10px; border-top: 1px solid #f3f4f6; color: #374151; }

.seats-cell { display: flex; flex-wrap: wrap; gap: 6px; }
.seat-badge { display: inline-flex; }
.seat-user { background: #eef2ff; color: #4f46e5; border-radius: 6px; padding: 2px 8px; font-size: 0.78rem; font-family: monospace; }
.seat-empty { font-size: 0.78rem; color: #9ca3af; }

.roles-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.role-row { display: flex; align-items: center; gap: 10px; padding: 6px 10px; background: #f9fafb; border-radius: 8px; }
.role-person { flex: 1; font-size: 0.85rem; color: #374151; }
.role-badge { font-size: 0.72rem; background: #e0e7ff; color: #4338ca; padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.btn-remove { padding: 4px 10px; border: 1px solid #e5e7eb; border-radius: 6px; background: #fff; color: #dc2626; cursor: pointer; font-size: 0.78rem; }
.assign-row { display: flex; gap: 8px; flex-wrap: wrap; }
.assign-row input, .assign-row select { padding: 8px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 0.9rem; }
.assign-row input { flex: 1; min-width: 160px; }
.btn-outline { padding: 10px 22px; border: 1.5px solid #4f46e5; color: #4f46e5; border-radius: 8px; text-decoration: none; font-size: 1rem; background: none; cursor: pointer; }
.error { color: #dc2626; font-size: 0.9rem; margin-bottom: 12px; }
.success { color: #166534; font-size: 0.9rem; margin-bottom: 12px; }
.loading-text { color: #6b7280; }
</style>
