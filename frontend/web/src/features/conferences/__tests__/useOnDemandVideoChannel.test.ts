import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { useOnDemandVideoChannel } from '../useOnDemandVideoChannel'

class FakeBroadcastChannel {
  static instances: FakeBroadcastChannel[] = []
  onmessage: ((event: MessageEvent) => void) | null = null
  constructor(public name: string) { FakeBroadcastChannel.instances.push(this) }
  postMessage = vi.fn()
  close = vi.fn()
}

describe('useOnDemandVideoChannel', () => {
  it('accepts only same-conference messages and ignores its own source', async () => {
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    const received: unknown[] = []
    const Harness = defineComponent({
      setup() {
        useOnDemandVideoChannel('conference-1', (message) => received.push(message))
        return () => h('div')
      }
    })
    const wrapper = mount(Harness)
    await nextTick()
    const channel = FakeBroadcastChannel.instances.at(-1)!

    channel.onmessage?.({ data: { version: 1, conferenceId: 'other', sourceId: 'remote', sequence: 1, type: 'play', payload: {} } } as MessageEvent)
    channel.onmessage?.({ data: { version: 1, conferenceId: 'conference-1', sourceId: 'remote', sequence: 1, type: 'play', payload: {} } } as MessageEvent)

    expect(received).toHaveLength(1)
    wrapper.unmount()
    vi.unstubAllGlobals()
  })
})
