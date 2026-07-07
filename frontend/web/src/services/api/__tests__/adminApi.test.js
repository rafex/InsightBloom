import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { listUsers, updateUser, banUser, unbanUser, deleteUserLogical } from '../adminApi'

vi.mock('axios')

const BASE = '/api/users/api/v1/admin/users'

describe('adminApi', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('listUsers sends pagination params and returns the raw response body', async () => {
    axios.get.mockResolvedValue({ data: { data: [], total: 0 } })
    const result = await listUsers('tok', 2, 20)
    expect(axios.get).toHaveBeenCalledWith(BASE, {
      params: { page: 2, pageSize: 20 },
      headers: { Authorization: 'Bearer tok' }
    })
    expect(result).toEqual({ data: [], total: 0 })
  })

  it('listUsers omits the Authorization header when no token is given', async () => {
    axios.get.mockResolvedValue({ data: { data: [] } })
    await listUsers(undefined)
    expect(axios.get).toHaveBeenCalledWith(BASE, { params: { page: 1, pageSize: 50 }, headers: {} })
  })

  it('updateUser forwards all editable fields', async () => {
    axios.put.mockResolvedValue({ data: { data: { uuid: 'u1' } } })
    await updateUser('u1', {
      displayName: 'Ana', email: 'a@x.com', phone: '555', roles: ['admin'],
      firstName: 'Ana', lastName: 'Lopez'
    }, 'tok')
    const [url, body, config] = axios.put.mock.calls[0]
    expect(url).toBe(`${BASE}/u1`)
    expect(body).toEqual({
      displayName: 'Ana', email: 'a@x.com', phone: '555', roles: ['admin'],
      firstName: 'Ana', lastName: 'Lopez'
    })
    expect(config.headers.Authorization).toBe('Bearer tok')
  })

  it('banUser / unbanUser / deleteUserLogical hit the expected action endpoints', async () => {
    axios.post.mockResolvedValue({ data: { data: {} } })

    await banUser('u1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/u1/ban`, {}, { headers: { Authorization: 'Bearer tok' } })

    await unbanUser('u1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/u1/unban`, {}, { headers: { Authorization: 'Bearer tok' } })

    await deleteUserLogical('u1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/u1/delete`, {}, { headers: { Authorization: 'Bearer tok' } })
  })
})
