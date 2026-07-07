import axios from 'axios'

export interface SendMessageRequest {
  conferenceId: string
  authorUuid?: string | null
  authorKind?: string
  displayName?: string | null
  deviceFingerprint?: string | null
  type: 'doubt' | 'topic'
  word: string
  detail?: string | null
  token?: string | null
}

export async function sendMessage({
  conferenceId, authorUuid, authorKind, displayName, deviceFingerprint, type, word, detail, token
}: SendMessageRequest): Promise<unknown> {
  const res = await axios.post('/api/ingest/api/v1/messages', {
    conferenceId,
    author: { userId: authorUuid, kind: authorKind || 'user', displayName },
    device: { fingerprint: deviceFingerprint },
    message: { type, word, detail },
    source: { type: 'rest' },
    receivedAt: new Date().toISOString()
  }, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  return res.data.data
}
