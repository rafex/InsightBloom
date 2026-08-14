import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OnDemandVideoPlayer from '../OnDemandVideoPlayer.vue'

class FakeBroadcastChannel {
  onmessage: ((event: MessageEvent) => void) | null = null
  constructor(public name: string) {}
  postMessage = vi.fn()
  close = vi.fn()
}

describe('OnDemandVideoPlayer', () => {
  afterEach(() => {
    delete (window as any).YT
    vi.unstubAllGlobals()
  })

  it('does not autoplay and starts only after the manual play control', async () => {
    const playVideo = vi.fn()
    const pauseVideo = vi.fn()
    const seekTo = vi.fn()
    const MockPlayer = vi.fn(function (this: any, _element: HTMLIFrameElement, options: any) {
      this.playVideo = playVideo
      this.pauseVideo = pauseVideo
      this.seekTo = seekTo
      this.getCurrentTime = () => 0
      this.getDuration = () => 120
      this.setVolume = vi.fn()
      this.setPlaybackRate = vi.fn()
      queueMicrotask(() => options.events.onReady())
    })
    ;(window as any).YT = { Player: MockPlayer }
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)

    const wrapper = mount(OnDemandVideoPlayer, {
      props: { conferenceId: 'conference-1', provider: 'YOUTUBE', videoUrl: 'https://youtu.be/abc123' }
    })
    await flushPromises()

    expect(playVideo).not.toHaveBeenCalled()
    await wrapper.get('button.video-control').trigger('click')
    expect(playVideo).toHaveBeenCalledOnce()
    expect(seekTo).not.toHaveBeenCalledWith(expect.anything(), false)
  })
})
