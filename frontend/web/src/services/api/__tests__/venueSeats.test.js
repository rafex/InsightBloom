import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { setVenueMap, defineVenueSeats, getConferenceSeatMap, reserveSeat } from '../usersApi'

vi.mock('axios')

const BASE = '/api/users/api/v1/conferences'

describe('venue seats API', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('setVenueMap PUTs the venue image', async () => {
    axios.put.mockResolvedValue({ data: { data: { uuid: 'c1', venueMapBase64: 'data:image/png;base64,x' } } })
    await setVenueMap('c1', 'data:image/png;base64,x', 'tok')
    expect(axios.put).toHaveBeenCalledWith(`${BASE}/c1/venue-map`, { imageBase64: 'data:image/png;base64,x' },
      { headers: { Authorization: 'Bearer tok' } })
  })

  it('defineVenueSeats PUTs the full seat list', async () => {
    const seats = [{ uuid: null, label: 'A1', x: 0.1, y: 0.2 }]
    axios.put.mockResolvedValue({ data: { data: [{ uuid: 's1', label: 'A1', x: 0.1, y: 0.2 }] } })
    const result = await defineVenueSeats('c1', seats, 'tok')
    expect(axios.put).toHaveBeenCalledWith(`${BASE}/c1/seats`, { seats }, { headers: { Authorization: 'Bearer tok' } })
    expect(result).toHaveLength(1)
  })

  it('getConferenceSeatMap GETs seats with occupancy', async () => {
    axios.get.mockResolvedValue({ data: { data: [{ uuid: 's1', occupied: true }] } })
    const result = await getConferenceSeatMap('c1', 'tok')
    expect(axios.get).toHaveBeenCalledWith(`${BASE}/c1/seats`, { headers: { Authorization: 'Bearer tok' } })
    expect(result[0].occupied).toBe(true)
  })

  it('reserveSeat POSTs the chosen seatUuid', async () => {
    axios.post.mockResolvedValue({ data: { data: { uuid: 'r1', seatUuid: 's1' } } })
    const result = await reserveSeat('c1', 's1', 'tok')
    expect(axios.post).toHaveBeenCalledWith(`${BASE}/c1/reservations`, { seatUuid: 's1' },
      { headers: { Authorization: 'Bearer tok' } })
    expect(result.seatUuid).toBe('s1')
  })
})
