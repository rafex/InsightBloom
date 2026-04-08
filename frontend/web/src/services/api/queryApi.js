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
