import axios from 'axios'
import type {
  Conference, ConferenceHistoryEntry, UpdateConferenceRequest,
  DownloadCounts, CertificateSettings, Timezone, UserProfile,
  SeatingMode, Reservation, VenueSeat, EventType, EventCapability, IntegrationConfig, EventNotesPad,
  Role, RoleScopeValue, PermissionValue, EventRoleAssignment, JaasToken, SandboxInfo, WorkspaceDownloadInfo,
  ChatSettings, SandboxIncident, SandboxVariant, SandboxAvailability, SandboxStatusEntry,
  WorkspaceFileEntry, WorkspaceFileContent, DeviceBlock, DeviceAccessSettings, PlatformDeviceBlock
} from './types'
import { getFingerprint } from '@/services/auth/fingerprint'

function authHeader(token?: string | null) {
  return { headers: { Authorization: `Bearer ${token}` } }
}

/** Mismo fingerprint que ya usan invitados anonimos (ver services/auth/fingerprint.ts), ahora
 *  tambien mandado en Jitsi/IDE para que DeviceAccessGuard pueda controlar cuantos dispositivos
 *  usa un mismo usuario y cuantas cuentas comparte un mismo dispositivo. */
async function authHeaderWithDevice(token?: string | null) {
  const fingerprint = await getFingerprint()
  return { headers: { Authorization: `Bearer ${token}`, 'X-Device-Fingerprint': fingerprint } }
}

export async function getConferences(token: string): Promise<Conference[]> {
  const res = await axios.get('/api/users/api/v1/conferences', authHeader(token))
  return res.data.data
}

export async function getAttendeesCount(conferenceId: string, token: string): Promise<number> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/attendees/count`, authHeader(token))
  return res.data.data.count
}

export async function getRegisteredAttendeesCount(conferenceId: string, token: string): Promise<number> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/attendees/count`, authHeader(token))
  return res.data.data.registered
}

/** Personas únicas registradas en alguna conferencia del organizador (deduplicado). */
export async function getUniqueRegisteredAttendeesCount(token: string): Promise<number> {
  const res = await axios.get('/api/users/api/v1/conferences/attendees/registered-summary', authHeader(token))
  return res.data.data.uniqueRegisteredAttendees
}

/** Igual que getUniqueRegisteredAttendeesCount, pero solo cuenta usuarios con status ACTIVE. */
export async function getActiveRegisteredAttendeesCount(token: string): Promise<number> {
  const res = await axios.get('/api/users/api/v1/conferences/attendees/active-summary', authHeader(token))
  return res.data.data.activeRegisteredAttendees
}

export async function getDownloadCounts(conferenceId: string, token: string): Promise<DownloadCounts> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/downloads/count`, authHeader(token))
  return res.data.data
}

export async function createConference(
  name: string,
  expiresAt: string | null | undefined,
  token: string,
  latitude?: number | null,
  longitude?: number | null,
  eventDate?: string | null,
  venue?: string | null,
  startTime?: string | null,
  endTime?: string | null,
  displayName?: string | null,
  timezoneId?: number | null,
  eventTypeKey?: string | null,
  capacity?: number | null
): Promise<Conference> {
  const body: Record<string, unknown> = { name }
  if (displayName) body.displayName = displayName
  if (expiresAt) body.expiresAt = expiresAt
  if (latitude != null) body.latitude = latitude
  if (longitude != null) body.longitude = longitude
  if (eventDate) body.eventDate = eventDate
  if (venue) body.venue = venue
  if (startTime) body.startTime = startTime
  if (endTime) body.endTime = endTime
  if (timezoneId != null) body.timezoneId = timezoneId
  if (eventTypeKey) body.eventTypeKey = eventTypeKey
  if (capacity != null) body.capacity = capacity
  const res = await axios.post('/api/users/api/v1/conferences', body, authHeader(token))
  return res.data.data
}

export async function updateConference(
  uuid: string,
  {
    displayName, venue, eventDate, startTime, endTime, latitude, longitude, presentationSourceUrl,
    flyerBase64, timezoneId
  }: UpdateConferenceRequest,
  token: string
): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${uuid}`, {
    displayName, venue, eventDate, startTime, endTime, latitude, longitude, presentationSourceUrl,
    flyerBase64, timezoneId
  }, authHeader(token))
  return res.data.data
}

