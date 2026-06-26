import axios from 'axios'

const BASE = '/api/presentations/api/v1'

function authHeader(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function uploadPresentation(conferenceId, file, token) {
  const form = new FormData()
  form.append('file', file)
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/presentation`, form, {
    headers: { ...authHeader(token), 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export async function getPresentationStatus(conferenceId) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/presentation/status`)
  return res.data
}

export function getSlidesUrl(conferenceId) {
  return `${BASE}/conferences/${conferenceId}/presentation/slides`
}

export function getSlidesPreviewUrl(conferenceId) {
  return `${BASE}/conferences/${conferenceId}/presentation/slides/preview`
}

export function getPdfUrl(conferenceId) {
  return `${BASE}/conferences/${conferenceId}/presentation/pdf`
}
