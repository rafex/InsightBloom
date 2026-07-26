<template lang="pug">
.conf-config-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  h2 Configuración del evento

  nav.sub-links(v-if="conferenceId")
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/edit`") Editor
    router-link.sub-link(:to="`/dashboard/conferences/${conferenceId}/config`") Configuración

  nav.config-tabs(v-if="!loading && !error" aria-label="Secciones de configuración")
    button.config-tab(type="button" :class="{ active: activeTab === 'general' }" @click="activeTab = 'general'") General
    button.config-tab(type="button" :class="{ active: activeTab === 'tools' }" @click="activeTab = 'tools'") Herramientas
    button.config-tab(type="button" :class="{ active: activeTab === 'sandbox' }" @click="activeTab = 'sandbox'") IDE y sandboxes
    button.config-tab(type="button" :class="{ active: activeTab === 'access' }" @click="activeTab = 'access'") Acceso y roles
    button.config-tab(type="button" :class="{ active: activeTab === 'ai' }" @click="activeTab = 'ai'") 🤖 IA
    button.config-tab(type="button" :class="{ active: activeTab === 'network' }" @click="activeTab = 'network'") 🌐 Red

  .loading-text(v-if="loading") Cargando conferencia...
  .error(v-else-if="error") {{ error }}
  .form(v-else)
    .form-group.general-group(v-if="eventTypes.length" v-show="activeTab === 'general'")
      label Tipo de evento
      select(v-model="eventTypeKey")
        option(v-for="t in eventTypes" :key="t.key" :value="t.key") {{ t.name }}
      p.field-hint Determina qué herramientas están disponibles (boletos, encuestas, videollamada...).
      button.btn-outline(type="button" @click="saveEventType" :disabled="savingEventType")
        span(v-if="savingEventType") Guardando...
        span(v-else) Guardar tipo de evento
      p.success(v-if="eventTypeSaved") Tipo de evento actualizado.
      p.error(v-if="eventTypeError") {{ eventTypeError }}

    .form-group.certificate-engine-group(v-show="activeTab === 'tools'")
      label Motor de certificado
      select(v-model="certificateEngine")
        option(value="INHOUSE") Inhouse (PDFBox)
        option(value="HTML_CHROME") HTML + Playwright/Chromium
      p.field-hint INHOUSE usa el editor legacy y la configuración global como respaldo. HTML + Playwright/Chromium usa el editor visual del evento y su catálogo de diseños.
      button.btn-outline(type="button" @click="saveCertificateEngine" :disabled="savingCertificateEngine")
        span(v-if="savingCertificateEngine") Guardando...
        span(v-else) Guardar motor de certificado
      p.success(v-if="certificateEngineSaved") Motor de certificado actualizado.
      p.error(v-if="certificateEngineError") {{ certificateEngineError }}

    .form-group.canvas-group(v-show="activeTab === 'tools'")
      label Lienzo del evento
      p.field-hint Selecciona una o varias herramientas y define el modo de cada una. Si no seleccionas ninguna, se mantiene el modo legado del tipo de evento.
      .canvas-tools
        label.canvas-tool-option(v-for="tool in canvasToolOptions" :key="tool.value")
          input(type="checkbox" :value="tool.value" v-model="canvasTools")
          span {{ tool.label }}
      .canvas-mode-row(v-for="tool in canvasTools" :key="tool")
        span.canvas-mode-label {{ canvasToolLabel(tool) }}
        select(v-model="canvasModes[tool]")
          option(v-for="option in canvasModeOptions(tool)" :key="option.value" :value="option.value") {{ option.label }}
      p.field-hint(v-if="canvasTools.includes('ETHERPAD')") Etherpad sólo admite notas grupales (todos colaboran) o notas individuales (un pad privado por asistente); no tiene modo de publicación exclusiva del moderador. Las notas individuales se borran al vencer el evento y se pueden exportar.
      button.btn-outline(type="button" @click="saveCanvasConfig" :disabled="savingCanvasConfig")
        span(v-if="savingCanvasConfig") Guardando...
        span(v-else) Guardar configuración del lienzo
      p.success(v-if="canvasConfigSaved") Configuración del lienzo guardada.
      p.error(v-if="canvasConfigError") {{ canvasConfigError }}

    .form-group.tickets-group(v-show="activeTab === 'general'")
      label Boletos y aforo
      p.field-hint Elige cómo se registran los asistentes: sin control (solo unirse), con aforo, o con mapa de asientos.
      select(v-model="seatingMode")
        option(value="NONE") Ninguno (solo unirse)
        option(value="GENERAL") Aforo (cupo limitado, sin asiento)
        option(value="SEATED") Con asientos (mapa del recinto)
      .coord-field
        span.coord-label Aforo máximo
        input(v-model.number="capacity" type="number" min="2" placeholder="10")
      p.field-hint Cuántas personas van a tener acceso al evento y sus herramientas (IDE, encuestas...), sin importar el modo de boletos elegido — la infraestructura tiene recursos limitados. El mínimo es 2 porque el creador ocupa un boleto operativo contado. Cada moderador adicional ocupa otra plaza. Recomendado hasta {{ recommendedMaxCapacity }}.
      p.capacity-alert(v-if="capacityAlert" :class="capacityAlert.level") {{ capacityAlert.text }}
      button.btn-outline(type="button" @click="saveSeating" :disabled="savingSeating")
        span(v-if="savingSeating") Guardando...
        span(v-else) Guardar configuración de boletos
      p.success(v-if="seatingSaved") Configuración de boletos guardada.
      p.error(v-if="seatingError") {{ seatingError }}
      label.toggle-row
        input(type="checkbox" v-model="ticketSalesEnabled")
        span Permitir adquisición de boletos desde la cartelera pública
      p.field-hint El evento puede seguir activo aunque cierres la emisión de boletos. Los boletos ya emitidos conservan su acceso.
      button.btn-outline(type="button" @click="saveTicketSales" :disabled="savingTicketSales")
        span(v-if="savingTicketSales") Guardando...
        span(v-else) Guardar disponibilidad de boletos
      p.success(v-if="ticketSalesSaved") Disponibilidad de boletos actualizada.
      p.error(v-if="ticketSalesError") {{ ticketSalesError }}
      .ticket-links(v-if="seatingMode !== 'NONE' || eventTypes.find(t => t.key === eventTypeKey)?.capabilities.some(c => c.startsWith('TICKETING_'))")
        router-link.btn-outline(:to="`/dashboard/conferences/${conferenceId}/tickets`") Administrar boletos
        router-link.btn-outline(:to="`/dashboard/conferences/${conferenceId}/check-in`") Ir al check-in
        router-link.btn-outline(v-if="seatingMode === 'SEATED'" :to="`/dashboard/conferences/${conferenceId}/venue-map`") Editar mapa de asientos

    .form-group.sandbox-group(v-show="activeTab === 'sandbox'")
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
        input(v-model.number="sandboxJvmHeapMb" type="number" min="64" placeholder="70 (por defecto)")
      p.field-hint Límite de memoria (-Xmx) de las JVMs dentro del sandbox — el Language Server de Java y cualquier programa que corran los asistentes. Por defecto son chicas (70 MB), pensadas para cursos: no toman toda la memoria disponible del sandbox aunque puedan. No puede exceder el límite de memoria del contenedor configurado en la infraestructura; si lo excedés, el servidor rechaza el guardado.
      button.btn-outline(type="button" @click="saveSandboxConfig" :disabled="savingSandboxConfig")
        span(v-if="savingSandboxConfig") Guardando...
        span(v-else) Guardar configuración del IDE
      p.success(v-if="sandboxConfigSaved") Configuración del IDE guardada.
      p.error(v-if="sandboxConfigError") {{ sandboxConfigError }}
      label.toggle-row
        input(type="checkbox" v-model="sandboxInternetEnabled" @change="saveSandboxInternet" :disabled="savingSandboxInternet")
        span Permitir acceso a internet desde los sandboxes
      p.field-hint Por defecto los sandboxes no tienen salida de red. Al activarlo, solo pueden salir mediante la proxy interna hacia los hosts de la lista blanca definida por la plataforma; la lista negra siempre tiene prioridad.

      .sandbox-status
        .coord-field
          span.coord-label Estado de las máquinas
          button.btn-outline(type="button" @click="loadSandboxStatus" :disabled="loadingSandboxStatus")
            span(v-if="loadingSandboxStatus") Cargando...
            span(v-else) Ver estado de sandboxes
        .prewarm-control
          button.btn-outline(type="button" @click="prewarmSandboxPool" :disabled="prewarmingSandboxPool")
            span(v-if="prewarmingSandboxPool") Preparando...
            span(v-else) Preparar sandboxes antes del evento
          p.field-hint Crea por adelantado los Pods Web y CLI configurados, pero no los asigna a ningún alumno. Quedan listos para reclamarse cuando entren los asistentes.
        p.success(v-if="sandboxPrewarmResult")
          | Pool solicitado: Web {{ sandboxPrewarmResult.variants.find(v => v.variant === 'web')?.createdPods || 0 }} nuevos de {{ sandboxPrewarmResult.variants.find(v => v.variant === 'web')?.desiredPods || 0 }}; CLI {{ sandboxPrewarmResult.variants.find(v => v.variant === 'cli')?.createdPods || 0 }} nuevos de {{ sandboxPrewarmResult.variants.find(v => v.variant === 'cli')?.desiredPods || 0 }}.
        p.error(v-if="sandboxPrewarmError") {{ sandboxPrewarmError }}
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
              th Acciones
          tbody
            tr(v-if="!sandboxStatus.length")
              td(colspan="6") No hay sandboxes activos.
            tr(v-for="pod in sandboxStatus" :key="pod.podName")
              td {{ pod.podName }}
              td {{ pod.variant === 'cli' ? 'CLI' : 'Web' }}
              td {{ pod.phase }}
              td {{ pod.ready ? '✓' : '—' }}
              td.seats-cell
                span.seat-badge(v-for="seat in pod.seats" :key="seat.seatIndex")
                  span.seat-user(v-if="seat.userUuid") {{ seat.userUuid }}
                  span.seat-empty(v-else) (libre)
              td.sandbox-actions
                button.btn-small(type="button" @click="deleteSandbox(pod)" :disabled="sandboxActionBusy === pod.podName")
                  span(v-if="sandboxActionBusy === pod.podName") Procesando...
                  span(v-else) Eliminar
                template(v-if="sandboxIsFree(pod)")
                  button.btn-small.btn-recreate(type="button" @click="recreateSandbox(pod)" :disabled="sandboxActionBusy === pod.podName")
                    span(v-if="sandboxActionBusy === pod.podName") Procesando...
                    span(v-else) Recrear
                span.action-note(v-else) Ocupado: eliminación forzada
        p.error(v-if="sandboxActionError") {{ sandboxActionError }}

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

    .form-group.device-access-group(v-show="activeTab === 'access'")
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

    .form-group.roles-group(v-if="canManageRoles" v-show="activeTab === 'access'")
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

    .form-group.mentor-group(v-show="activeTab === 'ai'")
      label Tutor IA del evento
      p.field-hint Configuración pedagógica exclusiva de este evento. El proveedor, la URL base y la clave del Tutor IA (compartidos por toda la plataforma) se configuran aparte, en #[router-link(to="/dashboard/admin/ai/tutor") IA → Tutor IA] (solo administradores).
      label.toggle-row
        input(type="checkbox" v-model="mentorEnabled" :disabled="savingMentor")
        span {{ mentorEnabled ? 'Tutor habilitado para los asistentes' : 'Tutor deshabilitado para los asistentes' }}
      .coord-field
        span.coord-label Objetivo pedagógico del taller
        textarea(v-model="mentorObjective" rows="4" maxlength="2000" placeholder="Qué deben aprender o construir los asistentes")
      .coord-field
        span.coord-label Instrucciones adicionales y límites
        textarea(v-model="mentorPrompt" rows="5" maxlength="8000" placeholder="Por ejemplo: pedir primero qué intentaron y dar una pista a la vez")
      label.toggle-row
        input(type="checkbox" v-model="mentorIncludePresentation" :disabled="savingMentor")
        span Leer la presentación como contexto de consulta
      p.field-hint 🧭 Modo socrático activo: el tutor hará preguntas y dará pistas graduales; no entregará la solución completa.
      .coord-field
        span.coord-label Máximo de consultas por usuario/minuto
        input(v-model.number="mentorMaxRequests" type="number" min="1" max="30" :disabled="savingMentor")
      button.btn-outline(type="button" @click="saveMentor" :disabled="savingMentor")
        span(v-if="savingMentor") Guardando...
        span(v-else) Guardar configuración pedagógica del evento
      p.success(v-if="mentorSaved") Configuración del tutor IA guardada.
      p.error(v-if="mentorError") {{ mentorError }}

    .form-group.survey-ai-group(v-show="activeTab === 'ai'")
      label Encuesta IA del evento
      p.field-hint Al sugerir preguntas, la IA siempre usa el contenido de la presentación del evento (comportamiento global, no configurable aquí). Este campo es solo texto adicional que quieras que también considere y que puede no estar explícito en las diapositivas: objetivos del examen, temas a enfatizar, terminología esperada.
      .coord-field
        span.coord-label Contexto adicional para sugerir preguntas
        textarea(v-model="surveyExtraContext" rows="5" maxlength="4000" placeholder="Por ejemplo: enfatizar seguridad y buenas prácticas, o los temas del capítulo 3 del material del curso")
      button.btn-outline(type="button" @click="saveSurveyAiConfig" :disabled="savingSurveyAiConfig")
        span(v-if="savingSurveyAiConfig") Guardando...
        span(v-else) Guardar contexto de Encuesta IA
      p.success(v-if="surveyAiConfigSaved") Contexto de Encuesta IA guardado.
      p.error(v-if="surveyAiConfigError") {{ surveyAiConfigError }}

    .form-group.egress-policy-group(v-show="activeTab === 'network'")
      label Control de red del evento (egress)
      p.field-hint Dominios adicionales que el IDE de ESTE evento puede alcanzar, más allá de la
        |  lista global de la plataforma (se suman, nunca la reemplazan). La lista negra, tanto la
        |  global como la de aquí, siempre gana sobre cualquier lista blanca.
      .coord-field
        span.coord-label Lista blanca adicional (permitidos)
        textarea(v-model="egressAllowedHosts" rows="5" placeholder="un-dominio-extra.com&#10;*.otro-dominio.org")
      .coord-field
        span.coord-label Lista negra adicional (bloqueados)
        textarea(v-model="egressBlockedHosts" rows="3" placeholder="dominio-a-bloquear.com")
      button.btn-outline(type="button" @click="saveEgressPolicy" :disabled="savingEgressPolicy")
        span(v-if="savingEgressPolicy") Guardando...
        span(v-else) Guardar control de red
      p.success(v-if="egressPolicySaved") Control de red del evento guardado.
      p.error(v-if="egressPolicyError") {{ egressPolicyError }}
</template>

<script lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import {
  getConference, setSeatingMode, setTicketSalesEnabled, getActiveEventTypes, setEventType,
  getEventRoles, getActiveRoles, assignEventRole, removeEventRole, setSandboxConfig, setSandboxInternet,
  listSandboxIncidents, listSandboxStatus, prewarmSandboxPool as prewarmSandboxPoolApi, deleteSandbox as deleteSandboxApi,
  recreateSandbox as recreateSandboxApi, setDeviceAccessConfig, setCanvasConfigs, setCertificateEngine,
  getCertificateEngine, getConferenceEgressPolicy, setConferenceEgressPolicy
} from '@/services/api/usersApi'
import { getAiMentorConfig, setAiMentorConfig, getAiSurveyConfig, setAiSurveyConfig } from '@/services/api/surveyApi'
import type { Conference, SeatingMode, EventType, EventRoleAssignment, Role, SandboxIncident, SandboxStatusEntry, SandboxPrewarmResult, CanvasTool, CanvasAudienceMode, CanvasToolConfig, CertificateEngine } from '@/services/api/types'
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

    const activeTab = ref<'general' | 'tools' | 'sandbox' | 'access' | 'ai' | 'network'>('general')
    const seatingMode  = ref<SeatingMode>('NONE')
    const capacity     = ref<number | null>(null)
    const recommendedMaxCapacity = RECOMMENDED_MAX_CAPACITY
    const capacityAlert = computed(() => capacityWarning(capacity.value))
    const savingSeating = ref(false)
    const seatingSaved  = ref(false)
    const seatingError  = ref('')
    const ticketSalesEnabled = ref(true)
    const savingTicketSales = ref(false)
    const ticketSalesSaved = ref(false)
    const ticketSalesError = ref('')
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
    // backend (70Mi, ver KubernetesPodClient). El backend rechaza (400) valores que excedan el
    // limite de memoria del contenedor configurado en el chart de despliegue -- no se valida ese
    // techo exacto aca en el frontend porque depende de infra (values.yaml), no de este repo.
    const sandboxJvmHeapMb = ref<number | null>(70)
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
    const prewarmingSandboxPool = ref(false)
    const sandboxPrewarmResult = ref<SandboxPrewarmResult | null>(null)
    const sandboxPrewarmError = ref('')
    const sandboxActionBusy = ref<string | null>(null)
    const sandboxActionError = ref('')
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
    const certificateEngine = ref<CertificateEngine>('INHOUSE')
    const savingCertificateEngine = ref(false)
    const certificateEngineSaved = ref(false)
    const certificateEngineError = ref('')
    const canvasTools = ref<CanvasTool[]>([])
    const canvasModes = reactive<Record<CanvasTool, CanvasAudienceMode>>({
      DRAWIO: 'INDEPENDENT', EXCALIDRAW: 'INDEPENDENT', ETHERPAD: 'COLLABORATIVE'
    })
    const savingCanvasConfig = ref(false)
    const canvasConfigSaved = ref(false)
    const canvasConfigError = ref('')
    const eventRoles      = ref<EventRoleAssignment[]>([])
    const assignableRoles = ref<Role[]>([])
    const canManageRoles  = ref(false)
    const assignIdentifier = ref('')
    const assignRoleKey   = ref('')
    const assigning       = ref(false)
    const roleAssigned    = ref(false)
    const roleError       = ref('')
    const mentorEnabled = ref(false)
    const mentorObjective = ref('')
    const mentorPrompt = ref('')
    const mentorIncludePresentation = ref(true)
    const mentorMaxRequests = ref(8)
    const savingMentor = ref(false)
    const mentorSaved = ref(false)
    const mentorError = ref('')
    const surveyExtraContext = ref('')
    const savingSurveyAiConfig = ref(false)
    const surveyAiConfigSaved = ref(false)
    const surveyAiConfigError = ref('')
    const egressAllowedHosts = ref('')
    const egressBlockedHosts = ref('')
    const savingEgressPolicy = ref(false)
    const egressPolicySaved = ref(false)
    const egressPolicyError = ref('')

    function csvToLines(csv: string | null): string {
      if (!csv) return ''
      return csv.split(',').map((h) => h.trim()).filter(Boolean).join('\n')
    }

    function linesToCsv(lines: string): string {
      return lines.split(/[\n,]/).map((h) => h.trim()).filter(Boolean).join(',')
    }

    async function loadEgressPolicy() {
      try {
        const policy = await getConferenceEgressPolicy(props.conferenceId as string, auth.state.token as string)
        egressAllowedHosts.value = csvToLines(policy.allowedHosts)
        egressBlockedHosts.value = csvToLines(policy.blockedHosts)
      } catch (e: any) {
        egressPolicyError.value = e.response?.data?.error?.message || 'No se pudo cargar el control de red del evento'
      }
    }

    async function saveEgressPolicy() {
      savingEgressPolicy.value = true; egressPolicySaved.value = false; egressPolicyError.value = ''
      try {
        const policy = await setConferenceEgressPolicy(
          props.conferenceId as string, linesToCsv(egressAllowedHosts.value), linesToCsv(egressBlockedHosts.value),
          auth.state.token as string
        )
        egressAllowedHosts.value = csvToLines(policy.allowedHosts)
        egressBlockedHosts.value = csvToLines(policy.blockedHosts)
        egressPolicySaved.value = true
      } catch (e: any) {
        egressPolicyError.value = e.response?.data?.error?.message || 'No se pudo guardar el control de red del evento'
      } finally {
        savingEgressPolicy.value = false
      }
    }

    async function loadSurveyAiConfig() {
      try {
        const result = await getAiSurveyConfig(props.conferenceId as string, auth.state.token as string)
        surveyExtraContext.value = result.data.extraContext || ''
      } catch (e: any) {
        surveyAiConfigError.value = e.response?.data?.error?.message || 'No se pudo cargar el contexto de Encuesta IA'
      }
    }

    async function saveSurveyAiConfig() {
      savingSurveyAiConfig.value = true; surveyAiConfigSaved.value = false; surveyAiConfigError.value = ''
      try {
        const result = await setAiSurveyConfig(props.conferenceId as string, surveyExtraContext.value, auth.state.token as string)
        surveyExtraContext.value = result.data.extraContext || ''
        surveyAiConfigSaved.value = true
      } catch (e: any) {
        surveyAiConfigError.value = e.response?.data?.error?.message || 'No se pudo guardar el contexto de Encuesta IA'
      } finally {
        savingSurveyAiConfig.value = false
      }
    }

    async function loadMentor() {
      try {
        const result = await getAiMentorConfig(props.conferenceId as string, auth.state.token as string)
        const mentor = result.data
        mentorEnabled.value = mentor.enabled
        mentorObjective.value = mentor.objective || ''
        mentorPrompt.value = mentor.prompt || ''
        mentorIncludePresentation.value = mentor.includePresentation !== false
        mentorMaxRequests.value = mentor.maxRequestsPerMinute || 8
      } catch (e: any) {
        mentorError.value = e.response?.data?.error?.message || 'No se pudo cargar la configuración pedagógica del evento'
      }
    }

    async function saveMentor() {
      savingMentor.value = true; mentorSaved.value = false; mentorError.value = ''
      try {
        const result = await setAiMentorConfig(props.conferenceId as string, {
          enabled: mentorEnabled.value,
          objective: mentorObjective.value,
          prompt: mentorPrompt.value,
          includePresentation: mentorIncludePresentation.value,
          maxRequestsPerMinute: mentorMaxRequests.value
        }, auth.state.token as string)
        const mentor = result.data
        mentorEnabled.value = mentor.enabled
        mentorObjective.value = mentor.objective || ''
        mentorPrompt.value = mentor.prompt || ''
        mentorIncludePresentation.value = mentor.includePresentation
        mentorMaxRequests.value = mentor.maxRequestsPerMinute
        mentorSaved.value = true
      } catch (e: any) {
        mentorError.value = e.response?.data?.error?.message || 'No se pudo guardar la configuración pedagógica del evento'
      } finally {
        savingMentor.value = false
      }
    }

    onMounted(async () => {
      try {
        const [conf, types] = await Promise.all([
          getConference(props.conferenceId as string, auth.state.token as string),
          getActiveEventTypes()
        ])
        conference.value = conf
        eventTypes.value = types
        eventTypeKey.value = conference.value.eventTypeKey || 'conference'
        certificateEngine.value = conference.value.certificateEngine || 'INHOUSE'
        seatingMode.value = (conference.value.seatingMode as SeatingMode) || 'NONE'
        capacity.value = conference.value.capacity ?? null
        ticketSalesEnabled.value = conference.value.ticketSalesEnabled !== false
        sandboxVariant.value = conference.value.sandboxVariant === 'terminal-nvim' ? 'terminal-nvim' : ''
        sandboxPoolSize.value = conference.value.sandboxPoolSize ?? 1
        sandboxCliPoolSize.value = conference.value.sandboxCliPoolSize ?? 1
        sandboxExtraPackages.value = conference.value.sandboxExtraPackages || ''
        sandboxRemoteGitUrl.value = conference.value.sandboxRemoteGitUrl || ''
        sandboxJvmHeapMb.value = conference.value.sandboxJvmHeapMb ?? 70
        sandboxSeatsPerPod.value = conference.value.sandboxSeatsPerPod ?? null
        sandboxInternetEnabled.value = conference.value.sandboxInternetEnabled === 1
        maxDevicesPerUser.value = conference.value.maxDevicesPerUser ?? null
        maxAccountsPerDevice.value = conference.value.maxAccountsPerDevice ?? null
        const configured = conference.value.canvasConfigs || []
        if (configured.length > 0) {
          canvasTools.value = configured.map(c => c.tool)
          configured.forEach(c => { canvasModes[c.tool] = normalizeCanvasMode(c.tool, c.audienceMode) })
        } else if (conference.value.canvasTool) {
          // Fallback para eventos guardados con el contrato anterior.
          canvasTools.value = [conference.value.canvasTool]
          canvasModes[conference.value.canvasTool] = normalizeCanvasMode(
            conference.value.canvasTool, conference.value.canvasAudienceMode)
        } else {
          canvasTools.value = []
        }
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

      await loadMentor()
      await loadSurveyAiConfig()
      await loadEgressPolicy()
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

    async function saveTicketSales() {
      savingTicketSales.value = true; ticketSalesError.value = ''; ticketSalesSaved.value = false
      try {
        conference.value = await setTicketSalesEnabled(
          props.conferenceId as string, ticketSalesEnabled.value, auth.state.token as string
        )
        ticketSalesEnabled.value = conference.value.ticketSalesEnabled !== false
        ticketSalesSaved.value = true
      } catch (e: any) {
        ticketSalesError.value = e.response?.data?.error?.message || 'No se pudo actualizar la disponibilidad de boletos'
      } finally {
        savingTicketSales.value = false
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

    async function prewarmSandboxPool() {
      prewarmingSandboxPool.value = true
      sandboxPrewarmError.value = ''
      sandboxPrewarmResult.value = null
      try {
        sandboxPrewarmResult.value = await prewarmSandboxPoolApi(
          props.conferenceId as string, auth.state.token as string)
        await loadSandboxStatus()
      } catch (e: any) {
        sandboxPrewarmError.value = e.response?.data?.error?.message || 'No se pudo preparar el pool de sandboxes'
      } finally {
        prewarmingSandboxPool.value = false
      }
    }

    function sandboxIsFree(pod: SandboxStatusEntry): boolean {
      return pod.seats.length === 0 || pod.seats.every((seat) => !seat.userUuid)
    }

    async function deleteSandbox(pod: SandboxStatusEntry) {
      const occupied = !sandboxIsFree(pod)
      const warning = occupied
        ? `El sandbox ${pod.podName} tiene usuarios asignados. Se cerrarán sus sesiones y se perderá cualquier trabajo no guardado. ¿Eliminarlo de todos modos?`
        : `¿Eliminar el sandbox ${pod.podName}? Se podrá crear nuevamente después.`
      if (!window.confirm(warning)) return
      sandboxActionBusy.value = pod.podName
      sandboxActionError.value = ''
      try {
        await deleteSandboxApi(props.conferenceId as string, pod.sandboxUuid, auth.state.token as string)
        await loadSandboxStatus()
      } catch (e: any) {
        sandboxActionError.value = e.response?.data?.error?.message || 'No se pudo eliminar el sandbox'
      } finally {
        sandboxActionBusy.value = null
      }
    }

    async function recreateSandbox(pod: SandboxStatusEntry) {
      if (!sandboxIsFree(pod)) return
      if (!window.confirm(`¿Recrear el sandbox ${pod.podName}? Esto aplica la imagen y configuración actuales.`)) return
      sandboxActionBusy.value = pod.podName
      sandboxActionError.value = ''
      try {
        await recreateSandboxApi(props.conferenceId as string, pod.sandboxUuid, auth.state.token as string)
        await loadSandboxStatus()
      } catch (e: any) {
        sandboxActionError.value = e.response?.data?.error?.message || 'No se pudo recrear el sandbox'
      } finally {
        sandboxActionBusy.value = null
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

    async function saveCertificateEngine() {
      savingCertificateEngine.value = true; certificateEngineError.value = ''; certificateEngineSaved.value = false
      try {
        conference.value = await setCertificateEngine(
          props.conferenceId as string, certificateEngine.value, auth.state.token as string)
        // Verificar la lectura real después del PUT. Así la UI no muestra "guardado" si un
        // gateway o una versión antigua del servicio aceptó la petición pero no persistió la
        // columna certificate_engine.
        const persistedEngine = await getCertificateEngine(
          props.conferenceId as string, auth.state.token as string)
        if (persistedEngine !== certificateEngine.value) {
          throw new Error('El motor devuelto por el servidor no coincide con la selección')
        }
        conference.value.certificateEngine = persistedEngine
        certificateEngineSaved.value = true
      } catch (e: any) {
        certificateEngineError.value = e.response?.data?.error?.message || 'No se pudo guardar el motor de certificado'
      } finally {
        savingCertificateEngine.value = false
      }
    }

    async function saveCanvasConfig() {
      savingCanvasConfig.value = true; canvasConfigError.value = ''; canvasConfigSaved.value = false
      try {
        conference.value = await setCanvasConfigs(
          props.conferenceId as string,
          canvasTools.value.map((tool): CanvasToolConfig => ({ tool, audienceMode: canvasModes[tool] })),
          auth.state.token as string
        )
        canvasConfigSaved.value = true
      } catch (e: any) {
        canvasConfigError.value = e.response?.data?.error?.message || 'No se pudo guardar la configuración del lienzo'
      } finally {
        savingCanvasConfig.value = false
      }
    }

    const breadcrumbItems = computed(() => [
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Eventos', to: '/dashboard/conferences' },
      { label: conference.value?.name || props.conferenceId || '', loading: loading.value && !conference.value },
      { label: 'Configuración' }
    ])

    function canvasToolLabel(tool: CanvasTool): string {
      return { DRAWIO: 'Drawio', EXCALIDRAW: 'Excalidraw', ETHERPAD: 'Etherpad' }[tool]
    }

    function canvasModeOptions(tool: CanvasTool): Array<{ value: CanvasAudienceMode; label: string }> {
      if (tool === 'ETHERPAD') {
        return [
          { value: 'COLLABORATIVE', label: 'Notas grupales (todos colaboran)' },
          { value: 'INDEPENDENT', label: 'Notas individuales (se borran al vencer el evento)' }
        ]
      }
      return [
        { value: 'INDEPENDENT', label: 'Trabajo independiente (solo persiste el moderador)' },
        { value: 'MODERATOR_ONLY', label: 'Solo el moderador edita; asistentes ven la publicación' }
      ]
    }

    function normalizeCanvasMode(tool: CanvasTool, mode: CanvasAudienceMode | null | undefined): CanvasAudienceMode {
      if (tool === 'ETHERPAD') return mode === 'INDEPENDENT' ? 'INDEPENDENT' : 'COLLABORATIVE'
      return mode === 'MODERATOR_ONLY' ? 'MODERATOR_ONLY' : 'INDEPENDENT'
    }

    return { conference, loading, error, activeTab,
             seatingMode, capacity, recommendedMaxCapacity, capacityAlert, savingSeating, seatingSaved, seatingError, saveSeating,
             ticketSalesEnabled, savingTicketSales, ticketSalesSaved, ticketSalesError, saveTicketSales,
             sandboxVariant, sandboxPoolSize, sandboxCliPoolSize, cliEnabled,
             sandboxExtraPackages, sandboxRemoteGitUrl, sandboxJvmHeapMb,
             sandboxSeatsPerPod, sandboxInternetEnabled,
             savingSandboxConfig, sandboxConfigSaved, sandboxConfigError, savingSandboxInternet,
             saveSandboxConfig, saveSandboxInternet,
             sandboxIncidents, sandboxIncidentsLoaded, loadingSandboxIncidents, sandboxIncidentsError,
             loadSandboxIncidents, incidentTypeLabel,
             sandboxStatus, sandboxStatusLoaded, loadingSandboxStatus, sandboxStatusError, loadSandboxStatus,
             prewarmingSandboxPool, sandboxPrewarmResult, sandboxPrewarmError, prewarmSandboxPool,
             sandboxActionBusy, sandboxActionError, sandboxIsFree, deleteSandbox, recreateSandbox,
             maxDevicesPerUser, maxAccountsPerDevice, savingDeviceAccessConfig,
             deviceAccessConfigSaved, deviceAccessConfigError, saveDeviceAccessConfig,
             eventTypes, eventTypeKey, savingEventType, eventTypeSaved, eventTypeError, saveEventType,
             certificateEngine, savingCertificateEngine, certificateEngineSaved, certificateEngineError, saveCertificateEngine,
             canvasTools, canvasModes, canvasToolLabel, canvasModeOptions,
             canvasToolOptions: [
               { value: 'DRAWIO', label: 'Drawio (diagramas)' },
               { value: 'EXCALIDRAW', label: 'Excalidraw (pizarra)' },
               { value: 'ETHERPAD', label: 'Etherpad (notas)' }
             ] as Array<{ value: CanvasTool; label: string }>,
             savingCanvasConfig, canvasConfigSaved, canvasConfigError, saveCanvasConfig,
             eventRoles, assignableRoles, canManageRoles, assignIdentifier, assignRoleKey, assigning,
             roleAssigned, roleError, roleName, assignRole, removeRole, breadcrumbItems,
             mentorEnabled, mentorObjective, mentorPrompt, mentorIncludePresentation, mentorMaxRequests,
             savingMentor, mentorSaved, mentorError, saveMentor,
             surveyExtraContext, savingSurveyAiConfig, surveyAiConfigSaved, surveyAiConfigError, saveSurveyAiConfig,
             egressAllowedHosts, egressBlockedHosts, savingEgressPolicy, egressPolicySaved, egressPolicyError,
             saveEgressPolicy }
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
.config-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin: 0 0 20px; padding-bottom: 10px; border-bottom: 1px solid #e5e7eb; }
.config-tab { padding: 8px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; background: #fff; color: #4b5563; cursor: pointer; font-size: 0.88rem; font-weight: 600; }
.config-tab:hover { border-color: #a5b4fc; color: #4f46e5; }
.config-tab.active { background: #4f46e5; border-color: #4f46e5; color: #fff; }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: #374151; }
input[type="text"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem;
}
textarea {
  width: 100%; box-sizing: border-box; padding: 10px 14px; border: 1.5px solid #d1d5db;
  border-radius: 8px; font: inherit; resize: vertical; min-height: 80px;
}
input:focus { outline: none; border-color: #4f46e5; }
textarea:focus { outline: none; border-color: #4f46e5; }
.toggle-row { display: flex; align-items: center; gap: 10px; font-weight: 500; cursor: pointer; margin-top: 4px; }
.toggle-row input { width: auto; }

.field-hint { margin: 4px 0 0; font-size: 0.8rem; color: #9ca3af; }
.canvas-tools { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1.5px solid #d1d5db; border-radius: 8px; background: #fff; }
.canvas-tool-option { display: flex; align-items: center; gap: 8px; font-weight: 500; cursor: pointer; }
.canvas-tool-option input { width: auto; }
.canvas-mode-row { display: flex; flex-direction: column; gap: 4px; margin-top: 4px; }
.canvas-mode-label { font-size: 0.8rem; color: #6b7280; font-weight: 600; }

.tickets-group select, .canvas-group select { padding: 10px 14px; border: 1.5px solid #d1d5db; border-radius: 8px; font-size: 1rem; margin-bottom: 10px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
.ticket-links { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 4px; }

.capacity-alert { margin: 6px 0 0; font-size: 0.82rem; font-weight: 600; padding: 6px 10px; border-radius: 6px; }
.capacity-alert.warning { background: #fef3c7; color: #92400e; }
.capacity-alert.risk { background: #ffedd5; color: #9a3412; }
.capacity-alert.critical { background: #fee2e2; color: #991b1b; }

.sandbox-status { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb; }
.prewarm-control { margin-top: 10px; padding: 10px 12px; border: 1px solid #e0e7ff; border-radius: 8px; background: #f8faff; }
.sandbox-incidents { margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb; }
.incidents-table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 0.82rem; }
.incidents-table th { text-align: left; padding: 6px 10px; background: #f9fafb; color: #6b7280; font-weight: 600; }
.incidents-table td { padding: 6px 10px; border-top: 1px solid #f3f4f6; color: #374151; }
.sandbox-actions { white-space: nowrap; }
.btn-small { padding: 4px 8px; border: 1px solid #fecaca; border-radius: 6px; background: #fff; color: #b91c1c; cursor: pointer; font-size: 0.75rem; margin-right: 4px; }
.btn-small:disabled { opacity: 0.55; cursor: wait; }
.btn-small.btn-recreate { border-color: #c7d2fe; color: #4338ca; }
.action-note { color: #9ca3af; font-size: 0.75rem; }

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
