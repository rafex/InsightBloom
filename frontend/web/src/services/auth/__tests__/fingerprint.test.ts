import { describe, it, expect, vi, beforeEach } from 'vitest'

const { constructorSpy } = vi.hoisted(() => ({ constructorSpy: vi.fn() }))

vi.mock('@thumbmarkjs/thumbmarkjs', () => ({
  Thumbmark: class {
    constructor(options: unknown) {
      constructorSpy(options)
    }

    async get() {
      return { thumbmark: 'hash-test' }
    }
  }
}))

describe('getFingerprint', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.resetModules()
  })

  // logging viene activado por defecto en ThumbmarkJS: envia la huella a api.thumbmarkjs.com y,
  // desde 1.11, descarga y evalua un script remoto dentro del origen de la app.
  it('disables the ThumbmarkJS remote logging', async () => {
    const { getFingerprint } = await import('../fingerprint')

    await expect(getFingerprint()).resolves.toBe('hash-test')

    expect(constructorSpy).toHaveBeenCalledWith(expect.objectContaining({ logging: false }))
  })
})
