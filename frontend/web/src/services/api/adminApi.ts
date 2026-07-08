import axios from 'axios'

const BASE = '/api/users/api/v1/admin/users'

function authHeader(token?: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export interface AdminUserUpdate {
  displayName?: string | null
  email?: string | null
  phone?: string | null
  roles?: string
  firstName?: string | null
  lastName?: string | null
}

export async function listUsers(token: string, page = 1, pageSize = 50): Promise<{ data: any[], meta?: { totalPages?: number, [key: string]: unknown } }> {
  const res = await axios.get(BASE, { params: { page, pageSize }, headers: authHeader(token) })
  return res.data
}

export async function updateUser(uuid: string, { displayName, email, phone, roles, firstName, lastName }: AdminUserUpdate, token: string): Promise<unknown> {
  const res = await axios.put(`${BASE}/${uuid}`, { displayName, email, phone, roles, firstName, lastName }, {
    headers: authHeader(token)
  })
  return res.data.data
}

export async function banUser(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/ban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function unbanUser(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/unban`, {}, { headers: authHeader(token) })
  return res.data.data
}

export async function deleteUserLogical(uuid: string, token: string): Promise<unknown> {
  const res = await axios.post(`${BASE}/${uuid}/delete`, {}, { headers: authHeader(token) })
  return res.data.data
}
