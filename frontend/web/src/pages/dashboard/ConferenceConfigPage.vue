<template lang="pug">
.conf-config-page
  DashboardBreadcrumb(:items="breadcrumbItems")

  h2 Configuración del evento
  SaveState(:state="saveState")

  ConferenceToolsNav(:conferenceId="conferenceId")

  nav.config-tabs(v-if="!loading && !error" role="tablist" aria-label="Secciones de configuración")
    button.config-tab(
      v-for="tab in configTabs"
      :id="`config-tab-${tab.id}`"
      :key="tab.id"
      type="button"
      role="tab"
      :aria-selected="activeTab === tab.id"
      aria-controls="config-tabpanel"
      :tabindex="activeTab === tab.id ? 0 : -1"
      :class="{ active: activeTab === tab.id }"
      @click="selectTab(tab.id)"
      @keydown.left.prevent="focusTab(-1)"
      @keydown.up.prevent="focusTab(-1)"
      @keydown.right.prevent="focusTab(1)"
      @keydown.down.prevent="focusTab(1)"
      @keydown.home.prevent="focusTab(-999)"
      @keydown.end.prevent="focusTab(999)"
    ) {{ tab.label }}

  LoadingState(v-if="loading" message="Cargando conferencia…")
  FeedbackMessage(v-else-if="error" :message="error" tone="error")
  .form(v-else id="config-tabpanel" role="tabpanel" :aria-labelledby="`config-tab-${activeTab}`" tabindex="0" @input.capture="markFormDirty" @change.capture="markFormDirty")
    .form-group.general-group(v-if="eventTypes.length" v-show="activeTab === 'general'")
      label Tipo de evento
      select#config-event-type(v-model="eventTypeKey")
        option(v-for="t in eventTypes" :key="t.key" :value="t.key") {{ t.name }}
      p.field-hint Determina qué herramientas están disponibles (boletos, encuestas, videollamada...).
      BaseButton(variant="secondary" type="button" @click="saveEventType" :disabled="savingEventType")
        span(v-if="savingEventType") Guardando...
        span(v-else) Guardar tipo de evento
      FeedbackMessage(v-if="eventTypeSaved" message="Tipo de evento actualizado." tone="success")
      FeedbackMessage(v-if="eventTypeError" :message="eventTypeError" tone="error")

    .form-group.certificate-engine-group(v-show="activeTab === 'tools'")
      label Motor de certificado
      select#config-certificate-engine(v-model="certificateEngine")
        option(value="INHOUSE") Clásico (editor simple)
        option(value="HTML_CHROME") Visual (editor de diseños)
      p.field-hint El motor Clásico usa el editor simple y la configuración global como respaldo. El Visual usa el editor de diseños del evento y su catálogo de plantillas.
      BaseButton(variant="secondary" type="button" @click="saveCertificateEngine" :disabled="savingCertificateEngine")
        span(v-if="savingCertificateEngine") Guardando...
        span(v-else) Guardar motor de certificado
      FeedbackMessage(v-if="certificateEngineSaved" message="Motor de certificado actualizado." tone="success")
      FeedbackMessage(v-if="certificateEngineError" :message="certificateEngineError" tone="error")

    .form-group.canvas-group(v-show="activeTab === 'tools'")
      label Lienzo del evento
      p.field-hint Selecciona una o varias herramientas y define el modo de cada una. Si no seleccionas ninguna, se mantiene el modo legado del tipo de evento.
      .event-canvas-tools
        label.event-canvas-tool-option(v-for="tool in canvasToolOptions" :key="tool.value")
          input(type="checkbox" :value="tool.value" v-model="canvasTools")
          span {{ tool.label }}
      .event-canvas-mode-row(v-for="tool in canvasTools" :key="tool")
        label.event-canvas-mode-label(:for="`config-canvas-mode-${tool}`") {{ canvasToolLabel(tool) }}
        select(:id="`config-canvas-mode-${tool}`" v-model="canvasModes[tool]")
          option(v-for="option in canvasModeOptions(tool)" :key="option.value" :value="option.value") {{ option.label }}
      p.field-hint(v-if="canvasTools.includes('ETHERPAD')") Etherpad sólo admite notas grupales (todos colaboran) o notas individuales (un pad privado por asistente); no tiene modo de publicación exclusiva del moderador. Las notas individuales se borran al vencer el evento y se pueden exportar.
      BaseButton(variant="secondary" type="button" @click="saveCanvasConfig" :disabled="savingCanvasConfig")
        span(v-if="savingCanvasConfig") Guardando...
        span(v-else) Guardar configuración del lienzo
      FeedbackMessage(v-if="canvasConfigSaved" message="Configuración del lienzo guardada." tone="success")
      FeedbackMessage(v-if="canvasConfigError" :message="canvasConfigError" tone="error")

    .form-group.tickets-group(v-show="activeTab === 'access'")
      label Boletos y aforo
      p.field-hint Elige cómo se registran los asistentes: sin control (solo unirse), con aforo, o con mapa de asientos.
      select#config-seating-mode(v-model="seatingMode")
        option(value="NONE") Ninguno (solo unirse)
        option(value="GENERAL") Aforo (cupo limitado, sin asiento)
        option(value="SEATED") Con asientos (mapa del recinto)
      .coord-field
        label.coord-label(for="config-capacity") Aforo máximo
        input#config-capacity(v-model.number="capacity" type="number" min="2" placeholder="10")
      p.field-hint Cuántas personas van a tener acceso al evento y sus herramientas (IDE, encuestas...), sin importar el modo de boletos elegido — la infraestructura tiene recursos limitados. El mínimo es 2 porque el creador ocupa un boleto operativo contado. Cada moderador adicional ocupa otra plaza. Recomendado hasta {{ recommendedMaxCapacity }}.
      p.capacity-alert(v-if="capacityAlert" :class="capacityAlert.level") {{ capacityAlert.text }}
      BaseButton(variant="secondary" type="button" @click="saveSeating" :disabled="savingSeating")
        span(v-if="savingSeating") Guardando...
        span(v-else) Guardar configuración de boletos
      FeedbackMessage(v-if="seatingSaved" message="Configuración de boletos guardada." tone="success")
      FeedbackMessage(v-if="seatingError" :message="seatingError" tone="error")
      ToggleSwitch(v-model="ticketSalesEnabled") Permitir adquisición de boletos desde la cartelera pública
      p.field-hint El evento puede seguir activo aunque cierres la adquisición pública. Los boletos ya emitidos conservan su acceso y puedes seguir gestionándolos o emitiéndolos manualmente desde “Administrar boletos”.
      BaseButton(variant="secondary" type="button" @click="saveTicketSales" :disabled="savingTicketSales")
        span(v-if="savingTicketSales") Guardando...
        span(v-else) Guardar disponibilidad de boletos
      FeedbackMessage(v-if="ticketSalesSaved" message="Disponibilidad de boletos actualizada." tone="success")
      FeedbackMessage(v-if="ticketSalesError" :message="ticketSalesError" tone="error")
      .ticket-links(v-if="seatingMode !== 'NONE' || eventTypes.find(t => t.key === eventTypeKey)?.capabilities.some(c => c.startsWith('TICKETING_'))")
        BaseLink(variant="secondary" :to="`/dashboard/events/${conferenceId}/tickets`") Administrar boletos
        BaseLink(variant="secondary" :to="`/dashboard/events/${conferenceId}/check-in`") Ir al check-in
        BaseLink(variant="secondary" v-if="seatingMode === 'SEATED'" :to="`/dashboard/events/${conferenceId}/venue-map`") Editar mapa de asientos

    .form-group.sandbox-group(v-show="activeTab === 'sandbox'")
      label IDE de código
      p.field-hint Configura el ambiente de desarrollo que reciben los asistentes en la pestaña "IDE". El ambiente incluye Java, Node.js y Python en el mismo sandbox. Los alumnos eligen Web, CLI con Neovim estable o CLI con LazyVim; cada opción tiene su propio pool y conserva su workspace separado.
      .coord-field
        label.coord-label(for="config-sandbox-web-pool") Sandboxes Web concurrentes (editor en el navegador)
        input#config-sandbox-web-pool(v-model.number="sandboxPoolSize" type="number" min="1" placeholder="1")
      .coord-field
        label.coord-label(for="config-sandbox-cli-pool") Sandboxes CLI concurrentes (Neovim estable)
        input#config-sandbox-cli-pool(v-model.number="sandboxCliPoolSize" type="number" min="1" placeholder="1")
      .coord-field
        label.coord-label(for="config-sandbox-cli-lazyvim-pool") Sandboxes CLI concurrentes (LazyVim)
        input#config-sandbox-cli-lazyvim-pool(v-model.number="sandboxCliLazyVimPoolSize" type="number" min="1" placeholder="1")
      .coord-field(v-if="cliEnabled")
        label.coord-label(for="config-sandbox-cli-seats") Alumnos por sandbox CLI
        input#config-sandbox-cli-seats(v-model.number="sandboxSeatsPerPod" type="number" min="1" max="10" placeholder="4 (por defecto)")
      p.field-hint(v-if="cliEnabled") En cada variante CLI, varios alumnos pueden compartir el mismo sandbox — cada uno con su propio usuario y espacio de trabajo aislado. El modo Web no admite esto: siempre es un sandbox por alumno.
      .coord-field
        label.coord-label(for="config-sandbox-git") Repositorio git remoto (opcional)
        input#config-sandbox-git(v-model="sandboxRemoteGitUrl" type="text" placeholder="https://github.com/...")
      p.field-hint Si lo indicás, se clona automáticamente en el workspace de cada alumno al arrancar su sandbox (solo si el workspace está vacío — no pisa trabajo ya en progreso).
      .coord-field
        label.coord-label(for="config-sandbox-jvm-heap") Memoria máxima de Java por sandbox (MB, opcional)
        input#config-sandbox-jvm-heap(v-model.number="sandboxJvmHeapMb" type="number" min="64" placeholder="70 (por defecto)")
      p.field-hint Cuánta memoria puede usar cada programa de Java que corran los asistentes (incluido el autocompletado del editor). El valor por defecto (70 MB) está pensado para cursos: alcanza para ejercicios y no acapara el sandbox. Si ponés un valor mayor al que soporta la infraestructura, el servidor rechaza el guardado y te lo indica.
      BaseButton(variant="secondary" type="button" @click="saveSandboxConfig" :disabled="savingSandboxConfig")
        span(v-if="savingSandboxConfig") Guardando...
        span(v-else) Guardar configuración del IDE
      FeedbackMessage(v-if="sandboxConfigSaved" message="Configuración del IDE guardada." tone="success")
      FeedbackMessage(v-if="sandboxConfigError" :message="sandboxConfigError" tone="error")
      ToggleSwitch(v-model="sandboxInternetEnabled" :disabled="savingSandboxInternet" @update:modelValue="saveSandboxInternet") Permitir acceso a internet desde los sandboxes
      p.field-hint Por defecto los sandboxes no tienen salida de red. Al activarlo, solo pueden salir mediante la proxy interna hacia los hosts de la lista blanca definida por la plataforma; la lista negra siempre tiene prioridad.

      .sandbox-status
        .coord-field
          span.coord-label Estado de las máquinas
          BaseButton(variant="secondary" size="sm" type="button" @click="loadSandboxStatus" :disabled="loadingSandboxStatus" :loading="loadingSandboxStatus") Ver estado de sandboxes
        .prewarm-control
          BaseButton(variant="secondary" size="sm" type="button" @click="prewarmSandboxPool" :disabled="prewarmingSandboxPool")
            span(v-if="prewarmingSandboxPool") Preparando...
            span(v-else) Preparar sandboxes antes del evento
          p.field-hint Crea por adelantado los sandboxes Web y CLI configurados, pero no los asigna a ningún alumno. Quedan listos para reclamarse cuando entren los asistentes.
        FeedbackMessage(v-if="sandboxPrewarmResult" :message="sandboxPrewarmMessage" tone="success")
        FeedbackMessage(v-if="sandboxPrewarmError" :message="sandboxPrewarmError" tone="error")
        p.field-hint Sandboxes: {{ sandboxStatus.filter(p => p.ready).length }} listos, {{ sandboxStatus.filter(p => !p.ready).length }} pendientes, {{ sandboxStatus.filter(p => !p.seats.some(s => s.userUuid)).length }} libres. Para revisar y editar los archivos de un alumno, usá el "Editor de código" en Moderación.
        FeedbackMessage(v-if="sandboxStatusError" :message="sandboxStatusError" tone="error")
        LoadingState(v-if="loadingSandboxStatus" message="Cargando estado de los sandboxes…")
        EmptyState(v-else-if="sandboxStatusLoaded && !sandboxStatus.length" message="No hay sandboxes activos.")
        .table-scroll(v-else-if="sandboxStatusLoaded")
          table.incidents-table.sandbox-status-table
            thead
              tr
                th Pod
                th Modo
                th Fase
                th Listo
                th Diagnóstico
                th Asientos
                th Acciones
            tbody
              tr(v-for="pod in sandboxStatus" :key="pod.podName")
                td(data-label="Pod") {{ pod.podName }}
                td(data-label="Modo") {{ pod.variant === 'cli-lazyvim' ? 'CLI · LazyVim' : (pod.variant === 'cli' ? 'CLI · Neovim' : 'Web') }}
                td(data-label="Fase")
                  StatusBadge(:status="pod.phase" pill)
                td(data-label="Listo")
                  StatusBadge(:status="pod.ready ? 'READY' : 'NOT_READY'" pill)
                td(data-label="Diagnóstico") {{ pod.reason || '—' }}{{ pod.restartCount ? ` (${pod.restartCount} reinicios)` : '' }}
                td.seats-cell(data-label="Asientos")
                  span.seat-badge(v-for="seat in pod.seats" :key="seat.seatIndex")
                    span.seat-user(
                      v-if="seat.userUuid"
                      :title="`UUID: ${seat.userUuid}`"
                      :aria-label="`Asiento ${seat.seatIndex + 1}: ${seat.userDisplayName || 'Usuario'}. UUID ${seat.userUuid}`"
                    ) Asiento {{ seat.seatIndex + 1 }}: {{ seat.userDisplayName || 'Usuario' }}
                    span.seat-empty(v-else) Asiento {{ seat.seatIndex + 1 }}: libre
                td.sandbox-actions(data-label="Acciones")
                  .sandbox-actions-group
                    BaseButton.icon-action(variant="danger" size="sm" type="button" @click="deleteSandbox(pod)" :disabled="sandboxActionBusy === pod.podName" :loading="sandboxActionBusy === pod.podName" :aria-label="`Eliminar sandbox ${pod.podName}`" :title="`Eliminar sandbox ${pod.podName}`")
                      UiIcon(v-if="sandboxActionBusy !== pod.podName" name="trash" size="18" aria-hidden="true")
                    template(v-if="sandboxIsFree(pod)")
                      BaseButton.icon-action(variant="secondary" size="sm" type="button" @click="recreateSandbox(pod)" :disabled="sandboxActionBusy === pod.podName" :loading="sandboxActionBusy === pod.podName" :aria-label="`Recrear sandbox ${pod.podName}`" :title="`Recrear sandbox ${pod.podName}`")
                        UiIcon(v-if="sandboxActionBusy !== pod.podName" name="refresh" size="18" aria-hidden="true")
                    span.action-note(v-else) Ocupado: eliminación forzada
        FeedbackMessage(v-if="sandboxActionSuccess" :message="sandboxActionSuccess" tone="success")
        FeedbackMessage(v-if="sandboxActionError" :message="sandboxActionError" tone="error")

      .sandbox-incidents(v-if="cliEnabled")
        .coord-field
          span.coord-label Incidentes de recursos
          BaseButton(variant="secondary" size="sm" type="button" @click="loadSandboxIncidents" :disabled="loadingSandboxIncidents" :loading="loadingSandboxIncidents") Ver incidentes
        p.field-hint Cuando un sandbox compartido detecta que un alumno acapara CPU/memoria (por error o a propósito), lo reinicia automáticamente y lo registra acá — para que sepas quién está causando problemas.
        FeedbackMessage(v-if="sandboxIncidentsError" :message="sandboxIncidentsError" tone="error")
        LoadingState(v-if="loadingSandboxIncidents" message="Cargando incidentes…")
        EmptyState(v-else-if="sandboxIncidentsLoaded && !sandboxIncidents.length" message="Sin incidentes registrados.")
        .table-scroll(v-else-if="sandboxIncidentsLoaded")
          table.incidents-table
            thead
              tr
                th Cuándo
                th Alumno
                th Tipo
                th Detalle
            tbody
              tr(v-for="incident in sandboxIncidents" :key="incident.uuid")
                td {{ new Date(incident.occurredAt).toLocaleString() }}
                td {{ incident.userUuid || '(desconocido)' }}
                td {{ incidentTypeLabel(incident.type) }}
                td {{ incident.detail }}

    .form-group.device-access-group(v-show="activeTab === 'access'")
      label Acceso por dispositivo
      p.field-hint Controla cuántos dispositivos puede usar a la vez un mismo asistente en Videollamada e IDE, y bloquea automáticamente un dispositivo que se loguea con demasiadas cuentas distintas (podés revisar y desbloquear desde "Bloqueos", en Moderación).
      .coord-field
        label.coord-label(for="config-max-devices") Máx. dispositivos activos por usuario
        input#config-max-devices(v-model.number="maxDevicesPerUser" type="number" min="1" max="10" placeholder="2 (por defecto)")
      .coord-field
        label.coord-label(for="config-max-accounts") Máx. cuentas distintas por dispositivo antes de bloquear
        input#config-max-accounts(v-model.number="maxAccountsPerDevice" type="number" min="1" max="50" placeholder="3 (por defecto)")
      BaseButton(variant="secondary" type="button" @click="saveDeviceAccessConfig" :disabled="savingDeviceAccessConfig")
        span(v-if="savingDeviceAccessConfig") Guardando...
        span(v-else) Guardar acceso por dispositivo
      FeedbackMessage(v-if="deviceAccessConfigSaved" message="Configuración de acceso por dispositivo guardada." tone="success")
      FeedbackMessage(v-if="deviceAccessConfigError" :message="deviceAccessConfigError" tone="error")

    .form-group.roles-group(v-if="canManageRoles" v-show="activeTab === 'roles'")
      label Roles del evento
      p.field-hint Asigna moderadores, staff de acceso u otros roles a personas solo para este evento.
      .roles-list(v-if="eventRoles.length")
        .role-row(v-for="r in eventRoles" :key="r.userUuid")
          span.role-person {{ r.displayName || r.email || r.userUuid }}
          span.role-badge {{ roleName(r.roleKey) }}
          BaseButton(variant="danger" size="sm" type="button" @click="removeRole(r.userUuid)") Quitar
      .assign-row
        input#config-role-identifier(v-model="assignIdentifier" type="text" aria-label="Email o usuario" placeholder="Email o usuario")
        select#config-role-key(v-model="assignRoleKey" aria-label="Rol a asignar")
          option(v-for="role in assignableRoles" :key="role.key" :value="role.key") {{ role.name }}
        BaseButton(variant="secondary" size="sm" type="button" @click="assignRole" :disabled="assigning") Asignar
      FeedbackMessage(v-if="roleAssigned" message="Rol asignado." tone="success")
      FeedbackMessage(v-if="roleError" :message="roleError" tone="error")

    .form-group.mentor-group(v-show="activeTab === 'ai'")
      label Tutor IA del evento
      span.scope-badge Configuración de este evento
      p.field-hint Configuración pedagógica exclusiva de este evento. El proveedor, la URL base y la clave del Tutor IA (compartidos por toda la plataforma) se configuran aparte, en #[router-link(to="/dashboard/admin/ai/tutor") IA → Tutor IA] (solo administradores).
      ToggleSwitch(v-model="mentorEnabled" :disabled="savingMentor")
        | {{ mentorEnabled ? 'Tutor habilitado para los asistentes' : 'Tutor deshabilitado para los asistentes' }}
      .coord-field
        label.coord-label(for="config-mentor-objective") Objetivo pedagógico del taller
        textarea#config-mentor-objective(v-model="mentorObjective" rows="4" maxlength="2000" placeholder="Qué deben aprender o construir los asistentes")
      .coord-field
        label.coord-label(for="config-mentor-prompt") Instrucciones adicionales y límites
        textarea#config-mentor-prompt(v-model="mentorPrompt" rows="5" maxlength="8000" placeholder="Por ejemplo: pedir primero qué intentaron y dar una pista a la vez")
      ToggleSwitch(v-model="mentorIncludePresentation" :disabled="savingMentor") Leer la presentación como contexto de consulta
      p.field-hint 🧭 Modo socrático activo: el tutor hará preguntas y dará pistas graduales; no entregará la solución completa.
      .coord-field
        label.coord-label(for="config-mentor-rate") Máximo de consultas por usuario/minuto
        input#config-mentor-rate(v-model.number="mentorMaxRequests" type="number" min="1" max="30" :disabled="savingMentor")
      BaseButton(variant="secondary" type="button" @click="saveMentor" :disabled="savingMentor")
        span(v-if="savingMentor") Guardando...
        span(v-else) Guardar configuración pedagógica del evento
      FeedbackMessage(v-if="mentorSaved" message="Configuración del tutor IA guardada." tone="success")
      FeedbackMessage(v-if="mentorError" :message="mentorError" tone="error")

    .form-group.survey-ai-group(v-show="activeTab === 'ai'")
      label Encuesta IA del evento
      p.field-hint Al sugerir preguntas, la IA siempre usa el contenido de la presentación del evento (comportamiento global, no configurable aquí). Este campo es solo texto adicional que quieras que también considere y que puede no estar explícito en las diapositivas: objetivos del examen, temas a enfatizar, terminología esperada.
      .coord-field
        label.coord-label(for="config-survey-context") Contexto adicional para sugerir preguntas
        textarea#config-survey-context(v-model="surveyExtraContext" rows="5" maxlength="4000" placeholder="Por ejemplo: enfatizar seguridad y buenas prácticas, o los temas del capítulo 3 del material del curso")
      BaseButton(variant="secondary" type="button" @click="saveSurveyAiConfig" :disabled="savingSurveyAiConfig")
        span(v-if="savingSurveyAiConfig") Guardando...
        span(v-else) Guardar contexto de Encuesta IA
      FeedbackMessage(v-if="surveyAiConfigSaved" message="Contexto de Encuesta IA guardado." tone="success")
      FeedbackMessage(v-if="surveyAiConfigError" :message="surveyAiConfigError" tone="error")

    .form-group.egress-policy-group(v-show="activeTab === 'network'")
      label Control de red del evento
      p.field-hint Dominios adicionales que el IDE de ESTE evento puede alcanzar, más allá de la
        |  lista global de la plataforma (se suman, nunca la reemplazan). La lista negra, tanto la
        |  global como la de aquí, siempre gana sobre cualquier lista blanca.
      .coord-field
        label.coord-label(for="config-egress-allowed") Lista blanca adicional (permitidos)
        textarea#config-egress-allowed(v-model="egressAllowedHosts" rows="5" placeholder="un-dominio-extra.com\n*.otro-dominio.org")
      .coord-field
        label.coord-label(for="config-egress-blocked") Lista negra adicional (bloqueados)
        textarea#config-egress-blocked(v-model="egressBlockedHosts" rows="3" placeholder="dominio-a-bloquear.com")
      BaseButton(variant="secondary" type="button" @click="saveEgressPolicy" :disabled="savingEgressPolicy")
        span(v-if="savingEgressPolicy") Guardando...
        span(v-else) Guardar control de red
      FeedbackMessage(v-if="egressPolicySaved" message="Control de red del evento guardado." tone="success")
      FeedbackMessage(v-if="egressPolicyError" :message="egressPolicyError" tone="error")

    .form-group.image-policy-group(v-show="activeTab === 'network'")
      label Imágenes de contenedor permitidas para este evento
      p.field-hint Prefijos adicionales de imágenes que este evento puede usar en el
        |  #[code FROM] de un Containerfile/Dockerfile, más allá de la lista global de la
        |  plataforma (se suman, nunca la reemplazan). La lista negra, tanto la global como la de
        |  aquí, siempre gana sobre cualquier lista blanca.
      .coord-field
        label.coord-label(for="config-image-allowed") Lista blanca adicional (permitidas)
        textarea#config-image-allowed(v-model="imageAllowedImages" rows="5" placeholder="python\nnode")
      .coord-field
        label.coord-label(for="config-image-blocked") Lista negra adicional (bloqueadas)
        textarea#config-image-blocked(v-model="imageBlockedImages" rows="3" placeholder="alpine:edge")
      BaseButton(variant="secondary" type="button" @click="saveImagePolicy" :disabled="savingImagePolicy")
        span(v-if="savingImagePolicy") Guardando...
        span(v-else) Guardar imágenes permitidas
      FeedbackMessage(v-if="imagePolicySaved" message="Política de imágenes del evento guardada." tone="success")
      FeedbackMessage(v-if="imagePolicyError" :message="imagePolicyError" tone="error")

  //- Confirmación de acciones destructivas sobre sandboxes (reemplaza window.confirm: la accion
  //- mas destructiva de la pagina merece un dialogo con consecuencias explicitas y accesible).
  BaseModal(
    v-if="sandboxConfirm"
    :title="sandboxConfirm.action === 'delete' ? '¿Eliminar sandbox?' : '¿Recrear sandbox?'"
    :confirmLabel="sandboxConfirm.action === 'delete' ? 'Eliminar' : 'Recrear'"
    :confirmVariant="sandboxConfirm.action === 'delete' ? 'danger' : 'primary'"
    :loading="sandboxActionBusy === sandboxConfirm.pod.podName"
    @confirm="performSandboxAction"
    @close="sandboxConfirm = null"
  )
    template(v-if="sandboxConfirm.action === 'delete' && sandboxConfirm.occupied")
      p.
        El sandbox #[strong {{ sandboxConfirm.pod.podName }}] tiene
        #[strong {{ sandboxConfirm.occupiedSeats }} {{ sandboxConfirm.occupiedSeats === 1 ? 'persona asignada' : 'personas asignadas' }}].
        Se cerrarán sus sesiones y #[strong se perderá el trabajo no guardado].
    template(v-else-if="sandboxConfirm.action === 'delete'")
      p El sandbox #[strong {{ sandboxConfirm.pod.podName }}] está libre. Se podrá crear nuevamente después.
    template(v-else)
      p Se recreará #[strong {{ sandboxConfirm.pod.podName }}] aplicando la imagen y configuración actuales.

  BaseModal(
    v-if="pendingTab"
    title="¿Cambiar de sección?"
    confirmLabel="Descartar cambios"
    confirmVariant="danger"
    @confirm="confirmTabChange"
    @close="pendingTab = null"
  )
    p Hay cambios sin guardar en esta sección. Si continúas, se descartarán.
