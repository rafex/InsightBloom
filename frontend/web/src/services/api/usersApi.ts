import axios from 'axios'
import type {
  Conference, ConferenceHistoryEntry, UpdateConferenceRequest,
  DownloadCounts, CertificateSettings, Timezone, UserProfile,
  SeatingMode, Reservation, VenueSeat, EventType, EventCapability
} from './types'

function authHeader(token?: string | null) {
  return { headers: { Authorization: `Bearer ${token}` } }
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
  eventTypeKey?: string | null
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