export async function getTimezones(): Promise<Timezone[]> {
  const res = await axios.get('/api/users/api/v1/timezones')
  return res.data.data
}

export async function deleteConference(uuid: string, token: string): Promise<void> {
  await axios.delete(`/api/users/api/v1/conferences/${uuid}`, authHeader(token))
}

export async function getConference(id: string, token: string): Promise<Conference> {
  const res = await axios.get(`/api/users/api/v1/conferences/${id}`, authHeader(token))
  return res.data.data
}

export async function getConferenceByFriendlyId(friendlyId: string): Promise<Conference> {
  const res = await axios.get(`/api/users/api/v1/conferences/by-friendly/${friendlyId}`)
  return res.data.data
}

export async function getUserProfile(uuid: string): Promise<UserProfile> {
  const res = await axios.get(`/api/users/api/v1/users/${uuid}`)
  return res.data.data
}

export async function updateUserProfile(
  uuid: string,
  { firstName, lastName }: { firstName?: string | null, lastName?: string | null },
  token: string
): Promise<UserProfile> {
  const res = await axios.put(`/api/users/api/v1/users/${uuid}`, { firstName, lastName }, authHeader(token))
  return res.data.data
}

export async function joinConference(identifier: string, token: string): Promise<Conference> {
  const res = await axios.post('/api/users/api/v1/conferences/join', { identifier }, authHeader(token))
  return res.data.data
}

export async function getConferenceHistory(token: string): Promise<ConferenceHistoryEntry[]> {
  const res = await axios.get('/api/users/api/v1/conferences/history', authHeader(token))
  return res.data.data
}

export async function changePassword(
  uuid: string,
  { currentPassword, newPassword }: { currentPassword: string, newPassword: string },
  token: string
): Promise<unknown> {
  const res = await axios.post(`/api/users/api/v1/users/${uuid}/password`, { currentPassword, newPassword }, authHeader(token))
  return res.data.data
}

export async function getCertificateBlobUrl(conferenceId: string, token: string): Promise<string> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/certificate`, {
    headers: { Authorization: `Bearer ${token}` },
    responseType: 'blob'
  })
  return URL.createObjectURL(res.data)
}

export async function getCertificateSettings(): Promise<CertificateSettings> {
  const res = await axios.get('/api/users/api/v1/certificate-settings')
  return res.data.data
}

export async function saveCertificateSettings(settings: CertificateSettings, token: string): Promise<CertificateSettings> {
  const res = await axios.put('/api/users/api/v1/certificate-settings', settings, authHeader(token))
  return res.data.data
}

export async function setSeatingMode(
  conferenceId: string, seatingMode: SeatingMode, capacity: number | null, token: string
): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/seating`,
    { seatingMode, capacity }, authHeader(token))
  return res.data.data
}

export async function setSandboxConfig(
  conferenceId: string,
  sandboxVariant: string,
  sandboxPoolSize: number | null,
  sandboxExtraPackages: string | null,
  sandboxRemoteGitUrl: string | null,
  sandboxJvmHeapMb: number | null,
  sandboxSeatsPerPod: number | null,
  sandboxCliPoolSize: number | null,
  token: string
): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/sandbox-config`,
    { sandboxVariant, sandboxPoolSize, sandboxExtraPackages, sandboxRemoteGitUrl, sandboxJvmHeapMb,
      sandboxSeatsPerPod, sandboxCliPoolSize },
    authHeader(token))
  return res.data.data
}

export async function setDeviceAccessConfig(
  conferenceId: string,
  maxDevicesPerUser: number | null,
  maxAccountsPerDevice: number | null,
  token: string
): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/device-access-config`,
    { maxDevicesPerUser, maxAccountsPerDevice },
    authHeader(token))
  return res.data.data
}

