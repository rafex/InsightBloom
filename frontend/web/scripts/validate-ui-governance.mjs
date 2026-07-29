import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = fileURLToPath(new URL('.', import.meta.url))
const webRoot = join(scriptDir, '..')
const sourceRoots = ['src/pages', 'src/components'].map((path) => join(webRoot, path))
const baselineLocalHex = 1278
const sourceOfTruth = 'src/styles/global.css'
const fileExtensions = new Set(['.vue', '.css'])
const hexPattern = /#[0-9a-fA-F]{6}\b/g
const forbiddenSelectors = /\.(?:btn-primary|btn-secondary|btn-danger|btn-ghost|link-btn-primary|link-btn-secondary|link-btn-ghost)\s*\{/g
const legacySelectorAllowlist = new Set([
  'src/pages/dashboard/ModerationMessagesPage.vue',
  'src/pages/dashboard/ModerationWordsPage.vue',
  'src/pages/dashboard/PresentationManagePage.vue',
  'src/pages/dashboard/SpeakerPanelPage.vue',
  'src/components/QrCodeModal.vue'
])

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectFiles(path)
    return fileExtensions.has(path.slice(path.lastIndexOf('.'))) ? [path] : []
  })
}

const files = sourceRoots.flatMap(collectFiles)
let localHexCount = 0
const selectorViolations = []

for (const file of files) {
  const source = readFileSync(file, 'utf8')
  localHexCount += source.match(hexPattern)?.length ?? 0
  for (const match of source.matchAll(forbiddenSelectors)) {
    selectorViolations.push({
      file: relative(webRoot, file),
      location: `${relative(webRoot, file)}:${source.slice(0, match.index).split('\n').length}`
    })
  }
}

const errors = []
if (localHexCount > baselineLocalHex) {
  errors.push(`colores hex locales aumentaron: ${localHexCount} > ${baselineLocalHex}`)
}
const unexpectedSelectorViolations = selectorViolations.filter(({ file }) => !legacySelectorAllowlist.has(file))
if (unexpectedSelectorViolations.length) {
  errors.push(`selectores canónicos redefinidos fuera de ${sourceOfTruth}: ${unexpectedSelectorViolations.map(({ location }) => location).join(', ')}`)
}

console.log(`UI governance: ${localHexCount} colores hex locales (baseline máximo ${baselineLocalHex})`)
console.log(`UI governance: ${files.length} archivos de páginas/componentes inspeccionados`)
if (selectorViolations.length) {
  console.warn(`UI governance: ${selectorViolations.length} redefiniciones legacy pendientes (allowlist temporal)`)
}

if (errors.length) {
  console.error(errors.map((error) => `ERROR: ${error}`).join('\n'))
  process.exitCode = 1
} else {
  console.log('UI governance: OK')
}
