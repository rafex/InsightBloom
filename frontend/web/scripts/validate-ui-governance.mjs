import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = fileURLToPath(new URL('.', import.meta.url))
const webRoot = join(scriptDir, '..')
const sourceRoots = ['src/pages', 'src/components'].map((path) => join(webRoot, path))
const scopedSourceRoots = ['src/app', 'src/pages', 'src/components'].map((path) => join(webRoot, path))
const baselineLocalHex = 1278
const sourceOfTruth = 'src/styles/global.css'
const scopedClassificationPath = join(scriptDir, 'scoped-style-classification.json')
const scopedClassification = JSON.parse(readFileSync(scopedClassificationPath, 'utf8'))
const visualStyleExceptionsPath = join(scriptDir, 'visual-style-exceptions.json')
const visualStyleExceptions = JSON.parse(readFileSync(visualStyleExceptionsPath, 'utf8'))
const scopedCategories = new Set(['shell', 'shared-component', 'canonical-component', 'domain-screen', 'visualization', 'embedded-tool'])
const fileExtensions = new Set(['.vue', '.css'])
const hexPattern = /#[0-9a-fA-F]{6}\b/g
const forbiddenSelectors = /\.(?:btn-primary|btn-secondary|btn-danger|btn-ghost|link-btn-primary|link-btn-secondary|link-btn-ghost)\s*\{/g

function collectFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return collectFiles(path)
    return fileExtensions.has(path.slice(path.lastIndexOf('.'))) ? [path] : []
  })
}

const files = sourceRoots.flatMap(collectFiles)
const scopedFiles = scopedSourceRoots
  .flatMap(collectFiles)
  .filter((file) => readFileSync(file, 'utf8').includes('<style scoped'))
let localHexCount = 0
const localHexByFile = {}
const selectorViolations = []

for (const file of files) {
  const source = readFileSync(file, 'utf8')
  const fileHexCount = source.match(hexPattern)?.length ?? 0
  const relativeFile = relative(webRoot, file)
  localHexCount += fileHexCount
  if (fileHexCount) localHexByFile[relativeFile] = fileHexCount
  for (const match of source.matchAll(forbiddenSelectors)) {
    selectorViolations.push({
      file: relativeFile,
      location: `${relativeFile}:${source.slice(0, match.index).split('\n').length}`
    })
  }
}

const errors = []
if (localHexCount > baselineLocalHex) {
  errors.push(`colores hex locales aumentaron: ${localHexCount} > ${baselineLocalHex}`)
}
const unexpectedSelectorViolations = selectorViolations
if (unexpectedSelectorViolations.length) {
  errors.push(`selectores canónicos redefinidos fuera de ${sourceOfTruth}: ${unexpectedSelectorViolations.map(({ location }) => location).join(', ')}`)
}
const hexFiles = new Set(Object.keys(localHexByFile))
const exceptionFiles = new Set(Object.keys(visualStyleExceptions))
const unclassifiedHexFiles = [...hexFiles].filter((file) => !exceptionFiles.has(file))
const staleHexExceptions = [...exceptionFiles].filter((file) => !hexFiles.has(file))
const changedHexExceptions = Object.entries(visualStyleExceptions)
  .filter(([file, exception]) => localHexByFile[file] !== exception.count)
if (unclassifiedHexFiles.length) {
  errors.push(`colores hex sin excepción documentada: ${unclassifiedHexFiles.join(', ')}`)
}
if (staleHexExceptions.length) {
  errors.push(`excepciones de color obsoletas: ${staleHexExceptions.join(', ')}`)
}
if (changedHexExceptions.length) {
  errors.push(`conteo de colores hex cambió en excepciones: ${changedHexExceptions.map(([file, exception]) => `${file}=${localHexByFile[file] ?? 0} (esperado ${exception.count})`).join(', ')}`)
}
const scopedRelativeFiles = new Set(scopedFiles.map((file) => relative(webRoot, file)))
const missingScopedClassifications = [...scopedRelativeFiles].filter((file) => !scopedClassification[file])
const staleScopedClassifications = Object.keys(scopedClassification).filter((file) => !scopedRelativeFiles.has(file))
const invalidScopedClassifications = Object.entries(scopedClassification)
  .filter(([file, category]) => scopedRelativeFiles.has(file) && !scopedCategories.has(category))
if (missingScopedClassifications.length) {
  errors.push(`estilos scoped sin clasificación: ${missingScopedClassifications.join(', ')}`)
}
if (staleScopedClassifications.length) {
  errors.push(`clasificaciones scoped obsoletas: ${staleScopedClassifications.join(', ')}`)
}
if (invalidScopedClassifications.length) {
  errors.push(`categorías scoped inválidas: ${invalidScopedClassifications.map(([file, category]) => `${file}=${category}`).join(', ')}`)
}
const scopedCategoryCounts = Object.values(scopedClassification).reduce((counts, category) => {
  counts[category] = (counts[category] || 0) + 1
  return counts
}, {})

console.log(`UI governance: ${localHexCount} colores hex locales (baseline máximo ${baselineLocalHex})`)
console.log(`UI governance: ${files.length} archivos de páginas/componentes inspeccionados`)
console.log(`UI governance: ${Object.keys(visualStyleExceptions).length} superficies con excepciones de color documentadas (${localHexCount} literales fijados por intención)`)
console.log(`UI governance: ${scopedFiles.length} estilos scoped clasificados (${Object.entries(scopedCategoryCounts).sort(([a], [b]) => a.localeCompare(b)).map(([category, count]) => `${category}=${count}`).join(', ')})`)
if (selectorViolations.length) {
  console.warn(`UI governance: ${selectorViolations.length} redefiniciones legacy pendientes`)
}

if (errors.length) {
  console.error(errors.map((error) => `ERROR: ${error}`).join('\n'))
  process.exitCode = 1
} else {
  console.log('UI governance: OK')
}
