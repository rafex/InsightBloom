import { describe, expect, it } from 'vitest'
import { formatStatusLabel, formatTicketStatusLabel } from '../status'

describe('status labels', () => {
  it('translates shared statuses for the UI', () => {
    expect(formatStatusLabel('CHECKED_IN')).toBe('Registrado en check-in')
    expect(formatStatusLabel('UNKNOWN_STATUS')).toBe('Unknown status')
  })

  it('keeps ticket-specific detail without exposing backend enums', () => {
    expect(formatTicketStatusLabel('ISSUED')).toBe('Emitido · sin reclamar')
    expect(formatTicketStatusLabel('CHECKED_IN')).toBe('Registrado en check-in')
    expect(formatTicketStatusLabel('NEW_TICKET_STATE')).toBe('New ticket state')
  })
})