</template>

<script lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, reactive, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  getConference, setSeatingMode, setTicketSalesEnabled, getActiveEventTypes, setEventType,
  getEventRoles, getActiveRoles, assignEventRole, removeEventRole, setSandboxConfig, setSandboxInternet,
  listSandboxIncidents, listSandboxStatus, prewarmSandboxPool as prewarmSandboxPoolApi, deleteSandbox as deleteSandboxApi,
  recreateSandbox as recreateSandboxApi, setDeviceAccessConfig, setCanvasConfigs, setCertificateEngine,
  getCertificateEngine, getConferenceEgressPolicy, setConferenceEgressPolicy,
  getConferenceImagePolicy, setConferenceImagePolicy
} from '@/services/api/usersApi'
import { getAiMentorConfig, setAiMentorConfig, getAiSurveyConfig, setAiSurveyConfig } from '@/services/api/surveyApi'
import type { Conference, SeatingMode, EventType, EventRoleAssignment, Role, SandboxIncident, SandboxStatusEntry, SandboxPrewarmResult, CanvasTool, CanvasAudienceMode, CanvasToolConfig, CertificateEngine } from '@/services/api/types'
import { useAuthStore } from '@/features/auth/authStore'
import { capacityWarning, RECOMMENDED_MAX_CAPACITY } from '@/utils/capacityWarning'
import DashboardBreadcrumb from '@/components/DashboardBreadcrumb.vue'
import ConferenceToolsNav from '@/components/ConferenceToolsNav.vue'
import BaseButton from '@/components/ui/BaseButton.vue'
import BaseLink from '@/components/ui/BaseLink.vue'
import BaseModal from '@/components/ui/BaseModal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import FeedbackMessage from '@/components/ui/FeedbackMessage.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ToggleSwitch from '@/components/ui/ToggleSwitch.vue'
import SaveState from '@/components/ui/SaveState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import UiIcon from '@/components/ui/UiIcon.vue'

