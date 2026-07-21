import axios from 'axios'

const BASE = '/api/presentations/api/v1'

export type PresentationProvider = 'MARP' | 'SLIDEV'

export interface PresentationStatus {
  ready: boolean
  provider?: PresentationProvider
  presentationProvider?: PresentationProvider
  engineVersion?: string | null
  [key: string]: unknown
}

function authHeader(token?: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function uploadPresentation(conferenceId: string, file: Blob, token: string, provider: PresentationProvider = 'MARP'): Promise<unknown> {
  const form = new FormData()
  form.append('file', file)
  form.append('presentationProvider', provider)
  const res = await axios.post(`${BASE}/conferences/${conferenceId}/presentation`, form, {
    headers: { ...authHeader(token), 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export async function getPresentationStatus(conferenceId: string): Promise<PresentationStatus> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/presentation/status`)
  return res.data
}

export function getSlidesUrl(conferenceId: string, token?: string | null): string {
  const query = token ? `?ib_token=${encodeURIComponent(token)}` : ''
  return `${BASE}/conferences/${conferenceId}/presentation/slides${query}`
}

export function getPresentationRootUrl(conferenceId: string, token?: string | null): string {
  const query = token ? `?ib_token=${encodeURIComponent(token)}` : ''
  return `${BASE}/conferences/${conferenceId}/presentation/${query}`
}

export function getPresenterSlidesUrl(conferenceId: string, token?: string | null): string {
  const query = token ? `?ib_token=${encodeURIComponent(token)}` : ''
  return `${BASE}/conferences/${conferenceId}/presentation/presenter${query}`
}

export function getSlidesPreviewUrl(conferenceId: string): string {
  return `${BASE}/conferences/${conferenceId}/presentation/slides/preview`
}

export function getPdfUrl(conferenceId: string, token?: string | null): string {
  const query = token ? `?ib_token=${encodeURIComponent(token)}` : ''
  return `${BASE}/conferences/${conferenceId}/presentation/pdf${query}`
}

function wsBase(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}${BASE}`
}

export function getAudienceWsUrl(conferenceId: string, token?: string | null): string {
  const query = token ? `?ib_token=${encodeURIComponent(token)}` : ''
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/audience${query}`
}

export function getPresenterWsUrl(conferenceId: string, token: string): string {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/presenter?token=${encodeURIComponent(token)}`
}

export function getRemoteWsUrl(conferenceId: string, remoteToken: string): string {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/remote?token=${encodeURIComponent(remoteToken)}`
}

export async function createRemoteLinkToken(conferenceId: string, organizerToken: string): Promise<string> {
  const res = await axios.post(
    `${BASE}/conferences/${conferenceId}/presentation/remote-token`,
    {},
    { headers: authHeader(organizerToken) }
  )
  return res.data.token
}
