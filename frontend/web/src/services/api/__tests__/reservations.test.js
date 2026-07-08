import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  setSeatingMode, reserveGeneral, getMyTicket, cancelReservation, listReservations, checkInTicket
} from '../usersApi'

vi.mock('axios')

const BASE = '/api/users/api/v1/conferences'

describe('reservations API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('setSeatingMode PUTs mode and capacity', async () => {
    axios.put.mockResolvedValue({ data: { data: { uuid: 'c1', seatingMode: 'GENERAL', capacity: 50 } } })
    await setSeatingMode('c1', 'GENERAL', 50, 'tok')
    expect(axios.put).toHaveBeenCalledWith(`${BASE}/c1/seating`, { seatingMode: 'GENERAL', capacity: 50 },
      { headers: { Authorization: 'Bearer tok' } })
  })

  it('reserveGeneral POSTs with an empty body', async () => {
    axios.post.mockResolvedValue({ data: { data: { uuid: 'r1', ticketCode: 'abc' } } })
    const result = await reserveGeneral('c1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/c1/reservations`, {}, { headers: { Authorization: 'Bearer tok' } })
    expect(result).toEqual({ uuid: 'r1', ticketCode: 'abc' })
  })

  it('getMyTicket returns the reservation when it exists', async () => {
    axios.get.mockResolvedValue({ data: { data: { uuid: 'r1', status: 'RESERVED' } } })
    const result = await getMyTicket('c1', 'tok')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/c1/reservations/me`, { headers: { Authorization: 'Bearer tok' } })
    expect(result).toEqual({ uuid: 'r1', status: 'RESERVED' })
  })

  it('getMyTicket returns null (not an error) when there is no ticket yet', async () => {
    axios.get.mockRejectedValue({ response: { status: 404 } })
    const result = await getMyTicket('c1', 'tok')
    expect(result).toBeNull()
  })

  it('getMyTicket rethrows non-404 errors', async () => {
    const error = { response: { status: 500 } }
    axios.get.mockRejectedValue(error)
    await expect(getMyTicket('c1', 'tok')).rejects.toBe(error)
  })

  it('cancelReservation DELETEs the reservation', async () => {
    axios.delete.mockResolvedValue({ data: { data: { cancelled: true } } })
    await cancelReservation('c1', 'tok')
    expect(axios.delete).toHaveBeenCalledWith(`${BASE}/c1/reservations/me`, { headers: { Authorization: 'Bearer tok' } })
  })

  it('listReservations GETs the full list (organizer-only)', async () => {
    axios.get.mockResolvedValue({ data: { data: [{ uuid: 'r1' }, { uuid: 'r2' }] } })
    const result = await listReservations('c1', 'tok')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/c1/reservations`, { headers: { Authorization: 'Bearer tok' } })
    expect(result).toHaveLength(2)
  })

  it('checkInTicket POSTs the scanned ticketCode', async () => {
    axios.post.mockResolvedValue({ data: { data: { uuid: 'r1', status: 'CHECKED_IN' } } })
    const result = await checkInTicket('c1', 'abc-123', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/c1/reservations/check-in`, { ticketCode: 'abc-123' },
      { headers: { Authorization: 'Bearer tok' } })
    expect(result.status).toBe('CHECKED_IN')
  })
})