export default {
  name: 'ConferenceConfigPage',
  components: { DashboardBreadcrumb, ConferenceToolsNav, BaseButton, BaseLink, BaseModal, EmptyState, FeedbackMessage, LoadingState, ToggleSwitch, SaveState, StatusBadge, UiIcon },
  props: { conferenceId: String },
  setup(props: { conferenceId?: string }) {
    const auth        = useAuthStore()
    const conference   = ref<Conference | null>(null)
    const loading      = ref(true)
    const error        = ref('')

    const activeTab = ref<'general' | 'tools' | 'sandbox' | 'access' | 'roles' | 'ai' | 'network'>('general')
    const configTabs = [
      { id: 'general', label: 'General' },
      { id: 'tools', label: 'Contenido y herramientas' },
      { id: 'sandbox', label: 'IDE y sandboxes' },
      { id: 'access', label: 'Acceso y boletos' },
      { id: 'roles', label: 'Roles y moderación' },
      { id: 'ai', label: 'IA' },
      { id: 'network', label: 'Red' }
    ] as const
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
    // null means that the conference has not opted into the LazyVim pool yet. Do not display
    // a fabricated 1 here: availability correctly treats null as 0/0, and showing 1 made the
    // configuration look enabled while the attendee picker remained disabled.
    const sandboxCliLazyVimPoolSize = ref<number | null>(null)
    const cliEnabled = computed(() => (sandboxCliPoolSize.value ?? 0) > 0
      || (sandboxCliLazyVimPoolSize.value ?? 0) > 0)
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
    const sandboxPrewarmMessage = computed(() => {
      const variants = sandboxPrewarmResult.value?.variants || []
      const details = variants.map((variant) =>
        `${sandboxVariantLabel(variant.variant)} ${variant.createdPods || 0} nuevos de ${variant.desiredPods || 0}`
      )
      return `Pool solicitado: ${details.join('; ')}.`
    })
    const sandboxPrewarmError = ref('')
    const sandboxActionBusy = ref<string | null>(null)
    const sandboxActionSuccess = ref('')
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
    const imageAllowedImages = ref('')
    const imageBlockedImages = ref('')
    const savingImagePolicy = ref(false)
    const imagePolicySaved = ref(false)
    const imagePolicyError = ref('')

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

    async function loadImagePolicy() {
      try {
        const policy = await getConferenceImagePolicy(props.conferenceId as string, auth.state.token as string)
        imageAllowedImages.value = csvToLines(policy.allowedImages)
        imageBlockedImages.value = csvToLines(policy.blockedImages)
      } catch (e: any) {
        imagePolicyError.value = e.response?.data?.error?.message || 'No se pudo cargar la política de imágenes del evento'
      }
    }

    async function saveImagePolicy() {
      savingImagePolicy.value = true; imagePolicySaved.value = false; imagePolicyError.value = ''
      try {
        const policy = await setConferenceImagePolicy(
          props.conferenceId as string, linesToCsv(imageAllowedImages.value), linesToCsv(imageBlockedImages.value),
          auth.state.token as string
        )
        imageAllowedImages.value = csvToLines(policy.allowedImages)
        imageBlockedImages.value = csvToLines(policy.blockedImages)
        imagePolicySaved.value = true
      } catch (e: any) {
        imagePolicyError.value = e.response?.data?.error?.message || 'No se pudo guardar la política de imágenes del evento'
      } finally {
        savingImagePolicy.value = false
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

    // Aviso de cambios sin guardar (auditoría UX): la página tiene ~10 botones "Guardar X"
    // independientes y era muy fácil editar varias secciones, guardar una y salir perdiendo el
    // resto en silencio. formDirty se activa con cualquier input dentro del formulario y se
    // limpia cuando CUALQUIER guardado tiene éxito (heurística optimista: cubre el flujo comun
    // editar->guardar->salir sin molestar; editar dos secciones y guardar solo una puede no
    // avisar — preferible a avisar siempre en falso). Para route-leave y beforeunload se usa el
    // dialogo nativo: es el unico mecanismo que el navegador permite en beforeunload, y usar el
    // mismo en ambos mantiene el comportamiento identico.
    const formDirty = ref(false)
    const pendingTab = ref<string | null>(null)
    const markFormDirty = () => { formDirty.value = true }
    const savedFlags = [eventTypeSaved, certificateEngineSaved, canvasConfigSaved, seatingSaved,
      ticketSalesSaved, sandboxConfigSaved, deviceAccessConfigSaved, egressPolicySaved, imagePolicySaved,
      mentorSaved, surveyAiConfigSaved]
    savedFlags.forEach((flag) => watch(flag, (saved) => { if (saved) formDirty.value = false }))

    const savingFlags = [savingEventType, savingCertificateEngine, savingCanvasConfig, savingSeating,
      savingTicketSales, savingSandboxConfig, savingSandboxInternet, savingDeviceAccessConfig,
      savingEgressPolicy, savingImagePolicy, savingMentor, savingSurveyAiConfig]
    const saveState = computed(() => {
      if (savingFlags.some((flag) => flag.value)) return 'saving'
      if (formDirty.value) return 'dirty'
      if (savedFlags.some((flag) => flag.value)) return 'saved'
      return 'clean'
    })
    function selectTab(tab: typeof activeTab.value) {
      if (tab === activeTab.value) return
      if (formDirty.value) {
        pendingTab.value = tab
        return
      }
      activeTab.value = tab
      formDirty.value = false
    }

    function focusTab(direction: number) {
      const currentIndex = configTabs.findIndex((tab) => tab.id === activeTab.value)
      const nextIndex = direction < -1
        ? 0
        : direction > 1
          ? configTabs.length - 1
          : (currentIndex + direction + configTabs.length) % configTabs.length
      const nextTab = configTabs[nextIndex]
      const wasDirty = formDirty.value
      selectTab(nextTab.id)
      if (wasDirty && nextTab.id !== activeTab.value) return
      window.requestAnimationFrame(() => document.getElementById(`config-tab-${nextTab.id}`)?.focus())
    }

    function confirmTabChange() {
      if (!pendingTab.value) return
      activeTab.value = pendingTab.value as typeof activeTab.value
      pendingTab.value = null
      formDirty.value = false
    }

    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (formDirty.value) { e.preventDefault(); e.returnValue = '' }
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
    onBeforeRouteLeave(() => {
      if (!formDirty.value) return true
      return window.confirm('Hay cambios sin guardar en esta página. ¿Salir de todos modos?')
    })

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
        sandboxCliLazyVimPoolSize.value = conference.value.sandboxCliLazyVimPoolSize ?? null
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
      await loadImagePolicy()
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
          sandboxRemoteGitUrl.value.trim() || null,
          sandboxJvmHeapMb.value, sandboxSeatsPerPod.value, sandboxCliPoolSize.value,
          sandboxCliLazyVimPoolSize.value,
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

    function sandboxVariantLabel(variant: string): string {
      if (variant === 'cli-lazyvim') return 'CLI · LazyVim'
      if (variant === 'cli') return 'CLI · Neovim'
      return 'Web'
    }

    function sandboxIsFree(pod: SandboxStatusEntry): boolean {
      return pod.seats.length === 0 || pod.seats.every((seat) => !seat.userUuid)
    }

    const sandboxConfirm = ref<{
      action: 'delete' | 'recreate'
      pod: SandboxStatusEntry
      occupied: boolean
      occupiedSeats: number
    } | null>(null)

    function deleteSandbox(pod: SandboxStatusEntry) {
      sandboxConfirm.value = {
        action: 'delete',
        pod,
        occupied: !sandboxIsFree(pod),
        occupiedSeats: pod.seats.filter((seat) => seat.userUuid).length
      }
    }

    function recreateSandbox(pod: SandboxStatusEntry) {
      if (!sandboxIsFree(pod)) return
      sandboxConfirm.value = { action: 'recreate', pod, occupied: false, occupiedSeats: 0 }
    }

    async function performSandboxAction() {
      const confirm = sandboxConfirm.value
      if (!confirm) return
      const { action, pod } = confirm
      sandboxActionBusy.value = pod.podName
      sandboxActionSuccess.value = ''
      sandboxActionError.value = ''
      try {
        if (action === 'delete') {
          await deleteSandboxApi(props.conferenceId as string, pod.sandboxUuid, auth.state.token as string)
        } else {
          await recreateSandboxApi(props.conferenceId as string, pod.sandboxUuid, auth.state.token as string)
        }
        await loadSandboxStatus()
        sandboxActionSuccess.value = action === 'delete'
          ? `Sandbox ${pod.podName} eliminado.`
          : `Sandbox ${pod.podName} recreado. Se está preparando la nueva instancia.`
        sandboxConfirm.value = null
      } catch (e: any) {
        sandboxActionError.value = e.response?.data?.error?.message ||
          (action === 'delete' ? 'No se pudo eliminar el sandbox' : 'No se pudo recrear el sandbox')
        sandboxConfirm.value = null
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
      { label: 'Eventos', to: '/dashboard/events' },
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

    return { conference, loading, error, activeTab, configTabs, selectTab, focusTab, pendingTab, confirmTabChange, saveState,
             seatingMode, capacity, recommendedMaxCapacity, capacityAlert, savingSeating, seatingSaved, seatingError, saveSeating,
             ticketSalesEnabled, savingTicketSales, ticketSalesSaved, ticketSalesError, saveTicketSales,
             sandboxVariant, sandboxPoolSize, sandboxCliPoolSize, sandboxCliLazyVimPoolSize, cliEnabled,
             sandboxRemoteGitUrl, sandboxJvmHeapMb,
             sandboxSeatsPerPod, sandboxInternetEnabled,
             savingSandboxConfig, sandboxConfigSaved, sandboxConfigError, savingSandboxInternet,
             saveSandboxConfig, saveSandboxInternet,
             sandboxIncidents, sandboxIncidentsLoaded, loadingSandboxIncidents, sandboxIncidentsError,
             loadSandboxIncidents, incidentTypeLabel,
             sandboxStatus, sandboxStatusLoaded, loadingSandboxStatus, sandboxStatusError, loadSandboxStatus,
             prewarmingSandboxPool, sandboxPrewarmResult, sandboxPrewarmMessage, sandboxPrewarmError, prewarmSandboxPool,
             sandboxActionBusy, sandboxActionSuccess, sandboxActionError, sandboxIsFree, deleteSandbox, recreateSandbox,
             sandboxConfirm, performSandboxAction, markFormDirty,
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
             saveEgressPolicy,
             imageAllowedImages, imageBlockedImages, savingImagePolicy, imagePolicySaved, imagePolicyError,
             saveImagePolicy }
  }
}
</script>

<style scoped>
.conf-config-page { max-width: 680px; }
h2 { color: var(--color-heading); margin-bottom: 8px; margin-top: 0; }
.config-tabs { display: flex; gap: 6px; flex-wrap: wrap; margin: 0 0 20px; padding-bottom: 10px; border-bottom: 1px solid var(--color-border-subtle); }
.config-tab { padding: 8px 14px; border: 1.5px solid var(--color-border); border-radius: 8px; background: var(--color-surface); color: var(--color-text-secondary); cursor: pointer; font-size: 0.88rem; font-weight: 600; }
.config-tab:hover { border-color: var(--color-focus); color: var(--color-primary); }
.config-tab:focus-visible { outline: none; border-color: var(--color-focus); box-shadow: 0 0 0 3px var(--color-primary-soft); }
.config-tab.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-on-primary); }
.form-group { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
label { font-weight: 600; font-size: 0.9rem; color: var(--color-text-secondary); }
input[type="text"], input[type="number"] {
  padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 1rem;
}
textarea {
  width: 100%; box-sizing: border-box; padding: 10px 14px; border: 1.5px solid var(--color-border);
  border-radius: 8px; font: inherit; resize: vertical; min-height: 80px;
}
input:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }
textarea:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; border-color: var(--color-primary); }

.scope-badge { align-self: flex-start; display: inline-flex; padding: 3px 9px; border-radius: 999px; background: var(--color-primary-soft); color: var(--color-primary-dark); font-size: 0.72rem; font-weight: 700; }
.tickets-group select, .canvas-group select { padding: 10px 14px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 1rem; margin-bottom: 10px; }
.coord-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.coord-label { font-size: 0.8rem; color: var(--color-text-muted); font-weight: 500; }
.ticket-links { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 4px; }

.capacity-alert { margin: 6px 0 0; font-size: 0.82rem; font-weight: 600; padding: 6px 10px; border-radius: 6px; }
.capacity-alert.warning { background: var(--color-warning-soft); color: var(--color-warning); }
.capacity-alert.risk { background: var(--color-warning-soft); color: var(--color-warning); }
.capacity-alert.critical { background: var(--color-danger-soft); color: var(--color-danger-dark); }

.sandbox-status { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border-subtle); }
.prewarm-control { margin-top: 10px; padding: 10px 12px; border: 1px solid var(--color-primary-border); border-radius: 8px; background: var(--color-primary-soft); }
.sandbox-incidents { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border-subtle); }
.incidents-table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 0.82rem; }
.incidents-table th { text-align: left; padding: 6px 10px; background: var(--color-surface-muted); color: var(--color-text-muted); font-weight: 600; }
.incidents-table td { padding: 6px 10px; border-top: 1px solid var(--color-surface-muted); color: var(--color-text-secondary); }
.incidents-table th:last-child, .incidents-table td:last-child { min-width: 92px; }
.sandbox-actions { min-width: 92px; vertical-align: middle; white-space: nowrap; }
.sandbox-actions-group { display: flex; align-items: center; justify-content: center; gap: 6px; }
.icon-action { width: 34px; height: 34px; padding: 0; }
.action-note { color: var(--color-text-muted); font-size: 0.75rem; }

