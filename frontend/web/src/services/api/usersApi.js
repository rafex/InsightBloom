import axios from 'axios'

export async function getConferences(token) {
  const res = await axios.get('/api/users/api/v1/conferences', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function createConference(name, expiresAt, token, latitude, longitude, eventDate, venue) {
  const body = { name }
  if (expiresAt) body.expiresAt = expiresAt
  if (latitude != null) body.latitude = latitude
  if (longitude != null) body.longitude = longitude
  if (eventDate) body.eventDate = eventDate
  if (venue) body.venue = venue
  const res = await axios.post('/api/users/api/v1/conferences', body, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function deleteConference(uuid, token) {
  await axios.delete(`/api/users/api/v1/conferences/${uuid}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
}

export async function getConference(id, token) {
  const res = await axios.get(`/api/users/api/v1/conferences/${id}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function getConferenceByFriendlyId(friendlyId) {
  const res = await axios.get(`/api/users/api/v1/conferences/by-friendly/${friendlyId}`)
  return res.data.data
}

export async function getUserProfile(uuid) {
  const res = await axios.get(`/api/users/api/v1/users/${uuid}`)
  return res.data.data
}

export async function updateUserProfile(uuid, { firstName, lastName }, token) {
  const res = await axios.put(`/api/users/api/v1/users/${uuid}`, { firstName, lastName }, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function joinConference(identifier, token) {
  const res = await axios.post('/api/users/api/v1/conferences/join', { identifier }, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function getConferenceHistory(token) {
  const res = await axios.get('/api/users/api/v1/conferences/history', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function getCertificateBlobUrl(conferenceId, token) {
  const res = await axios.get(`/api/users/api/v1/conferences/${conferenceId}/certificate`, {
    headers: { Authorization: `Bearer ${token}` },
    responseType: 'blob'
  })
  return URL.createObjectURL(res.data)
}
