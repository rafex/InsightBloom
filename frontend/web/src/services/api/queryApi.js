import axios from 'axios'

const BASE = '/api/query/api/v1'

export async function getDoubtCloud(conferenceId) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/cloud/doubts`)
  return res.data.data
}

export async function getTopicCloud(conferenceId) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/cloud/topics`)
  return res.data.data
}

export async function getWordTimeline(conferenceId, word, type) {
  const res = await axios.get(`${BASE}/conferences/${conferenceId}/words/${encodeURIComponent(word)}/timeline`, {
    params: { type }
  })
  return res.data.data
}

/**
 * Opens an SSE connection for a conference's doubts/topics cloud. `onSnapshot` receives the
 * full word list (on connect and reconnect), `onUpdate` receives one changed word at a time.
 * Returns the EventSource so the caller can close() it; the browser reconnects automatically
 * on transport errors, replaying a fresh snapshot each time.
 */
function streamCloud(conferenceId, kind, onSnapshot, onUpdate) {
  const es = new EventSource(`${BASE}/conferences/${conferenceId}/cloud/${kind}/stream`)
  es.addEventListener('snapshot', (e) => onSnapshot(JSON.parse(e.data)))
  es.addEventListener('update', (e) => onUpdate(JSON.parse(e.data)))
  return es
}

export function streamDoubtCloud(conferenceId, onSnapshot, onUpdate) {
  return streamCloud(conferenceId, 'doubts', onSnapshot, onUpdate)
}

export function streamTopicCloud(conferenceId, onSnapshot, onUpdate) {
  return streamCloud(conferenceId, 'topics', onSnapshot, onUpdate)
}
