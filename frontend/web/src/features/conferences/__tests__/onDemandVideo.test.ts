import { describe, expect, it } from 'vitest'
import { toEmbedUrl } from '../onDemandVideo'

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
      .toBe('https://peertube.example/videos/embed/abc123')
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/videos/watch/abc123'))
      .toBe('https://peertube.example/videos/embed/abc123')
  })

  it('passes through an already-embed PeerTube URL', () => {
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/videos/embed/abc123'))
      .toBe('https://peertube.example/videos/embed/abc123')
  })

  it('returns null for unrecognized PeerTube URL formats', () => {
    expect(toEmbedUrl('PEERTUBE', 'https://peertube.example/about')).toBeNull()
  })

  it('returns null for a malformed URL', () => {
    expect(toEmbedUrl('PEERTUBE', 'not-a-url')).toBeNull()
  })
})