export async function listDeviceBlocks(conferenceId: string, token: string): Promise<DeviceBlock[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/device-blocks`, authHeader(token))
  return res.data.data
}

export async function unblockDevice(conferenceId: string, blockUuid: string, token: string): Promise<void> {
  await axios.post(`/api/users/api/v1/conferences/${conferenceId}/device-blocks/${blockUuid}/unblock`,
    {}, authHeader(token))
}

export async function listSandboxIncidents(
  conferenceId: string, token: string
): Promise<SandboxIncident[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox-incidents`, authHeader(token))
  return res.data.data
}

export async function listSandboxStatus(
  conferenceId: string, token: string
): Promise<SandboxStatusEntry[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox-status`, authHeader(token))
  return res.data.data
}

export async function listWorkspaceFiles(
  conferenceId: string, userUuid: string, path: string, token: string
): Promise<WorkspaceFileEntry[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox/files`,
    { params: { userUuid, path }, ...authHeader(token) })
  return res.data.data.entries
}

export async function readWorkspaceFile(
  conferenceId: string, userUuid: string, path: string, token: string
): Promise<WorkspaceFileContent> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox/file`,
    { params: { userUuid, path }, ...authHeader(token) })
  return res.data.data
}

export async function writeWorkspaceFile(
  conferenceId: string, userUuid: string, path: string, content: string,
  mtime: number | null, force: boolean, token: string
): Promise<{ mtime: number }> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/sandbox/file`,
    { content, mtime, force },
    { params: { userUuid, path }, ...authHeader(token) })
  return res.data.data
}

export async function setSandboxInternet(
  conferenceId: string, internetEnabled: boolean, token: string
): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/sandbox-internet`,
    { internetEnabled }, authHeader(token))
  return res.data.data
}

export async function reserveGeneral(conferenceId: string, token: string): Promise<Reservation> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/reservations`, {}, authHeader(token))
  return res.data.data
}

export async function getMyTicket(conferenceId: string, token: string): Promise<Reservation | null> {
  try {
    const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/reservations/me`, authHeader(token))
    return res.data.data
  } catch (e: any) {
    if (e.response?.status === 404) return null
    throw e
  }
}

export async function cancelReservation(conferenceId: string, token: string): Promise<void> {
  await axios.delete(`/api/users/api/v1/conferences/${conferenceId}/reservations/me`, authHeader(token))
}

export async function listReservations(conferenceId: string, token: string): Promise<Reservation[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/reservations`, authHeader(token))
  return res.data.data
}

export async function checkInTicket(conferenceId: string, ticketCode: string, token: string): Promise<Reservation> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/reservations/check-in`,
    { ticketCode }, authHeader(token))
  return res.data.data
}

export async function setVenueMap(conferenceId: string, imageBase64: string, token: string): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/venue-map`,
    { imageBase64 }, authHeader(token))
  return res.data.data
}

export async function defineVenueSeats(
  conferenceId: string,
  seats: Array<{ uuid?: string | null, label: string, x: number, y: number }>,
  token: string
): Promise<VenueSeat[]> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/seats`, { seats }, authHeader(token))
  return res.data.data
}

export async function generateSeatLayout(
  conferenceId: string,
  description: string,
  token: string
): Promise<Array<{ uuid: string | null, label: string, x: number, y: number }>> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/venue-map/generate-seats`,
    { description }, authHeader(token))
  return res.data.data.seats
}

export async function getConferenceSeatMap(conferenceId: string, token: string): Promise<VenueSeat[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/seats`, authHeader(token))
  return res.data.data
}

export async function reserveSeat(conferenceId: string, seatUuid: string, token: string): Promise<Reservation> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/reservations`,
    { seatUuid }, authHeader(token))
  return res.data.data
}

