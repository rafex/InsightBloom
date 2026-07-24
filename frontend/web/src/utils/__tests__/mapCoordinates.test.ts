import { describe, expect, it } from 'vitest'
import { parseMapCoordinates } from '@/utils/mapCoordinates'

describe('parseMapCoordinates', () => {
  it('extrae coordenadas de una URL de Google Maps con /@', () => {
    expect(parseMapCoordinates(
      'https://www.google.com/maps/@19.4023902,-99.1353201,16z?entry=ttu'
    )).toEqual({ latitude: 19.4023902, longitude: -99.1353201 })
  })

  it('extrae coordenadas de una URL de OpenStreetMap con #map', () => {
    expect(parseMapCoordinates(
      'https://www.openstreetmap.org/#map=16/19.4023902/-99.1353201'
    )).toEqual({ latitude: 19.4023902, longitude: -99.1353201 })
  })

  it('acepta coordenadas de query en enlaces de mapas', () => {
    expect(parseMapCoordinates('https://www.google.com/maps?q=19.4023902,-99.1353201'))
      .toEqual({ latitude: 19.4023902, longitude: -99.1353201 })
    expect(parseMapCoordinates('https://www.openstreetmap.org/?mlat=19.4023902&mlon=-99.1353201'))
      .toEqual({ latitude: 19.4023902, longitude: -99.1353201 })
  })

  it('rechaza dominios no permitidos y coordenadas fuera de rango', () => {
    expect(parseMapCoordinates('https://evil-google.com/maps/@19.4,-99.1,16z')).toBeNull()
    expect(parseMapCoordinates('https://www.google.com/maps/@91,-99,16z')).toBeNull()
    expect(parseMapCoordinates('https://www.google.com/maps/@19,-181,16z')).toBeNull()
  })
})
