import axios from 'axios'

export async function createConference(name, token, latitude, longitude) {
  const body = { name }
  if (latitude != null) body.latitude = latitude
  if (longitude != null) body.longitude = longitude
  const res = await axios.post('/api/users/api/v1/conferences', body, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function getConferences(token) {
  const res = await axios.get('/api/users/api/v1/conferences', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
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
