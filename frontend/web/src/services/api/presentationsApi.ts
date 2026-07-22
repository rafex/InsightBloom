import axios from 'axios'

const BASE = '/api/presentations/api/v1'

export type PresentationProvider = 'MARP' | 'SLIDEV'
export type PresentationFormat = 'source' | 'fat'

export interface PresentationStatus {
  ready: boolean
  provider?: PresentationProvider
  presentationProvider?: PresentationProvider
  presentationFormat?: PresentationFormat
  engineVersion?: string | null
  [key: string]: unknown
}

export interface OfflinePresentationFile {
  path: string
  size: number
  sha256: string
  contentType: string
}

export interface OfflinePresentationManifest {
  conferenceId: string
  provider: PresentationProvider
  format: PresentationFormat
  indexPath: string
  artifactHash: string
  expiresAt: string
  files: OfflinePresentationFile[]
  signedPayload: string
  signature: string
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

export async function getOfflinePresentationManifest(conferenceId: string, token: string): Promise<OfflinePresentationManifest> {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/presentation/offline-manifest`, {
    headers: authHeader(token)
  })
  return res.data
}

export function getSlidesUrl(conferenceId: string, _token?: string | null): string {
  return `${BASE}/conferences/${conferenceId}/presentation/slides`
}

export function getPresentationRootUrl(conferenceId: string, _token?: string | null): string {
  return `${BASE}/conferences/${conferenceId}/presentation/`
}

export function getPresenterSlidesUrl(conferenceId: string, _token?: string | null): string {
  return `${BASE}/conferences/${conferenceId}/presentation/presenter`
}

export function getSlidesPreviewUrl(conferenceId: string): string {
  return `${BASE}/conferences/${conferenceId}/presentation/slides/preview`
}

export function getPdfUrl(conferenceId: string, _token?: string | null): string {
  return `${BASE}/conferences/${conferenceId}/presentation/pdf`
}

/** Prime the HttpOnly, path-scoped presentation cookie before loading an iframe,
 * WebSocket or download URL. Tokens must not be put in those URLs. */
export async function primePresentationAccess(conferenceId: string, token: string, presenter = false): Promise<void> {
  const path = presenter ? 'presenter' : 'slides'
  await axios.get(`${BASE}/conferences/${conferenceId}/presentation/${path}`, {
    headers: authHeader(token),
    responseType: 'text'
  })
}

function wsBase(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}${BASE}`
}

export function getAudienceWsUrl(conferenceId: string, _token?: string | null): string {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/audience`
}

export function getPresenterWsUrl(conferenceId: string, _token: string): string {
  return `${wsBase()}/conferences/${conferenceId}/presentation/ws/presenter`
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
