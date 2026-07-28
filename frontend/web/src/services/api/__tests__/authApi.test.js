import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { register, sendOtp, verifyOtp, requestLoginOtp, verifyLoginOtp } from '../authApi'

vi.mock('axios')
vi.mock('@/services/auth/fingerprint', () => ({
  getFingerprint: vi.fn().mockResolvedValue('fp-test')
}))

const BASE = '/api/users/api/v1/auth'

describe('authApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('register posts the full registration payload', async () => {
    axios.post.mockResolvedValue({ data: { data: { uuid: 'u1' } } })
    await register({
      displayName: 'Ana', email: 'a@x.com', phone: '555',
      password: 'secret', socialLinks: { linkedin: 'x' }
    })
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/register`, {
      displayName: 'Ana', email: 'a@x.com', phone: '555',
      password: 'secret', socialLinks: { linkedin: 'x' }, deviceFingerprint: 'fp-test'
    })
  })

  it('sendOtp posts identifier and channel', async () => {
    axios.post.mockResolvedValue({ data: { data: { sent: true } } })
    const result = await sendOtp('a@x.com', 'email')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/otp/send`, { identifier: 'a@x.com', channel: 'email' })
    expect(result).toEqual({ sent: true })
  })

  it('verifyOtp posts identifier and code and returns unwrapped data', async () => {
    axios.post.mockResolvedValue({ data: { data: { token: 'tok', role: 'user' } } })
    const result = await verifyOtp('a@x.com', '123456')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/otp/verify`, { identifier: 'a@x.com', code: '123456' })
    expect(result).toEqual({ token: 'tok', role: 'user' })
  })

  it('requestLoginOtp posts only the identifier, no channel', async () => {
    axios.post.mockResolvedValue({ data: { data: { status: 'sent' } } })
    await requestLoginOtp('a@x.com')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/login/otp/request`, { identifier: 'a@x.com' })
  })

  it('verifyLoginOtp posts identifier, code and device fingerprint, returns session data', async () => {
    axios.post.mockResolvedValue({ data: { data: { token: 'tok', userUuid: 'u1', role: 'organizer', expiresAt: '2026-01-01' } } })
    const result = await verifyLoginOtp('a@x.com', '654321')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/login/otp/verify`,
      { identifier: 'a@x.com', code: '654321', deviceFingerprint: 'fp-test' })
    expect(result).toEqual({ token: 'tok', userUuid: 'u1', role: 'organizer', expiresAt: '2026-01-01' })
  })
})
