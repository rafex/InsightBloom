import axios from 'axios'

export async function sendMessage({ conferenceId, authorUuid, authorKind, displayName, deviceFingerprint, type, word, detail, token }) {
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
