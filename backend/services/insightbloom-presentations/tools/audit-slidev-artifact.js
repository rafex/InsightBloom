#!/usr/bin/env node

/**
 * Auditor exploratorio para ZIP de Slidev precompilado.
 *
 * Este proceso nunca ejecuta el contenido del ZIP. Su salida es una señal de
 * defensa en profundidad, no una certificación de seguridad. El endpoint de
 * subida actual no invoca este script ni acepta aún artefactos precompilados.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const AdmZip = require('adm-zip');

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024;
const MAX_UNCOMPRESSED_BYTES = 250 * 1024 * 1024;
const MAX_FILES = 1000;
const MANIFEST_FILE = 'slidev-artifact.json';
const REQUIRED_ENTRY = 'dist/index.html';

const ALLOWED_EXTENSIONS = new Set([
  '.html', '.js', '.mjs', '.css', '.json', '.svg', '.png', '.jpg', '.jpeg',
  '.gif', '.webp', '.avif', '.woff', '.woff2', '.ttf', '.otf', '.mp4', '.webm',
  '.ogg', '.mp3', '.wav', '.pdf'
]);

const DENIED_BASENAMES = new Set([
  'package.json', 'package-lock.json', 'npm-shrinkwrap.json', 'pnpm-lock.yaml',
  'yarn.lock', 'vite.config.js', 'vite.config.ts', 'vite.config.mjs',
  'vite.config.cjs', 'webpack.config.js'
]);

const ALLOWED_NO_EXTENSION = new Set(['.gitkeep', '_redirects']);

const BLOCKING_RULES = [
  ['JS-AUTH-001', /\bdocument\.cookie\b|\bAuthorization\b|\b(?:ib_token|access_token|refresh_token)\b/gi, 'posible acceso a credenciales'],
  ['JS-ESC-001', /\b(?:window\.(?:top|parent)|parent\.(?:location|postMessage)|opener\b)/g, 'escape o comunicación con el contexto padre'],
  ['JS-PLAT-001', /\bnavigator\.serviceWorker\b|\bWebAssembly\b|\bimportScripts\s*\(/g, 'persistencia o plataforma adicional'],
  ['JS-IMPORT-001', /\bimport\s*\(\s*(?:['"](?:https?:|\/\/|\/)|(?:[^'"]*:\/\/))/g, 'import remoto o absoluto'],
  ['JS-DOM-001', /(?:href|src)\s*=\s*['"]\s*javascript:|\b(?:createContextualFragment|setAttribute)\s*\(\s*['"](?:src|href)['"]\s*,\s*['"]\s*javascript:/gi, 'URL javascript activa']
];

const WARNING_RULES = [
  ['JS-EXEC-001', /\beval\s*\(|\bnew\s+Function\s*\(|\bFunction\s*\(/g, 'ejecución dinámica; comparar contra el baseline de Slidev'],
  ['JS-NET-001', /\b(?:fetch|XMLHttpRequest|WebSocket|EventSource|sendBeacon)\s*\(|(?:https?:)?\/\/[^'"\s)]+/g, 'red o URL externa; validar contra la CSP y el baseline'],
  ['JS-DOM-002', /\b(?:innerHTML|outerHTML|insertAdjacentHTML)\b/g, 'inyección HTML; revisar contra el baseline de Slidev'],
  ['JS-DOM-003', /<\s*(?:iframe|object|embed)\b|\b(?:createElement|createContextualFragment)\s*\(\s*['"](?:iframe|object|embed)/gi, 'contenido activo embebido; revisar origen'],
  ['JS-STORE-001', /\b(?:localStorage|sessionStorage)\b/g, 'persistencia local; no debe guardar tokens'],
  ['JS-IMPORT-002', /\bimport\s*\(\s*['"]\.\.?\//g, 'import dinámico relativo; validar que apunte a dist/assets'],
  ['JS-NET-002', /\b(?:WebSocket|EventSource)\s*\(/g, 'canal persistente; permitir sólo la sincronización autorizada'],
  ['JS-EXT-001', /(?:https?:)?\/\/[^'"\s)]+/g, 'recurso externo; preferir assets locales']
];

function usage() {
  console.error('Uso: node tools/audit-slidev-artifact.js <archivo.zip> [--json]');
}

function normalizeEntryName(name) {
  const raw = String(name || '').replaceAll('\\', '/');
  const normalized = raw.endsWith('/') ? raw.slice(0, -1) : raw;
  if (!normalized || normalized.includes('\0') || normalized.startsWith('/') || /^[A-Za-z]:/.test(normalized)) {
    throw new Error('invalid_archive_path');
  }
  const parts = normalized.split('/');
  if (parts.some((part) => part === '..' || part === '')) throw new Error('invalid_archive_path');
  return normalized;
}

function isSymlink(entry) {
  const externalAttributes = entry.header?.externalFileAttributes || 0;
  const unixMode = (externalAttributes >>> 16) & 0xffff;
  return (unixMode & 0xf000) === 0xa000;
}

function issue(rule, severity, file, line, evidence, message) {
  return { rule, severity, file, line, evidence: evidence.slice(0, 240), message };
}

function lineNumber(text, offset) {
  return text.slice(0, offset).split('\n').length;
}

function scanText(file, text, issues) {
  for (const [rule, pattern, message] of BLOCKING_RULES) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      // Slidev incluye un acceso controlado a window.parent en su chunk de
      // overview. Se permite sólo en ese path de runtime conocido; cualquier
      // aparición en código de presentación sigue siendo bloqueante.
      if (rule === 'JS-ESC-001' && /^dist\/assets\/slidev\/overview-[^/]+\.js$/i.test(file)) continue;
      issues.push(issue(rule, 'blocking', file, lineNumber(text, match.index), match[0], message));
      if (!pattern.global) break;
    }
  }
  for (const [rule, pattern, message] of WARNING_RULES) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      issues.push(issue(rule, 'warning', file, lineNumber(text, match.index), match[0], message));
      if (!pattern.global) break;
    }
  }
}

function sha256(buffer) {
  return `sha256:${crypto.createHash('sha256').update(buffer).digest('hex')}`;
}

function readManifest(zip, issues) {
  const entry = zip.getEntry(MANIFEST_FILE);
  if (!entry) {
    issues.push(issue('ARTIFACT-001', 'blocking', MANIFEST_FILE, 1, '', 'falta el manifiesto del artefacto'));
    return null;
  }
  try {
    const manifest = JSON.parse(entry.getData().toString('utf8'));
    if (manifest.engine !== 'slidev') {
      issues.push(issue('ARTIFACT-002', 'blocking', MANIFEST_FILE, 1, String(manifest.engine || ''), 'engine distinto de slidev'));
    }
    if (manifest.artifactFormat !== 'static') {
      issues.push(issue('ARTIFACT-003', 'blocking', MANIFEST_FILE, 1, String(manifest.artifactFormat || ''), 'el formato debe ser static'));
    }
    if (manifest.base && manifest.base !== 'relative') {
      issues.push(issue('ARTIFACT-004', 'blocking', MANIFEST_FILE, 1, String(manifest.base), 'la base debe ser relative'));
    }
    if (manifest.indexFile && manifest.indexFile !== 'index.html') {
      issues.push(issue('ARTIFACT-009', 'blocking', MANIFEST_FILE, 1, String(manifest.indexFile), 'la entrada debe ser dist/index.html'));
    }
    if (!manifest.files || typeof manifest.files !== 'object' || Array.isArray(manifest.files)) {
      issues.push(issue('ARTIFACT-010', 'blocking', MANIFEST_FILE, 1, '', 'faltan los hashes files del manifiesto'));
    }
    if (!manifest.signature) {
      issues.push(issue('ARTIFACT-011', 'warning', MANIFEST_FILE, 1, '', 'el artefacto no tiene firma; se acepta sólo con la política de warnings habilitada'));
    }
    for (const exportPath of [manifest.exports?.pdf, ...(Array.isArray(manifest.previews) ? manifest.previews : [])]) {
      let normalizedPath = null;
      try { normalizedPath = exportPath ? normalizeEntryName(exportPath) : null; } catch { /* se reporta abajo */ }
      if (normalizedPath && !normalizedPath.startsWith('exports/') && !normalizedPath.startsWith('previews/')) {
        normalizedPath = null;
      }
      if (exportPath && !normalizedPath) {
        issues.push(issue('ARTIFACT-012', 'blocking', MANIFEST_FILE, 1, String(exportPath), 'ruta de exportación fuera del paquete permitido'));
      }
    }
    return manifest;
  } catch (error) {
    issues.push(issue('ARTIFACT-005', 'blocking', MANIFEST_FILE, 1, error.message, 'manifiesto JSON inválido'));
    return null;
  }
}

