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

function wsBase() {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}${BASE}`
}

export function getAudienceWsUrl(conferenceId) {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/audience`
}

export function getPresenterWsUrl(conferenceId, token) {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/presenter?token=${encodeURIComponent(token)}`
}

export function getRemoteWsUrl(conferenceId, remoteToken) {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/remote?token=${encodeURIComponent(remoteToken)}`
}

export async function createRemoteLinkToken(conferenceId, organizerToken) {
  const res = await axios.post(
    `${BASE}/conferences/${conferenceId}/presentation/remote-token`,
    {},
    { headers: authHeader(organizerToken) }
  )
  return res.data.token
}
