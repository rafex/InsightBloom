import React from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { Excalidraw, exportToSvg, serializeAsJSON } from '@excalidraw/excalidraw'
import '@excalidraw/excalidraw/index.css'

export interface ExcalidrawScene {
  elements: any[]
  appState: Record<string, any>
  files: Record<string, any>
}
export type ExcalidrawChangeHandler = (sceneJson: string, publishedSvg: string) => void

const EMPTY_APP_STATE = {
  viewBackgroundColor: '#ffffff',
  currentItemFontFamily: 5,
  currentItemFontSize: 20,
  currentItemStrokeColor: '#1e1e1e',
  currentItemBackgroundColor: 'transparent',
  currentItemFillStyle: 'hachure',
  currentItemStrokeWidth: 1,
  currentItemRoughness: 1,
  currentItemOpacity: 100,
  currentItemStartArrowhead: null,
  currentItemEndArrowhead: 'arrow',
  currentItemRoundness: 'round',
  scrollX: 0,
  scrollY: 0,
  zoom: { value: 1 }
}

export function parseExcalidrawScene(sceneJson: string | null | undefined): ExcalidrawScene {
  if (!sceneJson) return { elements: [], appState: { ...EMPTY_APP_STATE }, files: {} }
  try {
    const parsed = JSON.parse(sceneJson)
    return {
      elements: Array.isArray(parsed.elements) ? parsed.elements : [],
      appState: { ...EMPTY_APP_STATE, ...(parsed.appState || {}) },
      files: parsed.files && typeof parsed.files === 'object' ? parsed.files : {}
    }
  } catch {
    return { elements: [], appState: { ...EMPTY_APP_STATE }, files: {} }
  }
}

function svgDataUri(svg: SVGSVGElement): string {
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg.outerHTML)}`
}

export function mountExcalidrawEditor(
  container: HTMLElement,
  sceneJson: string | null | undefined,
  onChange: ExcalidrawChangeHandler
): () => void {
  const initialScene = parseExcalidrawScene(sceneJson)
  const initialElementsSignature = JSON.stringify(initialScene.elements)
  const root: Root = createRoot(container)
  let changeTimer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  let ready = false
  let hasObservedContentChange = false

  const publish = (elements: any[], appState: any, files: Record<string, any>) => {
    if (!ready || disposed) return
    // Excalidraw calls onChange while finishing the initial scene load. Do not
    // interpret that lifecycle callback as an edit: otherwise a new event (or
    // a previously published scene) is immediately overwritten by elements: [].
    // Viewport-only changes are also local and need no publication.
    if (!hasObservedContentChange && JSON.stringify(elements) === initialElementsSignature) return
    hasObservedContentChange = true
    if (changeTimer) clearTimeout(changeTimer)
    changeTimer = setTimeout(async () => {
      if (disposed) return
      try {
        const scene = serializeAsJSON(elements, appState, files, 'local')
        const svg = await exportToSvg({
          elements: elements.filter(element => !element.isDeleted),
          appState: { ...appState, exportWithDarkMode: false },
          files
        })
        if (!disposed) onChange(scene, svgDataUri(svg))
      } catch (error) {
        // The editor remains usable if an intermediate export fails. The next
        // change retries the publication and the host can surface the error.
        console.warn('No se pudo exportar la pizarra de Excalidraw', error)
      }
    }, 750)
  }

  root.render(React.createElement(Excalidraw, {
    initialData: {
      elements: initialScene.elements,
      appState: initialScene.appState,
      files: initialScene.files,
      scrollToContent: initialScene.elements.length > 0
    },
    onChange: (elements: any[], appState: any, files: Record<string, any>) => {
      publish(elements, appState, files)
    },
    UIOptions: { canvasActions: { loadScene: false } }
  } as any))

  requestAnimationFrame(() => { ready = true })

  return () => {
    disposed = true
    if (changeTimer) clearTimeout(changeTimer)
    root.unmount()
  }
}
