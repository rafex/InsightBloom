import axios from 'axios'

export async function getConferences(token) {
  const res = await axios.get('/api/users/api/v1/conferences', {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function createConference(name, token) {
  const res = await axios.post('/api/users/api/v1/conferences', { name }, {
    headers: { Authorization: `Bearer ${token}` }
  })
  return res.data.data
}

export async function getConferenceByFriendlyId(friendlyId) {
  const res = await axios.get(`/api/users/api/v1/conferences/by-friendly/${friendlyId}`)
  return res.data.data
}
