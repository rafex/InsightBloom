export interface FloatingVideoGeometry {
  right: number
  bottom: number
  width: number
  height: number
}

export interface FloatingVideoViewport {
  width: number
  height: number
}

export const DEFAULT_FLOATING_VIDEO_GEOMETRY: FloatingVideoGeometry = {
  right: 20,
  bottom: 20,
  width: 320,
  height: 250
}

export const FLOATING_VIDEO_MIN_WIDTH = 220
export const FLOATING_VIDEO_MIN_HEIGHT = 190
export const FLOATING_VIDEO_VIEWPORT_PADDING = 8
export const FLOATING_VIDEO_ASPECT_RATIO = 16 / 9
export const FLOATING_VIDEO_TOOLBAR_HEIGHT = 34
export const FLOATING_VIDEO_CONTROLS_HEIGHT = 46
export const FLOATING_VIDEO_CHROME_HEIGHT =
  FLOATING_VIDEO_TOOLBAR_HEIGHT + FLOATING_VIDEO_CONTROLS_HEIGHT

function finiteOr(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), Math.max(min, max))
}

export function floatingVideoHeightForWidth(width: number): number {
  return Math.round(width / FLOATING_VIDEO_ASPECT_RATIO + FLOATING_VIDEO_CHROME_HEIGHT)
}

export function floatingVideoMaxWidth(viewport: FloatingVideoViewport): number {
  const viewportWidth = Math.max(0, viewport.width - FLOATING_VIDEO_VIEWPORT_PADDING * 2)
  const viewportHeight = Math.max(0, viewport.height - FLOATING_VIDEO_VIEWPORT_PADDING * 2)
  return Math.max(
    FLOATING_VIDEO_MIN_WIDTH,
    Math.min(viewportWidth, (viewportHeight - FLOATING_VIDEO_CHROME_HEIGHT) * FLOATING_VIDEO_ASPECT_RATIO)
  )
}

export function sanitizeFloatingVideoGeometry(
  value: Partial<FloatingVideoGeometry> | null | undefined,
  viewport: FloatingVideoViewport
): FloatingVideoGeometry {
  const raw = value || {}
  const maxWidth = Math.max(FLOATING_VIDEO_MIN_WIDTH, floatingVideoMaxWidth(viewport))
  const width = clamp(finiteOr(raw.width, DEFAULT_FLOATING_VIDEO_GEOMETRY.width), FLOATING_VIDEO_MIN_WIDTH, maxWidth)
  const height = floatingVideoHeightForWidth(width)
  const maxRight = Math.max(FLOATING_VIDEO_VIEWPORT_PADDING, viewport.width - width - FLOATING_VIDEO_VIEWPORT_PADDING)
  const maxBottom = Math.max(FLOATING_VIDEO_VIEWPORT_PADDING, viewport.height - height - FLOATING_VIDEO_VIEWPORT_PADDING)
  return {
    width,
    height,
    right: clamp(finiteOr(raw.right, DEFAULT_FLOATING_VIDEO_GEOMETRY.right), FLOATING_VIDEO_VIEWPORT_PADDING, maxRight),
    bottom: clamp(finiteOr(raw.bottom, DEFAULT_FLOATING_VIDEO_GEOMETRY.bottom), FLOATING_VIDEO_VIEWPORT_PADDING, maxBottom)
  }
}

export function moveFloatingVideo(
  start: FloatingVideoGeometry,
  deltaX: number,
  deltaY: number,
  viewport: FloatingVideoViewport
): FloatingVideoGeometry {
  return sanitizeFloatingVideoGeometry({
    ...start,
    right: start.right - deltaX,
    bottom: start.bottom - deltaY
  }, viewport)
}

export function resizeFloatingVideo(
  start: FloatingVideoGeometry,
  deltaX: number,
  viewport: FloatingVideoViewport
): FloatingVideoGeometry {
  return sanitizeFloatingVideoGeometry({ ...start, width: start.width + deltaX }, viewport)
}
