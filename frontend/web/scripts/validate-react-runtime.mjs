import { readFileSync } from 'node:fs'

const packageJson = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'))

function majorOf(range, dependency) {
  const match = String(range || '').match(/\d+/)
  if (!match) throw new Error(`No se pudo resolver el major de ${dependency}: ${range}`)
  return Number(match[0])
}

function assertMatchingMajor(left, right, dependencies) {
  const leftMajor = majorOf(dependencies[left], left)
  const rightMajor = majorOf(dependencies[right], right)
  if (leftMajor !== rightMajor) {
    throw new Error(`${left} (${dependencies[left]}) y ${right} (${dependencies[right]}) deben usar el mismo major`)
  }
}

assertMatchingMajor('react', 'react-dom', packageJson.dependencies)
assertMatchingMajor('@types/react', '@types/react-dom', packageJson.devDependencies)

console.log('React runtime dependency contract: OK')
