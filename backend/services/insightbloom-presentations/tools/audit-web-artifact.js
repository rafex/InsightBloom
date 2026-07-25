const path = require('path');
const crypto = require('crypto');
const AdmZip = require('adm-zip');
const cheerio = require('cheerio');

const MAX_ARCHIVE_FILES = 1000;
const MAX_UNCOMPRESSED_BYTES = 250 * 1024 * 1024;
const MAX_FILE_BYTES = 25 * 1024 * 1024;
const ALLOWED_EXTENSIONS = new Set([
  '.html', '.htm', '.js', '.mjs', '.css', '.json', '.svg', '.png', '.jpg', '.jpeg',
  '.gif', '.webp', '.avif', '.woff', '.woff2', '.ttf', '.otf', '.mp4', '.webm',
  '.ogg', '.mp3', '.wav', '.txt'
]);
// Estos archivos pueden existir en un workspace de desarrollo, pero no forman parte de un
// sitio estático publicado. Se auditan por nombre y se excluyen del snapshot antes de servirlo;
// así un proyecto Node/Vite no necesita borrar su package.json para poder publicar, y nunca se
// exponen scripts de build o metadatos del proyecto en la URL pública.
const SENSITIVE_BASENAMES = new Set([
  '.env', '.env.local', '.env.production', '.npmrc', '.yarnrc', 'id_rsa',
]);
const EXCLUDED_BASENAMES = new Set([
  'package.json', 'package-lock.json', 'pnpm-lock.yaml', 'yarn.lock',
  'vite.config.js', 'vite.config.ts', 'webpack.config.js', 'dockerfile'
]);
const SECRET_NAME = /(?:^|[._-])(secret|secrets|token|password|passwd|credential|credentials|private-key|private_key)(?:[._-]|$)/i;
const BLOCKED_SCRIPT = /\b(?:document\.cookie|window\.opener|window\.parent|window\.top|document\.domain|navigator\.serviceWorker|importScripts\s*\(|WebAssembly|eval\s*\(|new\s+Function\s*\()/g;

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

function issue(rule, file, message, evidence = '') {
  return { rule, file, message, evidence: String(evidence).slice(0, 240) };
}

function isExcludedFromPublication(name) {
  return EXCLUDED_BASENAMES.has(path.posix.basename(name).toLowerCase());
}

function safeUrl(value) {
  const normalized = String(value || '').trim();
  if (!normalized || /^(?:https?:|mailto:|tel:|#|\/|\.\/?)/i.test(normalized)) return true;
  // URLs without a scheme are relative paths (for example `app.js`). Any
  // explicit scheme not listed above is treated as active content and is
  // rejected, including javascript:, data:, file: and vbscript:.
  return !/^[a-z][a-z0-9+.-]*:/i.test(normalized);
}

function safeLocalFormUrl(value) {
  const normalized = String(value || '').trim();
  if (!normalized || normalized.startsWith('#')
      || (normalized.startsWith('/') && !normalized.startsWith('//'))
      || normalized.startsWith('./')) {
    return true;
  }
  return !normalized.startsWith('//')
      && !/^(?:[a-z][a-z0-9+.-]*:|\\\\)/i.test(normalized)
      && !normalized.startsWith('../');
}

function auditHtml(file, source, issues) {
  const $ = cheerio.load(source, { decodeEntities: false });
  // Los metadatos http-equiv no ejecutan código por sí mismos. Se mantiene el
  // bloqueo específico de meta refresh más abajo, pero no debemos rechazar
  // metadatos legítimos como Content-Security-Policy o X-UA-Compatible.
  $('base, iframe, object, embed, portal').each((_, element) => {
    const tag = element.name;
    const httpEquiv = $(element).attr('http-equiv');
    issues.push(issue('HTML-ACTIVE-001', file, 'contenido HTML activo no permitido', `<${tag}${httpEquiv ? ` http-equiv="${httpEquiv}"` : ''}>`));
  });
  $('form').each((_, element) => {
    const action = $(element).attr('action') || '';
    if (!safeLocalFormUrl(action)) {
      issues.push(issue('HTML-FORM-001', file, 'el formulario solo puede enviar dentro de la publicación', action));
    }
  });
  $('*').each((_, element) => {
    for (const attribute of Object.keys(element.attribs || {})) {
      const name = attribute.toLowerCase();
      const value = $(element).attr(attribute) || '';
      if (name.startsWith('on')) issues.push(issue('HTML-SCRIPT-001', file, 'atributo inline de evento no permitido', name));
      if ((name === 'action' || name === 'formaction') && !safeLocalFormUrl(value)) {
        issues.push(issue('HTML-FORM-001', file, 'el formulario solo puede enviar dentro de la publicación', value));
      }
      if ((name === 'href' || name === 'src' || name === 'action' || name === 'formaction') && !safeUrl(value)) {
        issues.push(issue('HTML-URL-001', file, 'esquema URL no permitido', value));
      }
      if (name === 'http-equiv' && /^refresh$/i.test(value)) {
        issues.push(issue('HTML-REDIRECT-001', file, 'meta refresh no permitido', value));
      }
      if (/javascript:|vbscript:|data:text\/html/i.test(value)) {
        issues.push(issue('HTML-URL-002', file, 'URL activa no permitida', value));
      }
    }
  });
}

function auditText(file, source, issues) {
  BLOCKED_SCRIPT.lastIndex = 0;
  let match;
  while ((match = BLOCKED_SCRIPT.exec(source)) !== null) {
    issues.push(issue('JS-RUNTIME-001', file, 'patrón de escape o ejecución peligrosa', match[0]));
  }
}

function auditZipBuffer(buffer, artifactName = 'workspace.zip') {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) throw new Error('preview_archive_empty');
  const zip = new AdmZip(buffer);
  const entries = zip.getEntries();
  if (entries.length > MAX_ARCHIVE_FILES) throw new Error('archive_file_count_exceeded');

  let totalBytes = 0;
  const files = [];
  const excludedFiles = [];
  const issues = [];
  for (const entry of entries) {
    const name = normalizeEntryName(entry.entryName);
    if (isSymlink(entry)) throw new Error('archive_symlink_not_allowed');
    if (entry.isDirectory) continue;
    const basename = path.posix.basename(name).toLowerCase();
    const extension = path.posix.extname(basename);
    const declaredSize = Number(entry.header?.size || 0);
    totalBytes += declaredSize;
    if (declaredSize > MAX_FILE_BYTES) throw new Error('preview_file_size_exceeded');
    if (totalBytes > MAX_UNCOMPRESSED_BYTES) throw new Error('archive_uncompressed_size_exceeded');
    if (SENSITIVE_BASENAMES.has(basename) || SECRET_NAME.test(basename)) {
      throw new Error('preview_secret_or_project_file_not_allowed');
    }
    if (isExcludedFromPublication(name)) {
      excludedFiles.push(name);
      continue;
    }
    if (!ALLOWED_EXTENSIONS.has(extension)) throw new Error('preview_file_type_not_allowed');

    const data = entry.getData();
    if (['.html', '.htm'].includes(extension)) auditHtml(name, data.toString('utf8'), issues);
    if (['.js', '.mjs'].includes(extension)) auditText(name, data.toString('utf8'), issues);
    if (extension === '.svg') {
      const $ = cheerio.load(data.toString('utf8'), { xmlMode: true, decodeEntities: false });
      if ($('script, foreignObject, iframe, object, embed').length) {
        issues.push(issue('SVG-ACTIVE-001', name, 'SVG con contenido activo no permitido'));
      }
    }
    files.push({ path: name, bytes: data.length, sha256: crypto.createHash('sha256').update(data).digest('hex') });
  }
  if (!files.some((file) => /(^|\/)index\.html?$/i.test(file.path))) {
    throw new Error('preview_index_missing');
  }
  if (issues.length) {
    const error = new Error('preview_artifact_rejected');
    error.issues = issues;
    throw error;
  }
  files.sort((a, b) => a.path.localeCompare(b.path));
  const artifactHash = crypto.createHash('sha256')
    .update(files.map((file) => `${file.path}:${file.sha256}`).join('\n'))
    .digest('hex');
  return { artifactName, files, excludedFiles, artifactHash, totalBytes };
}

module.exports = { auditZipBuffer, normalizeEntryName, isSymlink, isExcludedFromPublication };