.seats-cell { display: flex; flex-wrap: wrap; gap: 6px; }
.seat-badge { display: inline-flex; }
.seat-user { background: var(--color-primary-soft); color: var(--color-primary); border-radius: 6px; padding: 2px 8px; font-size: 0.78rem; font-family: var(--font-family-mono); }
.seat-empty { font-size: 0.78rem; color: var(--color-text-muted); }

.sandbox-status-table td::before { display: none; }

.roles-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.role-row { display: flex; align-items: center; gap: 10px; padding: 6px 10px; background: var(--color-surface-muted); border-radius: 8px; }
.role-person { flex: 1; font-size: 0.85rem; color: var(--color-text-secondary); }
.role-badge { font-size: 0.72rem; background: var(--color-primary-soft); color: var(--color-primary-dark); padding: 2px 10px; border-radius: 10px; font-weight: 600; }
.assign-row { display: flex; gap: 8px; flex-wrap: wrap; }
.assign-row input, .assign-row select { padding: 8px 12px; border: 1.5px solid var(--color-border); border-radius: 8px; font-size: 0.9rem; }
.assign-row input { flex: 1; min-width: 160px; }

@media (max-width: 640px) {
  .config-tabs { flex-wrap: nowrap; overflow-x: auto; margin-inline: -4px; padding-inline: 4px 2px; scrollbar-width: thin; }
  .config-tab { flex: 0 0 auto; }
  .role-row { align-items: flex-start; flex-wrap: wrap; }
  .role-person { flex-basis: 100%; }
  .assign-row { flex-direction: column; }
  .assign-row input, .assign-row select { width: 100%; box-sizing: border-box; }
  .sandbox-actions-group { justify-content: flex-start; }
  .sandbox-status-table,
  .sandbox-status-table tbody,
  .sandbox-status-table tr,
  .sandbox-status-table td { display: block; width: 100%; box-sizing: border-box; }
  .sandbox-status-table thead { display: none; }
  .sandbox-status-table tbody tr {
    margin-bottom: 12px;
    padding: 8px 12px;
    border: 1px solid var(--color-border-subtle);
    border-radius: var(--radius-md);
    background: var(--color-surface);
    box-shadow: var(--shadow-card);
  }
  .sandbox-status-table td {
    display: grid;
    grid-template-columns: 7.5rem minmax(0, 1fr);
    align-items: center;
    gap: 8px;
    padding: 7px 0;
    border-top: 0;
  }
  .sandbox-status-table td::before {
    display: block;
    content: attr(data-label);
    color: var(--color-text-muted);
    font-size: 0.75rem;
    font-weight: 700;
  }
  .sandbox-status-table td > .status-badge,
  .sandbox-status-table .seat-badge,
  .sandbox-status-table .sandbox-actions-group,
  .sandbox-status-table .action-note { grid-column: 2; justify-self: start; }
  .sandbox-status-table .seats-cell,
  .sandbox-status-table .sandbox-actions { display: grid; grid-template-columns: 7.5rem minmax(0, 1fr); }
  .sandbox-status-table .seat-badge { width: fit-content; max-width: 100%; }
}
</style>
