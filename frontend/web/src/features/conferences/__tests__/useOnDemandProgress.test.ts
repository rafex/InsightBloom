import { describe, it, expect, beforeEach } from 'vitest'
import { getSavedProgress, saveProgress, clearProgress } from '../useOnDemandProgress'

describe('useOnDemandProgress', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('returns null when there is no saved progress', () => {
    expect(getSavedProgress('conf-1')).toBeNull()
  })

  it('saves and reads back progress for a conference', () => {
    saveProgress('conf-1', 125.7)
    expect(getSavedProgress('conf-1')).toBe(125)
  })

  it('keeps progress isolated per conference', () => {
    saveProgress('conf-1', 10)
    saveProgress('conf-2', 20)
    expect(getSavedProgress('conf-1')).toBe(10)
    expect(getSavedProgress('conf-2')).toBe(20)
  })

  it('clears progress', () => {
    saveProgress('conf-1', 42)
    clearProgress('conf-1')
    expect(getSavedProgress('conf-1')).toBeNull()
  })

  it('ignores corrupted stored values', () => {
    localStorage.setItem('ib_ondemand_progress_conf-1', 'not-a-number')
    expect(getSavedProgress('conf-1')).toBeNull()
  })

  it('ignores negative stored values', () => {
    localStorage.setItem('ib_ondemand_progress_conf-1', '-5')
    expect(getSavedProgress('conf-1')).toBeNull()
  })
})
