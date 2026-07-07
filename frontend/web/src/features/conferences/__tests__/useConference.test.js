import { describe, it, expect, vi, beforeEach } from 'vitest'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'
import { useConference } from '../useConference'

vi.mock('@/services/api/usersApi')

describe('useConference', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('loads a conference by friendly id and exposes it once resolved', async () => {
    getConferenceByFriendlyId.mockResolvedValue({ uuid: 'c1', name: 'Charla' })
    const { conference, loading, error, load } = useConference('charla-2026')

    const promise = load()
    expect(loading.value).toBe(true)
    await promise

    expect(getConferenceByFriendlyId).toHaveBeenCalledWith('charla-2026')
    expect(conference.value).toEqual({ uuid: 'c1', name: 'Charla' })
    expect(loading.value).toBe(false)
    expect(error.value).toBe('')
  })

  it('sets a friendly error message and clears loading when the lookup fails', async () => {
    getConferenceByFriendlyId.mockRejectedValue(new Error('404'))
    const { conference, loading, error, load } = useConference('no-existe')

    await load()

    expect(conference.value).toBeNull()
    expect(loading.value).toBe(false)
    expect(error.value).toBe('Conferencia no encontrada')
  })

  it('clears a previous error on a subsequent successful load', async () => {
    getConferenceByFriendlyId.mockRejectedValueOnce(new Error('404'))
    const { conference, error, load } = useConference('charla-2026')

    await load()
    expect(error.value).toBe('Conferencia no encontrada')

    getConferenceByFriendlyId.mockResolvedValueOnce({ uuid: 'c1' })
    await load()
    expect(error.value).toBe('')
    expect(conference.value).toEqual({ uuid: 'c1' })
  })
})