function auditArchive(zipPath) {
  const report = {
    tool: 'insightbloom-slidev-artifact-audit',
    version: 1,
    artifact: path.basename(zipPath),
    decision: 'ACCEPT',
    signature: 'not_checked',
    files: [],
    issues: []
  };

  const stat = fs.statSync(zipPath);
  if (!stat.isFile()) throw new Error('not_a_file');
  if (stat.size > MAX_UPLOAD_BYTES) {
    report.issues.push(issue('ARCHIVE-001', 'blocking', path.basename(zipPath), 1, String(stat.size), 'ZIP supera el límite de upload'));
  }

  const zip = new AdmZip(zipPath);
  const entries = zip.getEntries();
  let uncompressedBytes = 0;
  if (entries.length > MAX_FILES) {
    report.issues.push(issue('ARCHIVE-002', 'blocking', path.basename(zipPath), 1, String(entries.length), 'demasiadas entradas'));
  }

  for (const entry of entries) {
    let name;
    try { name = normalizeEntryName(entry.entryName); } catch (error) {
      report.issues.push(issue('ARCHIVE-003', 'blocking', entry.entryName, 1, entry.entryName, error.message));
      continue;
    }
    if (isSymlink(entry)) {
      report.issues.push(issue('ARCHIVE-004', 'blocking', name, 1, name, 'symlink no permitido'));
      continue;
    }
    if (entry.isDirectory) continue;

    const basename = path.posix.basename(name).toLowerCase();
    const extension = path.posix.extname(basename);
    uncompressedBytes += Number(entry.header?.size || 0);
    if (uncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
      report.issues.push(issue('ARCHIVE-005', 'blocking', name, 1, String(uncompressedBytes), 'tamaño descomprimido excedido'));
    }

    const allowedLocation = name === MANIFEST_FILE || name.startsWith('dist/') || name.startsWith('exports/') || name.startsWith('previews/');
    if (!allowedLocation) report.issues.push(issue('ARCHIVE-006', 'blocking', name, 1, name, 'archivo fuera de la allowlist de ubicaciones'));
    if (DENIED_BASENAMES.has(basename)) report.issues.push(issue('ARCHIVE-007', 'blocking', name, 1, basename, 'archivo de proyecto/build no permitido'));
    if (!ALLOWED_EXTENSIONS.has(extension) && !ALLOWED_NO_EXTENSION.has(basename)) {
      report.issues.push(issue('ARCHIVE-008', 'blocking', name, 1, extension || basename, 'extensión no permitida'));
    }
    if (extension === '.map') report.issues.push(issue('JS-SOURCE-001', 'blocking', name, 1, name, 'source maps no públicos'));

    const data = entry.getData();
    const digest = sha256(data);
    report.files.push({ path: name, bytes: data.length, sha256: digest });
    if (['.js', '.mjs', '.html'].includes(extension)) scanText(name, data.toString('utf8'), report.issues);
  }

  if (!zip.getEntry(REQUIRED_ENTRY)) report.issues.push(issue('ARTIFACT-006', 'blocking', REQUIRED_ENTRY, 1, '', 'falta la entrada HTML compilada'));
  const manifest = readManifest(zip, report.issues);
  if (manifest?.files && typeof manifest.files === 'object') {
    for (const declaredPath of Object.keys(manifest.files)) {
      if (!report.files.some((file) => file.path === declaredPath)) {
        report.issues.push(issue('ARTIFACT-013', 'blocking', declaredPath, 1, declaredPath, 'hash declarado para un archivo ausente'));
      }
    }
    for (const file of report.files) {
      const declared = manifest.files[file.path];
      if (declared && declared !== file.sha256) {
        report.issues.push(issue('ARTIFACT-007', 'blocking', file.path, 1, `${declared} != ${file.sha256}`, 'hash no coincide con el manifiesto'));
      }
    }
  }

  if (report.issues.some((item) => item.severity === 'blocking')) report.decision = 'REJECT';
  else if (report.issues.some((item) => item.severity === 'warning')) report.decision = 'QUARANTINE';
  return report;
}

function main() {
  const zipPath = process.argv[2];
  const json = process.argv.includes('--json');
  if (!zipPath) {
    usage();
    process.exitCode = 2;
    return;
  }
  try {
    const report = auditArchive(path.resolve(zipPath));
    if (json) console.log(JSON.stringify(report, null, 2));
    else {
      console.log(`${report.decision}: ${report.artifact}`);
      for (const item of report.issues) console.log(`- [${item.severity}] ${item.rule} ${item.file}:${item.line} — ${item.message}`);
    }
    process.exitCode = report.decision === 'ACCEPT' ? 0 : 1;
  } catch (error) {
    console.error(`ERROR: ${error.message}`);
    process.exitCode = 2;
  }
}

if (require.main === module) main();

module.exports = { auditArchive };