export async function setEventType(conferenceId: string, eventTypeKey: string, token: string): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${conferenceId}/event-type`,
    { eventTypeKey }, authHeader(token))
  return res.data.data
}

/** Tipos de evento activos, para el selector del organizador al crear/editar un evento. */
export async function getActiveEventTypes(): Promise<EventType[]> {
  const res = await axios.get('/api/users/api/v1/event-types')
  return res.data.data
}

/** Catálogo completo (incluye inactivos), solo para ADMIN. */
export async function getAllEventTypes(token: string): Promise<EventType[]> {
  const res = await axios.get('/api/users/api/v1/event-types/all', authHeader(token))
  return res.data.data
}

export async function getEventCapabilities(): Promise<EventCapability[]> {
  const res = await axios.get('/api/users/api/v1/event-capabilities')
  return res.data.data
}

export async function createEventType(
  key: string, name: string, description: string | null, capabilities: EventCapability[], token: string
): Promise<EventType> {
  const res = await axios.post('/api/users/api/v1/event-types', { key, name, description, capabilities }, authHeader(token))
  return res.data.data
}

export async function updateEventType(
  uuid: string, name: string, description: string | null, capabilities: EventCapability[], token: string
): Promise<EventType> {
  const res = await axios.put(`/api/users/api/v1/event-types/${uuid}`, { name, description, capabilities }, authHeader(token))
  return res.data.data
}

export async function setEventTypeActive(uuid: string, active: boolean, token: string): Promise<EventType> {
  const res = await axios.put(`/api/users/api/v1/event-types/${uuid}/active`, { active }, authHeader(token))
  return res.data.data
}

/** Activa/desactiva manualmente una conferencia. Solo aplica a conferencias sin expiresAt. */
export async function setConferenceActive(uuid: string, active: boolean, token: string): Promise<Conference> {
  const res = await axios.put(`/api/users/api/v1/conferences/${uuid}/active`, { active }, authHeader(token))
  return res.data.data
}

/** URLs publicas de las integraciones self-hosted configuradas (drawio, etc.), sin credenciales. */
export async function getIntegrationConfig(): Promise<IntegrationConfig> {
  const res = await axios.get('/api/users/api/v1/integrations')
  return res.data.data
}

/** Crea (perezosamente) y devuelve el pad de Etherpad del evento. */
export async function getEventNotes(conferenceId: string, token: string): Promise<EventNotesPad> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/notes`, authHeader(token))
  return res.data.data
}

/** Ultimo XML de drawio guardado para el evento (vacio si nunca se guardo). */
export async function getEventDiagram(conferenceId: string, token: string): Promise<{ xml: string }> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/diagram`, authHeader(token))
  return res.data.data
}

/** Guarda (reemplaza) el XML del diagrama de drawio del evento. */
export async function saveEventDiagram(conferenceId: string, xml: string, token: string): Promise<void> {
  await axios.put(`/api/users/api/v1/conferences/${conferenceId}/diagram`, { xml }, authHeader(token))
}

/** Token JWT firmado para unirse a la sala de JaaS (8x8.vc) de este evento, si esta configurado. */
export async function getJaasToken(conferenceId: string, token: string): Promise<JaasToken> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/jaas-token`,
    await authHeaderWithDevice(token))
  return res.data.data
}

/** Roles activos, opcionalmente filtrados por alcance (para el selector del Host al asignar roles de evento). */
export async function getActiveRoles(scope?: RoleScopeValue): Promise<Role[]> {
  const res = await axios.get('/api/users/api/v1/roles', { params: scope ? { scope } : {} })
  return res.data.data
}

/** Catálogo completo (incluye inactivos), solo para quien tenga MANAGE_USERS. */
export async function getAllRoles(token: string): Promise<Role[]> {
  const res = await axios.get('/api/users/api/v1/roles/all', authHeader(token))
  return res.data.data
}

export async function getPermissionsCatalog(): Promise<PermissionValue[]> {
  const res = await axios.get('/api/users/api/v1/permissions')
  return res.data.data
}

export async function createRole(
  key: string, name: string, description: string | null, scope: RoleScopeValue,
  permissions: PermissionValue[], token: string
): Promise<Role> {
  const res = await axios.post('/api/users/api/v1/roles', { key, name, description, scope, permissions }, authHeader(token))
  return res.data.data
}

