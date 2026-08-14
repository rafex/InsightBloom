import { describe, expect, it } from 'vitest'
import { toEmbedUrl, parseCuePointsMarkdown, findCuePointToTrigger, toolLabelForPath, type CuePointToolOption } from '../onDemandVideo'
import type { OnDemandCuePoint } from '@/services/api/types'

describe('toEmbedUrl', () => {
  it('returns null when provider or url is missing', () => {
    expect(toEmbedUrl(null, 'https://youtu.be/abc123')).toBeNull()
    expect(toEmbedUrl('YOUTUBE', null)).toBeNull()
  })

  it('converts YouTube watch/short/youtu.be URLs to embed URLs', () => {
    expect(toEmbedUrl('YOUTUBE', 'https://www.youtube.com/watch?v=abc123'))
      .toBe('https://www.youtube.com/embed/abc123')
    expect(toEmbedUrl('YOUTUBE', 'https://www.youtube.com/watch?v=abc123&t=30s'))
      .toBe('https://www.youtube.com/embed/abc123')
    expect(toEmbedUrl('YOUTUBE', 'https://youtu.be/abc123'))
      .toBe('https://www.youtube.com/embed/abc123')
    expect(toEmbedUrl('YOUTUBE', 'https://www.youtube.com/shorts/abc123'))
      .toBe('https://www.youtube.com/embed/abc123')
  })

  it('passes through an already-embed YouTube URL', () => {
    expect(toEmbedUrl('YOUTUBE', 'https://www.youtube.com/embed/abc123'))
      .toBe('https://www.youtube.com/embed/abc123')
  })

  it('returns null for unrecognized YouTube URL formats', () => {
    expect(toEmbedUrl('YOUTUBE', 'https://www.youtube.com/channel/xyz')).toBeNull()
  })

  it('converts PeerTube /w/ and /videos/watch/ URLs to embed URLs', () => {
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/w/abc123'))
      .toBe('https://peertube.example/videos/embed/abc123?api=1&autoplay=0')
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/videos/watch/abc123'))
      .toBe('https://peertube.example/videos/embed/abc123?api=1&autoplay=0')
  })

  it('passes through an already-embed PeerTube URL', () => {
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/videos/embed/abc123'))
      .toBe('https://peertube.example/videos/embed/abc123?api=1&autoplay=0')
  })

  it('returns null for unrecognized PeerTube URL formats', () => {
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/about')).toBeNull()
  })

  it('returns null for a malformed URL', () => {
    expect(toEmbedUrl('PEERTUBE', 'not-a-url')).toBeNull()
  })
})

describe('parseCuePointsMarkdown', () => {
  const tools: CuePointToolOption[] = [
    { path: 'survey', label: 'Encuesta' },
    { path: 'doubts', label: 'Nube de dudas' },
    { path: 'ide', label: 'IDE' }
  ]

  it('parses valid lines with the → separator', () => {
    const result = parseCuePointsMarkdown(
      '- 0:15 Abrí la encuesta ahora → survey\n- 2:30 Mirá la nube de dudas → doubts',
      tools
    )
    expect(result).toEqual({
      cuePoints: [
        { minutes: 0, seconds: 15, label: 'Abrí la encuesta ahora', toolPath: 'survey' },
        { minutes: 2, seconds: 30, label: 'Mirá la nube de dudas', toolPath: 'doubts' }
      ]
    })
  })

  it('parses valid lines with the ASCII -> separator', () => {
    const result = parseCuePointsMarkdown('- 5:00 Probá el IDE -> ide', tools)
    expect(result).toEqual({
      cuePoints: [{ minutes: 5, seconds: 0, label: 'Probá el IDE', toolPath: 'ide' }]
    })
  })

  it('accepts the tool label (case-insensitive) instead of the path', () => {
    const result = parseCuePointsMarkdown('- 1:00 Mirá esto → encuesta', tools)
    expect(result).toEqual({
      cuePoints: [{ minutes: 1, seconds: 0, label: 'Mirá esto', toolPath: 'survey' }]
    })
  })

  it('ignores blank lines between entries', () => {
    const result = parseCuePointsMarkdown('\n- 0:15 Uno → survey\n\n- 0:30 Dos → ide\n', tools)
    expect(result).toEqual({
      cuePoints: [
        { minutes: 0, seconds: 15, label: 'Uno', toolPath: 'survey' },
        { minutes: 0, seconds: 30, label: 'Dos', toolPath: 'ide' }
      ]
    })
  })

  it('rejects a malformed line', () => {
    const result = parseCuePointsMarkdown('esto no tiene el formato correcto', tools)
    expect('errors' in result && result.errors[0]).toMatch(/Línea 1: formato inválido/)
  })

  it('rejects seconds out of range', () => {
    const result = parseCuePointsMarkdown('- 0:75 Algo → survey', tools)
    expect('errors' in result && result.errors[0]).toMatch(/segundos deben estar entre 00 y 59/)
  })

  it('rejects an unrecognized tool token', () => {
    const result = parseCuePointsMarkdown('- 0:15 Algo → nope', tools)
    expect('errors' in result && result.errors[0]).toMatch(/herramienta "nope" no reconocida/)
  })

  it('is all-or-nothing: one bad line discards the whole batch', () => {
    const result = parseCuePointsMarkdown(
      '- 0:15 Bien → survey\n- 0:30 Mal → nope',
      tools
    )
    expect('cuePoints' in result).toBe(false)
    expect('errors' in result && result.errors).toHaveLength(1)
  })

  it('returns an error for empty input', () => {
    const result = parseCuePointsMarkdown('   \n  ', tools)
    expect('errors' in result && result.errors.length).toBeGreaterThan(0)
  })
})

describe('findCuePointToTrigger', () => {
  const cuePoints: OnDemandCuePoint[] = [
    { atSeconds: 15, label: 'Uno', toolPath: 'survey' },
    { atSeconds: 90, label: 'Dos', toolPath: 'ide' }
  ]

  it('returns null when no cue point is within its trigger window', () => {
    expect(findCuePointToTrigger(cuePoints, 5, new Set())).toBeNull()
    expect(findCuePointToTrigger(cuePoints, 50, new Set())).toBeNull()
  })

  it('returns the cue point once its timestamp is reached', () => {
    expect(findCuePointToTrigger(cuePoints, 15, new Set())).toEqual(cuePoints[0])
    expect(findCuePointToTrigger(cuePoints, 17, new Set())).toEqual(cuePoints[0])
  })

  it('stops matching once the 3s trigger window passes', () => {
    expect(findCuePointToTrigger(cuePoints, 18, new Set())).toBeNull()
  })

  it('does not re-trigger a cue point already in alreadyFiredSeconds', () => {
    expect(findCuePointToTrigger(cuePoints, 15, new Set([15]))).toBeNull()
  })

  it('can match a later cue point independently of an earlier fired one', () => {
    expect(findCuePointToTrigger(cuePoints, 90, new Set([15]))).toEqual(cuePoints[1])
  })
})

describe('toolLabelForPath', () => {
  it('returns the Spanish label for a known tool path', () => {
    expect(toolLabelForPath('survey')).toBe('Encuesta')
    expect(toolLabelForPath('ide')).toBe('IDE')
  })

  it('falls back to the raw path for an unknown tool', () => {
    expect(toolLabelForPath('nope')).toBe('nope')
  })
})
