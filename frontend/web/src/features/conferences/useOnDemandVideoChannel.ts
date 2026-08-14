import { onBeforeUnmount, onMounted } from 'vue'

export type OnDemandVideoMessageType =
  | 'ready'
  | 'play'
  | 'pause'
  | 'seek'
  | 'volume'
  | 'rate'
  | 'fullscreen'
  | 'state'

export interface OnDemandVideoMessage {
  version: 1
  conferenceId: string
  sourceId: string
  sequence: number
  type: OnDemandVideoMessageType
  payload: Record<string, unknown>
}

const CHANNEL_PREFIX = 'insightbloom-on-demand-video'
const MESSAGE_VERSION = 1 as const

function makeSourceId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function channelName(conferenceId: string): string {
  return `${CHANNEL_PREFIX}-${conferenceId}`
}

export function useOnDemandVideoChannel(
  conferenceId: string,
  onMessage: (message: OnDemandVideoMessage) => void
) {
  const sourceId = makeSourceId()
  let sequence = 0
  let channel: BroadcastChannel | null = null
  let receiveMessage: ((event: MessageEvent) => void) | null = null

  function isValidMessage(value: unknown): value is OnDemandVideoMessage {
    if (!value || typeof value !== 'object') return false
    const message = value as Partial<OnDemandVideoMessage>
    return message.version === MESSAGE_VERSION
      && message.conferenceId === conferenceId
      && typeof message.sourceId === 'string'
      && message.sourceId !== sourceId
      && typeof message.sequence === 'number'
      && typeof message.type === 'string'
      && !!message.payload && typeof message.payload === 'object'
  }

  function post(message: OnDemandVideoMessage) {
    channel?.postMessage(message)
    if (window.opener && window.opener !== window) {
      window.opener.postMessage(message, window.location.origin)
    }
  }

  function send(type: OnDemandVideoMessageType, payload: Record<string, unknown> = {}) {
    sequence += 1
    post({
      version: MESSAGE_VERSION,
      conferenceId,
      sourceId,
      sequence,
      type,
      payload
    })
  }

  onMounted(() => {
    if ('BroadcastChannel' in window) {
      channel = new BroadcastChannel(channelName(conferenceId))
      channel.onmessage = (event) => {
        if (isValidMessage(event.data)) onMessage(event.data)
      }
    }

    receiveMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      if (isValidMessage(event.data)) onMessage(event.data)
    }
    window.addEventListener('message', receiveMessage)
  })

  onBeforeUnmount(() => {
    if (receiveMessage) window.removeEventListener('message', receiveMessage)
    channel?.close()
    channel = null
  })

  return { sourceId, send }
}