export async function updateRole(
  uuid: string, name: string, description: string | null, permissions: PermissionValue[], token: string
): Promise<Role> {
  const res = await axios.put(`/api/users/api/v1/roles/${uuid}`, { name, description, permissions }, authHeader(token))
  return res.data.data
}

export async function setRoleActive(uuid: string, active: boolean, token: string): Promise<Role> {
  const res = await axios.put(`/api/users/api/v1/roles/${uuid}/active`, { active }, authHeader(token))
  return res.data.data
}

export async function getChatAiSetting(): Promise<boolean> {
  const res = await axios.get('/api/users/api/v1/settings/chat-ai')
  return res.data.data.chatAiEnabled
}

export async function getChatSettings(): Promise<ChatSettings> {
  const res = await axios.get('/api/users/api/v1/settings/chat-ai')
  return res.data.data
}

export async function setChatAiSetting(chatAiEnabled: boolean, token: string): Promise<boolean> {
  const res = await axios.put('/api/users/api/v1/settings/chat-ai', { chatAiEnabled }, authHeader(token))
  return res.data.data.chatAiEnabled
}

/** Actualiza el prompt de sistema y/o la temperatura de Roberto sin tocar el kill switch chatAiEnabled. */
export async function setChatSettings(
  chatAiEnabled: boolean, chatSystemPrompt: string | null, chatTemperature: number | null, token: string
): Promise<ChatSettings> {
  const res = await axios.put('/api/users/api/v1/settings/chat-ai',
    { chatAiEnabled, chatSystemPrompt, chatTemperature }, authHeader(token))
  return res.data.data
}

export async function getDeviceAccessSettings(token: string): Promise<DeviceAccessSettings> {
  const res = await axios.get('/api/users/api/v1/settings/device-access', authHeader(token))
  return res.data.data
}

export async function setDeviceAccessSettings(
  maxAccountsPerDevice: number | null, maxSessionsPerUser: number | null,
  maxRegistrationsPerDevicePerDay: number | null, token: string
): Promise<DeviceAccessSettings> {
  const res = await axios.put('/api/users/api/v1/settings/device-access',
    { maxAccountsPerDevice, maxSessionsPerUser, maxRegistrationsPerDevicePerDay }, authHeader(token))
  return res.data.data
}

export async function listPlatformDeviceBlocks(token: string): Promise<PlatformDeviceBlock[]> {
  const res = await axios.get('/api/users/api/v1/settings/device-blocks', authHeader(token))
  return res.data.data
}

export async function unblockPlatformDevice(blockUuid: string, token: string): Promise<void> {
  await axios.post(`/api/users/api/v1/settings/device-blocks/${blockUuid}/unblock`, {}, authHeader(token))
}

/** Lista los roles asignados a un evento; el backend devuelve 403 si quien pide no tiene ASSIGN_EVENT_ROLES. */
export async function getEventRoles(conferenceId: string, token: string): Promise<EventRoleAssignment[]> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/roles`, authHeader(token))
  return res.data.data
}

export async function assignEventRole(
  conferenceId: string, userIdentifier: string, roleKey: string, token: string
): Promise<EventRoleAssignment> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/roles`,
    { userIdentifier, roleKey }, authHeader(token))
  return res.data.data
}

export async function removeEventRole(conferenceId: string, userUuid: string, token: string): Promise<void> {
  await axios.delete(`/api/users/api/v1/conferences/${conferenceId}/roles/${userUuid}`, authHeader(token))
}

export async function getSandbox(
  conferenceId: string, token: string | null | undefined, variant: SandboxVariant
): Promise<SandboxInfo> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox?variant=${variant}`,
    await authHeaderWithDevice(token))
  return res.data.data
}

export async function getSandboxAvailability(
  conferenceId: string, token?: string | null
): Promise<SandboxAvailability> {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/sandbox/availability`,
    authHeader(token))
  return res.data.data
}

export async function generateWorkspaceDownloadUrl(conferenceId: string, token?: string | null): Promise<WorkspaceDownloadInfo> {
  const res = await axios.post(`/api/users/api/v1/conferences/${conferenceId}/sandbox/download`, {}, authHeader(token))
  return res.data.data
}
