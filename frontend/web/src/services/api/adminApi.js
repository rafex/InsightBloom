import axios from 'axios'

const BASE = '/api/users/api/v1/admin/users'

function authHeader(token) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function listUsers(token, page = 1, pageSize = 50) {
  const res = await axios.get(BASE, { params: { page, pageSize }, headers: authHeader(token) })
  return res.data
}

export async function updateUser(uuid, { displayName, email, phone, roles, firstName, lastName }, token) {
  const res = await axios.put(`${BASE}/${uuid}`, { displayName, email, phone, roles, firstName, lastName }, {
    headers: authHeader(token)
  })
  return res.data.data
}

export async function banUser(uuid, token) {
  const res = await axios.post(`${BASE}/${uuid}/ban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function unbanUser(uuid, token) {
  const res = await axios.post(`${BASE}/${uuid}/unban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function deleteUserLogical(uuid, token) {
  const res = await axios.post(`${BASE}/${uuid}/delete`, {}, { headers: authHeader(token) })
  return res.data.data
}
