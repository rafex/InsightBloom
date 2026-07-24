import { describe, expect, it } from 'vitest'
import { isInvalidSessionResponse } from '../sessionGuard'

describe('sessionGuard', () => {
  it('treats any unauthorized response as an invalid session', () => {
    expect(isInvalidSessionResponse(401, { error: 'token_invalid' })).toBe(true)
  })

  it('treats explicit account/session blocking as an invalid session', () => {
    expect(isInvalidSessionResponse(403, { error: 'account_banned' })).toBe(true)
    expect(isInvalidSessionResponse(403, { error: 'device_blocked' })).toBe(true)
    expect(isInvalidSessionResponse(403, { message: 'La sesión fue revocada' })).toBe(true)
  })

  it('does not log out for an ordinary authorization failure', () => {
    expect(isInvalidSessionResponse(403, { error: 'forbidden', message: 'Only organizers can edit this event' })).toBe(false)
    expect(isInvalidSessionResponse(409, { error: 'ticket_required' })).toBe(false)
  })
})

