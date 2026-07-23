const path = require('path');
const fs = require('fs');
const http = require('http');
const crypto = require('crypto');
const { execFile } = require('child_process');
const express = require('express');
const multer = require('multer');
const AdmZip = require('adm-zip');
const cheerio = require('cheerio');
const { chromium } = require('playwright-chromium');
const { attachLiveSync, issueRemoteToken } = require('./live');
const { auditArchive } = require('./tools/audit-slidev-artifact');
const { presentationCookiePath, hasConferenceAccess: accessFromResponse } = require('./access');

const PREVIEW_SLIDE_LIMIT = 5;

const PORT = process.env.PORT || 8091;
const DATA_DIR = process.env.DATA_DIR || '/data';
const USERS_URL = process.env.USERS_URL || 'http://insightbloom-users:8081';
const FRONTEND_BASE_URL = process.env.FRONTEND_BASE_URL || 'https://insightbloom.v1.rafex.cloud';
const NATS_URL = process.env.NATS_URL || '';
const NATS_AUTH_TOKEN = process.env.NATS_AUTH_TOKEN || '';
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY || '';
const MARP_BIN = path.join(__dirname, 'node_modules', '.bin', 'marp');
const SLIDEV_BIN = path.join(__dirname, 'node_modules', '.bin', 'slidev');
const CHROMIUM_PATH = process.env.CHROME_PATH || '/usr/bin/chromium';
const PRESENTATION_MANIFEST = 'manifest.json';
const SLIDEV_ARTIFACT_MANIFEST = 'slidev-artifact.json';
const DEFAULT_PRESENTATION_PROVIDER = 'MARP';
const PRESENTATION_PROVIDERS = new Set(['MARP', 'SLIDEV']);
const SLIDEV_FAT_ENABLED = process.env.SLIDEV_FAT_ENABLED === 'true';
const SLIDEV_FAT_ALLOW_WARNINGS = process.env.SLIDEV_FAT_ALLOW_WARNINGS === 'true';
const OFFLINE_PRESENTATION_TTL_MS = Math.min(
  Math.max(Number(process.env.OFFLINE_PRESENTATION_TTL_MS || 24 * 60 * 60 * 1000), 60 * 60 * 1000),
  7 * 24 * 60 * 60 * 1000,
);
const OFFLINE_MANIFEST_PRIVATE_KEY = process.env.OFFLINE_MANIFEST_PRIVATE_KEY || '';
const OFFLINE_MANIFEST_PUBLIC_KEY = process.env.OFFLINE_MANIFEST_PUBLIC_KEY || '';
const MAX_UNCOMPRESSED_ZIP_BYTES = 250 * 1024 * 1024;
const MAX_ARCHIVE_FILES = 1000;
const UPLOAD_RATE_WINDOW_MS = 60 * 1000;
const MAX_UPLOADS_PER_IP = 5;
const ALLOWED_ARCHIVE_EXTENSIONS = new Set([
  '.md', '.css', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.avif',
  '.woff', '.woff2', '.ttf', '.otf', '.mp4', '.webm', '.ogg', '.mp3', '.wav'
]);
const DENIED_ARCHIVE_NAMES = new Set([
  'package.json', 'package-lock.json', 'npm-shrinkwrap.json', 'vite.config.js',
  'vite.config.ts', 'vite.config.mjs', 'vite.config.cjs', 'webpack.config.js'
]);

const upload = multer({ dest: path.join(DATA_DIR, 'tmp'), limits: { fileSize: 100 * 1024 * 1024 } });
const uploadRate = new Map();

const app = express();
let certificateBrowserPromise = null;

app.disable('x-powered-by');
app.use((_req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=(), payment=()');
  if (_req.secure || _req.headers['x-forwarded-proto'] === 'https') {
    res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
  }
  next();
});

function certificateBrowser() {
  if (!certificateBrowserPromise) {
    certificateBrowserPromise = chromium.launch({
      headless: true,
      executablePath: CHROMIUM_PATH,
      args: ['--no-sandbox', '--disable-dev-shm-usage'],
    }).catch((error) => {
      certificateBrowserPromise = null;
      throw error;
    });
  }
  return certificateBrowserPromise;
}

function certificateEscape(value) {
  return String(value == null ? '' : value)
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

function certificateValue(data, key) {
  return key.split('.').reduce((current, part) => current && typeof current === 'object' ? current[part] : '', data) ?? '';
}

function certificateText(value, data) {
  const source = String(value == null ? '' : value);
  const placeholder = /\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g;
  let output = '';
  let lastIndex = 0;
  let match;
  while ((match = placeholder.exec(source)) !== null) {
    output += certificateEscape(source.slice(lastIndex, match.index));
    output += certificateEscape(certificateValue(data, match[1]));
    lastIndex = match.index + match[0].length;
  }
  return output + certificateEscape(source.slice(lastIndex));
}

function certificateStyle(style = {}) {
  const css = [];
  const number = (value, suffix = 'px') => Number.isFinite(Number(value)) ? `${Math.max(-2000, Math.min(2000, Number(value)))}${suffix}` : null;
  const color = value => typeof value === 'string' && /^(#[0-9a-f]{3,8}|rgba?\([0-9., %]+\)|transparent)$/i.test(value) ? value : null;
  const text = value => typeof value === 'string' && value.length <= 120 && /^[a-zA-Z0-9 .,()'"_-]+$/.test(value) ? value : null;
  const add = (key, value) => { if (value != null) css.push(`${key}:${value}`); };
  add('font-size', number(style.fontSize));
  add('font-weight', ['400', '500', '600', '700', '800'].includes(String(style.fontWeight)) ? String(style.fontWeight) : null);
  add('font-family', ['Arial', 'Georgia', 'Verdana', 'Courier New', 'system-ui', 'sans-serif'].includes(style.fontFamily) ? style.fontFamily : null);
  add('color', color(style.color));
  add('background', color(style.background));
  add('text-align', ['left', 'center', 'right'].includes(style.textAlign) ? style.textAlign : null);
  add('line-height', number(style.lineHeight, ''));
  const border = typeof style.border === 'string' && /^(none|\d{1,3}px\s+solid\s+#[0-9a-f]{3,8})$/i.test(style.border) ? style.border : null;
  add('border', border);
  add('border-radius', number(style.borderRadius));
  add('padding', number(style.padding));
  add('opacity', Number.isFinite(Number(style.opacity)) ? Math.max(0, Math.min(1, Number(style.opacity))) : null);
  return css.join(';');
}

function certificateBlock(block, data) {
  if (!block || typeof block !== 'object' || !['text', 'image', 'shape'].includes(block.type)) return '';
  const n = value => Number.isFinite(Number(value)) ? Math.max(-2000, Math.min(3000, Number(value))) : 0;
  const position = `left:${n(block.x)}px;top:${n(block.y)}px;width:${Math.max(1, n(block.width))}px;height:${Math.max(1, n(block.height))}px;`;
  const style = `${position}${certificateStyle(block.style || {})}`;
  if (block.type === 'text') return `<div class="certificate-block" style="${style};white-space:pre-wrap;overflow:hidden">${certificateText(block.text, data)}</div>`;
  if (block.type === 'shape') return `<div class="certificate-block" aria-hidden="true" style="${style}"></div>`;
  const src = typeof block.src === 'string' && /^data:image\/(png|jpeg|gif|webp|svg\+xml);base64,[a-z0-9+/=]+$/i.test(block.src) ? block.src : '';
  return src ? `<img class="certificate-block" alt="" src="${src}" style="${style};object-fit:contain">` : '';
}

function certificateDocument(documentJson, data) {
  if (typeof documentJson !== 'string' || documentJson.length > 200000) throw new Error('invalid_certificate_document');
  const document = JSON.parse(documentJson);
  if (!document || typeof document !== 'object' || !Array.isArray(document.blocks) || document.blocks.length > 100) throw new Error('invalid_certificate_document');
  const page = document.page && typeof document.page === 'object' ? document.page : {};
  const background = typeof page.background === 'string' && /^(#[0-9a-f]{3,8}|rgba?\([0-9., %]+\)|transparent)$/i.test(page.background) ? page.background : '#ffffff';
  return `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
    @page { size: 11in 8.5in; margin: 0; }
    html,body { margin:0; padding:0; background:#e5e7eb; }
    .page { position:relative; width:1056px; height:816px; overflow:hidden; background:${background}; box-sizing:border-box; }
    .certificate-block { position:absolute; box-sizing:border-box; }
  </style></head><body><main class="page">${document.blocks.map(block => certificateBlock(block, data)).join('')}</main></body></html>`;
}

app.post('/internal/v1/certificates/render', express.json({ limit: '300kb' }), async (req, res) => {
  if (!constantTimeHeaderMatches(req.headers['x-internal-api-key'], INTERNAL_API_KEY)) {
    return res.status(403).json({ error: 'forbidden' });
  }
  try {
    const html = certificateDocument(req.body?.documentJson, req.body?.data || {});
    const browser = await certificateBrowser();
    const context = await browser.newContext({ javaScriptEnabled: false });
    const page = await context.newPage();
    await page.setContent(html, { waitUntil: 'load', timeout: 30000 });
    const pdf = await page.pdf({ printBackground: true, preferCSSPageSize: true });
    await context.close();
    res.type('application/pdf').set('Content-Disposition', 'inline').send(pdf);
  } catch (error) {
    console.error('certificate_render_failed', error.message);
    res.status(400).json({ error: 'certificate_render_failed' });
  }
});

function constantTimeHeaderMatches(value, expected) {
  if (typeof value !== 'string' || !expected) return false;
  const actual = Buffer.from(value, 'utf8');
  const target = Buffer.from(expected, 'utf8');
  return actual.length === target.length && crypto.timingSafeEqual(actual, target);
}

function presentationUploadRateLimit(req, res, next) {
  const now = Date.now();
  if (uploadRate.size > 10_000) {
    for (const [key, value] of uploadRate) {
      if (now - value.startedAt >= UPLOAD_RATE_WINDOW_MS) uploadRate.delete(key);
    }
  }
  const key = req.ip || req.socket.remoteAddress || 'unknown';
  const current = uploadRate.get(key);
  if (!current || now - current.startedAt >= UPLOAD_RATE_WINDOW_MS) {
    uploadRate.set(key, { startedAt: now, count: 1 });
    return next();
  }
  if (current.count >= MAX_UPLOADS_PER_IP) {
    res.setHeader('Retry-After', String(Math.ceil((UPLOAD_RATE_WINDOW_MS - (now - current.startedAt)) / 1000)));
    return res.status(429).json({ error: 'upload_rate_limited', message: 'Demasiadas cargas; inténtalo más tarde' });
  }
  current.count += 1;
  return next();
}

// Presentation responses depend on the event, the current ticket/role and the
// latest uploaded artifact. Never let a browser, proxy or service worker reuse
// an old access error or an older presentation after those values change.
app.use('/api/v1/conferences/:id/presentation', (_req, res, next) => {
  res.setHeader('Cache-Control', 'no-store, max-age=0');
  next();
});

// PDF/miniatura y previews Slidev se generan bajo demanda con el engine activo y
// Chromium headless; se cachean en disco y estos mapas deduplican generaciones
// concurrentes para la misma conferencia.
const pdfGenerations = new Map();
const thumbnailGenerations = new Map();
const slidevPreviewGenerations = new Map();
// Slidev/Vite puede consumir cientos de MB durante la compilación. Mantener una
// sola ejecución del engine por pod evita que dos cargas o una vista previa
// concurrentes superen el límite de memoria del contenedor.
let slidevBuildChain = Promise.resolve();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function validConferenceId(id) {
  return typeof id === 'string' && UUID_RE.test(id);
}

function requestToken(req) {
  const authorization = req.headers.authorization || '';
  if (authorization.startsWith('Bearer ')) return authorization.slice(7);
  const cookieHeader = req.headers.cookie || '';
  const tokenCookie = cookieHeader.split(';').map((cookie) => cookie.trim()).find((cookie) => cookie.startsWith('ib_token='));
  if (!tokenCookie) return null;
  try { return decodeURIComponent(tokenCookie.slice('ib_token='.length)); } catch { return null; }
}

function setPresentationAccessCookie(req, res, conferenceId) {
  const token = requestToken(req);
  if (!token) return;
  const secure = req.secure || req.headers['x-forwarded-proto'] === 'https' ? '; Secure' : '';
  // Nginx removes /api/presentations before forwarding to this service. The
  // cookie is stored by the browser before that rewrite, so using /api/v1 here
  // silently prevents it from being sent to the iframe and WebSocket.
  res.setHeader('Set-Cookie', `ib_token=${encodeURIComponent(token)}; Path=${presentationCookiePath(conferenceId)}; HttpOnly; SameSite=Lax${secure}`);
}

async function hasConferenceAccess(conferenceId, token) {
  try {
    const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
    const response = await fetch(`${USERS_URL}/api/v1/conferences/${conferenceId}/access`, {
      headers,
    });
    if (!response.ok) return false;
    const body = await response.json();
    // Open events need no token; staff and presentation managers are authorized
    // by role and must not depend on an attendee ticket.
    return accessFromResponse(body);
  } catch {
    return false;
  }
}

async function requireConferenceAccess(req, res) {
  if (constantTimeHeaderMatches(req.headers['x-internal-api-key'], INTERNAL_API_KEY)) return true;
  if (await hasConferenceAccess(req.params.id, requestToken(req))) return true;
  res.status(403).json({ error: 'ticket_required', message: 'Registro y boleto requeridos' });
  return false;
}

async function hasPresentationManagementAccess(conferenceId, token) {
  if (!token) return false;
  try {
    const response = await fetch(`${USERS_URL}/api/v1/conferences/${conferenceId}/presentation-access`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function requirePresentationManagement(req, res, next) {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!(await hasPresentationManagementAccess(req.params.id, requestToken(req)))) {
    return res.status(403).json({ error: 'presentation_management_required' });
  }
  return next();
}

function conferenceDir(conferenceId) {
  return path.join(DATA_DIR, 'presentations', conferenceId);
}

function manifestPath(conferenceId) {
  return path.join(conferenceDir(conferenceId), PRESENTATION_MANIFEST);
}

function normalizeProvider(value, { allowMissing = false } = {}) {
  if (value == null || value === '') return allowMissing ? DEFAULT_PRESENTATION_PROVIDER : null;
  const provider = String(value).trim().toUpperCase();
  return PRESENTATION_PROVIDERS.has(provider) ? provider : null;
}

function readManifest(conferenceId) {
  const file = manifestPath(conferenceId);
  if (fs.existsSync(file)) {
    try {
      const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
      const provider = normalizeProvider(manifest.provider, { allowMissing: true });
      if (provider) return { format: manifest.format || 'source', ...manifest, provider };
    } catch {
      // Un manifiesto incompleto no debe impedir que las presentaciones Marp
      // antiguas sigan funcionando.
    }
  }
  return {
    provider: DEFAULT_PRESENTATION_PROVIDER,
    format: 'source',
    staticRoot: 'src',
    indexFile: 'slides.html',
    legacy: true,
  };
}

function presentationStaticRoot(conferenceId, manifest = readManifest(conferenceId)) {
  return path.join(conferenceDir(conferenceId), manifest.staticRoot || 'src');
}

function presentationIndexFile(conferenceId, manifest = readManifest(conferenceId)) {
  return path.join(presentationStaticRoot(conferenceId, manifest), manifest.indexFile || 'slides.html');
}

function contentTypeFor(file) {
  const extension = path.extname(file).toLowerCase();
  const types = {
    '.html': 'text/html; charset=utf-8', '.htm': 'text/html; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8', '.mjs': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
    '.gif': 'image/gif', '.webp': 'image/webp', '.avif': 'image/avif',
    '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.otf': 'font/otf',
    '.mp4': 'video/mp4', '.webm': 'video/webm', '.ogg': 'audio/ogg', '.mp3': 'audio/mpeg', '.wav': 'audio/wav',
  };
  return types[extension] || 'application/octet-stream';
}

function listPresentationFiles(rootDir) {
  const files = [];
  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { continue; }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.isFile()) {
        const relative = path.relative(rootDir, full).split(path.sep).join('/');
        const data = fs.readFileSync(full);
        files.push({
          path: relative,
          size: data.byteLength,
          sha256: crypto.createHash('sha256').update(data).digest('hex'),
          contentType: contentTypeFor(full),
        });
      }
    }
  }
  return files.sort((a, b) => a.path.localeCompare(b.path));
}

function canonicalOfflineManifest(manifest) {
  return JSON.stringify({
    conferenceId: manifest.conferenceId,
    provider: manifest.provider,
    format: manifest.format,
    indexPath: manifest.indexPath,
    artifactHash: manifest.artifactHash,
    expiresAt: manifest.expiresAt,
    files: manifest.files,
  });
}

function offlineManifestKeyMaterial() {
  if (!OFFLINE_MANIFEST_PRIVATE_KEY) return null;
  try {
    const privateKey = crypto.createPrivateKey(OFFLINE_MANIFEST_PRIVATE_KEY.replaceAll('\\n', '\n'));
    const derivedPublicKey = crypto.createPublicKey(privateKey);
    const derivedPublicKeyBase64 = derivedPublicKey.export({ type: 'spki', format: 'der' }).toString('base64');
    const configuredPublicKey = OFFLINE_MANIFEST_PUBLIC_KEY.trim();
    if (configuredPublicKey) {
      const configuredKey = crypto.createPublicKey(
        configuredPublicKey.includes('BEGIN')
          ? configuredPublicKey.replaceAll('\\n', '\n')
          : { key: Buffer.from(configuredPublicKey, 'base64'), type: 'spki', format: 'der' },
      );
      const configuredPublicKeyBase64 = configuredKey.export({ type: 'spki', format: 'der' }).toString('base64');
      if (configuredPublicKeyBase64 !== derivedPublicKeyBase64) throw new Error('offline_manifest_key_mismatch');
    }
    return { privateKey, publicKeyBase64: derivedPublicKeyBase64 };
  } catch (err) {
    console.error('offline_manifest_key_invalid', err.message);
    return null;
  }
}

function signOfflineManifest(manifest) {
  const keys = offlineManifestKeyMaterial();
  if (!keys) return null;
  try {
    const payload = Buffer.from(canonicalOfflineManifest(manifest), 'utf8');
    return {
      signedPayload: payload.toString('base64'),
      signature: crypto.sign(null, payload, keys.privateKey).toString('base64'),
    };
  } catch (err) {
    console.error('offline_manifest_signing_failed', err.message);
    return null;
  }
}

function zipEntryIsSymlink(entry) {
  const externalAttributes = entry.header?.externalFileAttributes || 0;
  const unixMode = (externalAttributes >>> 16) & 0xffff;
  return (unixMode & 0xf000) === 0xa000;
}

function normalizeArchiveEntryName(entryName) {
  const raw = String(entryName || '').replaceAll('\\', '/');
  const normalized = raw.endsWith('/') ? raw.slice(0, -1) : raw;
  if (!normalized || normalized.includes('\0') || normalized.startsWith('/') || /^[A-Za-z]:/.test(normalized)) {
    throw new Error('invalid_archive_path');
  }
  const parts = normalized.split('/');
  if (parts.some((part) => part === '..' || part === '')) {
    throw new Error('invalid_archive_path');
  }
  return normalized;
}

function fatArtifactInZip(zip) {
  return zip.getEntry(SLIDEV_ARTIFACT_MANIFEST) != null;
}

function parseFatManifest(zip) {
  const entry = zip.getEntry(SLIDEV_ARTIFACT_MANIFEST);
  if (!entry) throw new Error('slidev_fat_manifest_missing');
  let manifest;
  try { manifest = JSON.parse(entry.getData().toString('utf8')); } catch { throw new Error('slidev_fat_manifest_invalid'); }
  if (manifest.engine !== 'slidev' || manifest.artifactFormat !== 'static') throw new Error('slidev_fat_manifest_invalid');
  if (manifest.indexFile && manifest.indexFile !== 'index.html') throw new Error('slidev_fat_manifest_invalid');
  return manifest;
}

function validateArchive(zip) {
  const entries = zip.getEntries();
  if (entries.length > MAX_ARCHIVE_FILES) throw new Error('archive_file_count_exceeded');
  let totalBytes = 0;
  for (const entry of entries) {
    if (zipEntryIsSymlink(entry)) throw new Error('archive_symlink_not_allowed');
    const normalizedName = normalizeArchiveEntryName(entry.entryName);
    if (entry.isDirectory) continue;

    const basename = path.posix.basename(normalizedName).toLowerCase();
    const extension = path.posix.extname(basename);
    if (DENIED_ARCHIVE_NAMES.has(basename) || ['.js', '.mjs', '.cjs', '.ts', '.tsx', '.jsx', '.vue', '.sh'].includes(extension)) {
      throw new Error('archive_file_type_not_allowed');
    }
    if (!ALLOWED_ARCHIVE_EXTENSIONS.has(extension)) throw new Error('archive_file_type_not_allowed');

    totalBytes += Number(entry.header?.size || 0);
    if (totalBytes > MAX_UNCOMPRESSED_ZIP_BYTES) throw new Error('archive_uncompressed_size_exceeded');
  }
}

function sanitizeSvg(buffer) {
  const $ = cheerio.load(buffer.toString('utf8'), { xmlMode: true, decodeEntities: false });
  $('script, foreignObject, iframe, object, embed, audio, video').remove();
  $('*').each((_, element) => {
    for (const attribute of Object.keys(element.attribs || {})) {
      const name = attribute.toLowerCase();
      const value = $(element).attr(attribute) || '';
      if (name.startsWith('on') || /javascript:|data:text\/html/i.test(value)) {
        $(element).removeAttr(attribute);
      }
    }
  });
  return Buffer.from($.xml(), 'utf8');
}

function extractArchiveSafely(zip, destination) {
  validateArchive(zip);
  for (const entry of zip.getEntries()) {
    const normalizedName = normalizeArchiveEntryName(entry.entryName);
    const target = path.join(destination, ...normalizedName.split('/'));
    if (entry.isDirectory) {
      fs.mkdirSync(target, { recursive: true });
      continue;
    }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const data = entry.getData();
    fs.writeFileSync(target, path.posix.extname(normalizedName).toLowerCase() === '.svg'
      ? sanitizeSvg(data)
      : data);
  }
}

function extractFatArchiveSafely(zip, destination) {
  const manifest = parseFatManifest(zip);
  for (const entry of zip.getEntries()) {
    const normalizedName = normalizeArchiveEntryName(entry.entryName);
    const allowedLocation = normalizedName === SLIDEV_ARTIFACT_MANIFEST
      || normalizedName.startsWith('dist/')
      || normalizedName.startsWith('exports/')
      || normalizedName.startsWith('previews/');
    if (!allowedLocation) throw new Error('slidev_fat_file_not_allowed');
    const target = path.join(destination, ...normalizedName.split('/'));
    if (entry.isDirectory) {
      fs.mkdirSync(target, { recursive: true });
      continue;
    }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const data = entry.getData();
    fs.writeFileSync(target, path.posix.extname(normalizedName).toLowerCase() === '.svg'
      ? sanitizeSvg(data)
      : data);
  }
  const index = path.join(destination, 'dist', 'index.html');
  if (!fs.existsSync(index)) throw new Error('slidev_fat_index_missing');
  return manifest;
}

function findMarkdownFiles(rootDir) {
  const files = [];
  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { continue; }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.name.toLowerCase().endsWith('.md')) files.push(full);
    }
  }
  return files;
}

function findPresentationEntry(srcDir, provider) {
  const markdownFiles = findMarkdownFiles(srcDir);
  if (!markdownFiles.length) throw new Error('no_markdown_found_in_zip');
  if (provider === 'SLIDEV') {
    const slidevEntry = markdownFiles.find((file) => path.basename(file).toLowerCase() === 'slides.md');
    if (slidevEntry) return slidevEntry;
    if (markdownFiles.length === 1) return markdownFiles[0];
    throw new Error('slidev_entry_ambiguous');
  }
  return markdownFiles[0];
}

function presentationBasePath(conferenceId) {
  // The service is published behind the frontend proxy at /api/presentations.
  // Slidev writes absolute asset URLs into index.html, so using the internal
  // service path (/api/v1/...) makes every JS/CSS request bypass the proxy and
  // leaves the embedded presentation blank.
  const publicPrefix = process.env.PRESENTATIONS_PUBLIC_PREFIX || '/api/presentations/api/v1';
  return `${publicPrefix}/conferences/${conferenceId}/presentation/`;
}

function replaceActivePresentation(conferenceId, stagingDir) {
  const activeDir = conferenceDir(conferenceId);
  const backupDir = `${activeDir}.previous-${crypto.randomUUID()}`;
  let backedUp = false;
  try {
    if (fs.existsSync(activeDir)) {
      fs.renameSync(activeDir, backupDir);
      backedUp = true;
    }
    fs.renameSync(stagingDir, activeDir);
    if (backedUp) fs.rmSync(backupDir, { recursive: true, force: true });
  } catch (err) {
    if (fs.existsSync(activeDir) && backedUp) fs.rmSync(activeDir, { recursive: true, force: true });
    if (backedUp && fs.existsSync(backupDir)) fs.renameSync(backupDir, activeDir);
    throw err;
  }
}

function findFile(rootDir, predicate, maxDepth = 4) {
  const stack = [{ dir: rootDir, depth: 0 }];
  while (stack.length) {
    const { dir, depth } = stack.pop();
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { continue; }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (depth < maxDepth) stack.push({ dir: full, depth: depth + 1 });
      } else if (predicate(entry.name, full)) {
        return full;
      }
    }
  }
  return null;
}

// Extrae un campo escalar simple (title, description, ...) del frontmatter YAML de Marp
// (delimitado por --- ... ---). No se usa un parser YAML completo a propósito.
function extractFrontmatterField(markdown, field) {
  const match = markdown.match(/^---\s*\n([\s\S]*?)\n---/);
  if (!match) return null;
  const re = new RegExp(`^${field}\\s*:`, 'i');
  const line = match[1].split('\n').find((l) => re.test(l));
  if (!line) return null;
  const value = line.replace(re, '').trim();
  return value.replace(/^["']|["']$/g, '') || null;
}

function extractFrontmatterTitle(markdown) {
  return extractFrontmatterField(markdown, 'title');
}

function extractFrontmatterDescription(markdown) {
  return extractFrontmatterField(markdown, 'description');
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function sanitizeGeneratedMarpHtml(file) {
  const $ = cheerio.load(fs.readFileSync(file, 'utf8'), { decodeEntities: false });
  $('script, iframe, object, embed, link[rel="import"]').remove();
  $('*').each((_, element) => {
    for (const attribute of Object.keys(element.attribs || {})) {
      if (attribute.toLowerCase().startsWith('on')) $(element).removeAttr(attribute);
    }
    const href = $(element).attr('href');
    if (href && /^\s*javascript:/i.test(href)) $(element).removeAttr('href');
    const src = $(element).attr('src');
    if (src && /^\s*javascript:/i.test(src)) $(element).removeAttr('src');
  });
  fs.writeFileSync(file, $.html());
}

async function deriveConferenceName(conferenceId, title) {
  if (!title || !INTERNAL_API_KEY) return;
  try {
    const res = await fetch(`${USERS_URL}/api/v1/conferences/${conferenceId}/derive-name`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Auth': INTERNAL_API_KEY },
      body: JSON.stringify({ title })
    });
    if (!res.ok) console.error('derive_conference_name_failed', conferenceId, res.status);
  } catch (err) {
    console.error('derive_conference_name_error', conferenceId, err.message);
  }
}

// Registra una descarga de PDF (best-effort, no bloquea ni falla la descarga en curso).
async function recordDownload(conferenceId, kind) {
  if (!INTERNAL_API_KEY) return;
  try {
    const res = await fetch(`${USERS_URL}/api/v1/conferences/${conferenceId}/downloads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Internal-Auth': INTERNAL_API_KEY },
      body: JSON.stringify({ kind })
    });
    if (!res.ok) console.error('record_download_failed', conferenceId, kind, res.status);
  } catch (err) {
    console.error('record_download_error', conferenceId, kind, err.message);
  }
}

function runMarp(args) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      MARP_BIN,
      args,
      { timeout: 120000, stdio: ['ignore', 'pipe', 'pipe'] },
      (err, stdout, stderr) => {
        if (err) return reject(new Error(stderr || stdout || err.message));
        resolve(stdout);
      }
    );
    child.stdin?.end();
  });
}

function runSlidev(args) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      SLIDEV_BIN,
      args,
      { timeout: 180000, stdio: ['ignore', 'pipe', 'pipe'], env: { ...process.env, PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD: '1' } },
      (err, stdout, stderr) => {
        if (err) return reject(new Error(stderr || stdout || err.message));
        resolve(stdout);
      }
    );
    child.stdin?.end();
  });
}

function runSlidevSerialized(args) {
  const generation = slidevBuildChain.catch(() => {}).then(() => runSlidev(args));
  slidevBuildChain = generation.catch(() => {});
  return generation;
}

async function buildPresentation(provider, conferenceId, stagingDir, srcDir) {
  const mdFile = findPresentationEntry(srcDir, provider);
  const title = extractFrontmatterTitle(fs.readFileSync(mdFile, 'utf8'));

  if (provider === 'MARP') {
    const themeFile = findFile(srcDir, (name, full) => name === 'theme.css' && full.includes(`${path.sep}css${path.sep}`));
    const slidesHtml = path.join(srcDir, 'slides.html');
    const baseArgs = [mdFile, '--allow-local-files', '--html'];
    if (themeFile) baseArgs.push('--theme', themeFile);
    await runMarp([...baseArgs, '-o', slidesHtml]);
    sanitizeGeneratedMarpHtml(slidesHtml);
    return {
      provider,
      format: 'source',
      staticRoot: 'src',
      indexFile: 'slides.html',
      sourceFile: path.relative(stagingDir, mdFile),
      engineVersion: 'marp-cli',
      title,
    };
  }

  const distDir = path.join(stagingDir, 'dist');
  fs.mkdirSync(distDir, { recursive: true });
  const startedAt = Date.now();
  console.log('slidev_build_started', conferenceId);
  await runSlidevSerialized([
    'build', mdFile,
    '--out', distDir,
    '--base', presentationBasePath(conferenceId),
    '--without-notes',
  ]);
  console.log('slidev_build_finished', conferenceId, `${Date.now() - startedAt}ms`);
  return {
    provider,
    format: 'source',
    staticRoot: 'dist',
    indexFile: 'index.html',
    sourceFile: path.relative(stagingDir, mdFile),
    engineVersion: '@slidev/cli@52.18.0',
    title,
  };
}

function installSlidevFatPresentation(conferenceId, stagingDir, zip, audit) {
  if (!SLIDEV_FAT_ENABLED) throw new Error('slidev_fat_disabled');
  if (audit.decision === 'REJECT') throw new Error('slidev_fat_audit_rejected');
  if (audit.decision === 'QUARANTINE' && !SLIDEV_FAT_ALLOW_WARNINGS) {
    throw new Error('slidev_fat_audit_quarantine');
  }

  const manifest = extractFatArchiveSafely(zip, stagingDir);
  const pdf = manifest.exports?.pdf;
  const previews = Array.isArray(manifest.previews) ? manifest.previews : [];
  const title = typeof manifest.title === 'string' ? manifest.title.trim().slice(0, 300) : null;
  return {
    provider: 'SLIDEV',
    format: 'fat',
    staticRoot: 'dist',
    indexFile: 'index.html',
    engineVersion: String(manifest.engineVersion || 'unknown'),
    buildId: typeof manifest.buildId === 'string' ? manifest.buildId.slice(0, 160) : null,
    title,
    artifactManifest: manifest,
    artifactAudit: {
      tool: audit.tool,
      version: audit.version,
      decision: audit.decision,
      signature: audit.signature,
      files: audit.files.length,
      warnings: audit.issues.filter((item) => item.severity === 'warning').length,
      blocking: audit.issues.filter((item) => item.severity === 'blocking').length,
    },
    exports: {
      pdf: pdf || null,
      previews,
    },
  };
}

app.post('/api/v1/conferences/:id/presentation', requirePresentationManagement, presentationUploadRateLimit, upload.single('file'), async (req, res) => {
  const { id } = req.params;
  if (!validConferenceId(id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!req.file) return res.status(400).json({ error: 'file_required' });

  const provider = normalizeProvider(req.body.presentationProvider, { allowMissing: true });
  if (!provider) {
    try { fs.rmSync(req.file.path, { force: true }); } catch { /* cleanup best effort */ }
    return res.status(400).json({ error: 'invalid_presentation_provider' });
  }

  const activeParent = path.dirname(conferenceDir(id));
  const stagingDir = path.join(activeParent, `${id}.upload-${crypto.randomUUID()}`);

  try {
    fs.mkdirSync(stagingDir, { recursive: true });

    const zip = new AdmZip(req.file.path);
    const fat = provider === 'SLIDEV' && fatArtifactInZip(zip);
    let manifest;
    if (fat) {
      const audit = auditArchive(req.file.path);
      manifest = installSlidevFatPresentation(id, stagingDir, zip, audit);
    } else {
      const srcDir = path.join(stagingDir, 'src');
      fs.mkdirSync(srcDir, { recursive: true });
      extractArchiveSafely(zip, srcDir);
      manifest = await buildPresentation(provider, id, stagingDir, srcDir);
    }
    const fullManifest = {
      ...manifest,
      provider,
      generatedAt: new Date().toISOString(),
      status: 'ready',
    };
    fs.writeFileSync(path.join(stagingDir, PRESENTATION_MANIFEST), JSON.stringify(fullManifest, null, 2));
    replaceActivePresentation(id, stagingDir);
    pdfGenerations.delete(id);
    thumbnailGenerations.delete(id);
    slidevPreviewGenerations.delete(id);

    // Completa el nombre de certificado con el title del frontmatter si el organizador
    // no fijó uno explícito al crear/editar la conferencia (best-effort, no bloquea la subida).
    deriveConferenceName(id, manifest.title);

    res.json({
      ok: true,
      conferenceId: id,
      provider,
      presentationFormat: manifest.format || 'source',
      presentationProvider: provider,
      slidesUrl: provider === 'SLIDEV'
        ? `/api/v1/conferences/${id}/presentation/`
        : `/api/v1/conferences/${id}/presentation/slides`,
      pdfUrl: `/api/v1/conferences/${id}/presentation/pdf`,
    });
  } catch (err) {
    console.error('presentation_generation_failed', err);
    const badRequestErrors = ['invalid_archive_path', 'archive_symlink_not_allowed', 'archive_file_type_not_allowed',
      'archive_uncompressed_size_exceeded', 'archive_file_count_exceeded', 'no_markdown_found_in_zip', 'slidev_entry_ambiguous',
      'slidev_fat_disabled', 'slidev_fat_manifest_missing', 'slidev_fat_manifest_invalid', 'slidev_fat_file_not_allowed',
      'slidev_fat_index_missing', 'slidev_fat_audit_rejected', 'slidev_fat_audit_quarantine'];
    const status = badRequestErrors.includes(err.message)
      ? 400 : 500;
    const messages = {
      slidev_fat_disabled: 'El formato Slidev FAT todavía no está habilitado en este entorno.',
      slidev_fat_audit_rejected: 'El artefacto Slidev FAT fue rechazado por la auditoría de seguridad.',
      slidev_fat_audit_quarantine: 'El artefacto Slidev FAT quedó en cuarentena por advertencias de seguridad.',
    };
    res.status(status).json({ error: status === 400 ? err.message : 'presentation_generation_failed', message: status === 400 ? (messages[err.message] || err.message) : 'No se pudo procesar la presentación' });
  } finally {
    try { fs.rmSync(req.file.path, { force: true }); } catch { /* cleanup best effort */ }
    try { fs.rmSync(stagingDir, { recursive: true, force: true }); } catch { /* cleanup best effort */ }
  }
});

app.get('/api/v1/conferences/:id/presentation/slides', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  const file = presentationIndexFile(req.params.id);
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  setPresentationAccessCookie(req, res, req.params.id);
  res.sendFile(file);
});

app.get('/api/v1/conferences/:id/presentation/presenter', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  const manifest = readManifest(req.params.id);
  const file = presentationIndexFile(req.params.id, manifest);
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  setPresentationAccessCookie(req, res, req.params.id);
  res.sendFile(file);
});

app.get('/api/v1/conferences/:id/presentation/slides/preview', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  setPresentationAccessCookie(req, res, req.params.id);
  const manifest = readManifest(req.params.id);
  if (manifest.provider === 'SLIDEV') {
    try {
      const images = await ensureSlidevPreview(req.params.id);
      if (!images.length) return res.status(404).json({ error: 'not_found' });
      const imageTags = images.slice(0, PREVIEW_SLIDE_LIMIT).map((image) =>
        `<img src="/api/presentations/api/v1/conferences/${req.params.id}/presentation/preview/${encodeURIComponent(path.basename(image))}" alt="Diapositiva" style="display:block;width:100%;max-width:1200px;margin:0 auto 18px;background:#fff;">`
      ).join('');
      return res.type('html').send(`<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Vista previa</title><style>body{margin:0;padding:24px;background:#f3f4f6;font-family:system-ui,sans-serif}h1{max-width:1200px;margin:0 auto 20px;color:#1e1b4b;font-size:1.1rem}</style></head><body><h1>Vista previa · primeras ${PREVIEW_SLIDE_LIMIT} diapositivas</h1>${imageTags}</body></html>`);
    } catch (err) {
      console.error('slidev_preview_generation_failed', err);
      return res.status(500).json({ error: 'preview_generation_failed', message: 'No se pudo generar la vista previa' });
    }
  }

  const file = presentationIndexFile(req.params.id, manifest);
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  try {
    const $ = cheerio.load(fs.readFileSync(file, 'utf8'), { decodeEntities: false });
    $('section[data-marpit-pagination]').each((_, el) => {
      const page = parseInt($(el).attr('data-marpit-pagination'), 10);
      if (page > PREVIEW_SLIDE_LIMIT) $(el).remove();
    });
    $('script, iframe, object, embed, link[rel="import"]').remove();
    $('*').each((_, el) => {
      for (const attribute of Object.keys(el.attribs || {})) {
        if (attribute.toLowerCase().startsWith('on')) $(el).removeAttr(attribute);
      }
      const href = $(el).attr('href');
      if (href && /^\s*javascript:/i.test(href)) $(el).removeAttr('href');
    });
    $('body').append(
      '<div style="position:fixed;bottom:0;left:0;right:0;padding:10px 16px;' +
      'background:rgba(30,27,75,0.92);color:#fff;font-family:sans-serif;font-size:0.85rem;' +
      'text-align:center;z-index:9999;pointer-events:none;">' +
      'Vista previa &middot; primeras ' + PREVIEW_SLIDE_LIMIT + ' diapositivas &middot; ' +
      'inicia sesión para ver la presentación completa</div>'
    );
    res.setHeader('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:");
    res.send($.html());
  } catch (err) {
    res.status(500).json({ error: 'preview_generation_failed', message: err.message });
  }
});

app.get('/api/v1/conferences/:id/presentation/markdown', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  if (readManifest(req.params.id).format === 'fat') return res.status(404).json({ error: 'not_available_for_fat_artifact' });
  const srcDir = path.join(conferenceDir(req.params.id), 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  if (!mdFile) return res.status(404).json({ error: 'not_found' });
  res.type('text/plain').send(fs.readFileSync(mdFile, 'utf8'));
});

// El paquete offline sólo lo puede preparar alguien con permiso de administrar
// la presentación. El navegador descarga los archivos, los divide y cifra
// localmente; este endpoint nunca devuelve contenido de las diapositivas.
// La clave pública no es secreta. Se entrega en runtime para que el build del
// frontend no dependa de secretos de GitHub Actions. La clave privada y la
// pública configurada llegan al pod desde el Secret SOPS del despliegue.
app.get('/api/v1/offline-manifest/public-key', (_req, res) => {
  const keys = offlineManifestKeyMaterial();
  if (!keys) return res.status(503).json({ error: 'offline_not_configured' });
  return res.json({ algorithm: 'Ed25519', publicKey: keys.publicKeyBase64 });
});

app.get('/api/v1/conferences/:id/presentation/offline-manifest', requirePresentationManagement, (req, res) => {
  const conferenceId = req.params.id;
  const manifest = readManifest(conferenceId);
  const root = presentationStaticRoot(conferenceId, manifest);
  const index = presentationIndexFile(conferenceId, manifest);
  if (!fs.existsSync(index)) return res.status(404).json({ error: 'not_found' });
  if (!offlineManifestKeyMaterial()) {
    return res.status(503).json({ error: 'offline_not_configured', message: 'El modo offline no está configurado en el servidor' });
  }

  try {
    const files = listPresentationFiles(root);
    const artifactHash = crypto.createHash('sha256')
      .update(files.map((file) => `${file.path}:${file.sha256}`).join('\n'))
      .digest('hex');
    const offlineManifest = {
      conferenceId,
      provider: manifest.provider,
      format: manifest.format || 'source',
      indexPath: path.relative(root, index).split(path.sep).join('/'),
      artifactHash,
      expiresAt: new Date(Date.now() + OFFLINE_PRESENTATION_TTL_MS).toISOString(),
      files,
    };
    const signature = signOfflineManifest(offlineManifest);
    if (!signature) return res.status(503).json({ error: 'offline_not_configured' });
    return res.json({ ...offlineManifest, ...signature });
  } catch (err) {
    console.error('offline_manifest_generation_failed', err);
    return res.status(500).json({ error: 'offline_manifest_generation_failed' });
  }
});

app.use('/api/v1/conferences/:id/presentation', (req, res, next) => {
  const apiEndpointPaths = new Set(['/status', '/markdown', '/offline-manifest', '/slides', '/presenter', '/slides/preview', '/thumbnail', '/pdf', '/remote-token']);
  if (apiEndpointPaths.has(req.path)) return next();
  const manifest = readManifest(req.params.id);
  const root = presentationStaticRoot(req.params.id, manifest);
  if (manifest.provider === 'SLIDEV' && req.path.startsWith('/preview/')) return next();
  const serve = () => express.static(root, { index: false })(req, res, (err) => {
    if (err || manifest.provider !== 'SLIDEV' || path.extname(req.path)) return next(err);
    const index = presentationIndexFile(req.params.id, manifest);
    if (fs.existsSync(index)) return res.sendFile(index);
    next();
  });
  return requireConferenceAccess(req, res).then((allowed) => {
    if (allowed) {
      const contentSecurityPolicy = manifest.format === 'fat'
        ? "default-src 'none'; base-uri 'none'; object-src 'none'; form-action 'none'; frame-ancestors 'self' https://insightbloom.v1.rafex.cloud; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; img-src 'self' data: blob:; font-src 'self' data: https://fonts.gstatic.com; media-src 'self' blob:; connect-src 'none'"
        : manifest.provider === 'MARP'
          ? "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'self' https://insightbloom.v1.rafex.cloud; script-src 'none'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; img-src 'self' data: blob:; font-src 'self' data: https://fonts.gstatic.com; media-src 'self' blob:; connect-src 'none'; frame-src 'none'"
          : "default-src 'self'; base-uri 'none'; object-src 'none'; frame-ancestors 'self' https://insightbloom.v1.rafex.cloud; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; img-src 'self' data: blob:; font-src 'self' data: https://fonts.gstatic.com; media-src 'self' blob:; connect-src 'self' https: wss:";
      res.setHeader('Content-Security-Policy', contentSecurityPolicy);
      setPresentationAccessCookie(req, res, req.params.id);
      serve();
    }
  });
});

app.get('/api/v1/conferences/:id/presentation/preview/:file', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  setPresentationAccessCookie(req, res, req.params.id);
  const manifest = readManifest(req.params.id);
  if (manifest.provider !== 'SLIDEV') return res.status(404).json({ error: 'not_found' });
  const file = path.basename(req.params.file);
  if (!/^slide-[0-9]+\.png$/i.test(file)) return res.status(404).json({ error: 'not_found' });
  const previewRoot = manifest.format === 'fat' ? 'previews' : 'preview';
  const target = path.join(conferenceDir(req.params.id), previewRoot, file);
  if (!fs.existsSync(target)) {
    try { await ensureSlidevPreview(req.params.id); } catch { /* handled below */ }
  }
  if (!fs.existsSync(target)) return res.status(404).json({ error: 'not_found' });
  res.type('png').sendFile(target);
});

async function ensurePdf(conferenceId) {
  const confDir = conferenceDir(conferenceId);
  const slidesPdf = path.join(confDir, 'slides.pdf');
  if (fs.existsSync(slidesPdf)) return slidesPdf;

  const manifest = readManifest(conferenceId);
  if (manifest.format === 'fat') {
    const packagedPdf = typeof manifest.exports?.pdf === 'string'
      ? path.join(confDir, manifest.exports.pdf)
      : null;
    return packagedPdf && fs.existsSync(packagedPdf) ? packagedPdf : null;
  }

  if (pdfGenerations.has(conferenceId)) return pdfGenerations.get(conferenceId);

  const srcDir = path.join(confDir, 'src');
  const mdFile = manifest.sourceFile
    ? path.join(confDir, manifest.sourceFile)
    : findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  if (!mdFile) return null;

  if (manifest.provider === 'SLIDEV') {
    const generation = runSlidev([
      'export', mdFile,
      '--format', 'pdf',
      '--output', slidesPdf,
      '--timeout', '120000',
      '--executable-path', CHROMIUM_PATH,
    ])
      .then(() => slidesPdf)
      .finally(() => pdfGenerations.delete(conferenceId));
    pdfGenerations.set(conferenceId, generation);
    return generation;
  }

  const themeFile = findFile(srcDir, (name, full) => name === 'theme.css' && full.includes(`${path.sep}css${path.sep}`));

  const baseArgs = [mdFile, '--allow-local-files', '--html'];
  if (themeFile) baseArgs.push('--theme', themeFile);

  const generation = runMarp([...baseArgs, '-o', slidesPdf])
    .then(() => slidesPdf)
    .finally(() => pdfGenerations.delete(conferenceId));
  pdfGenerations.set(conferenceId, generation);
  return generation;
}

function findPngFiles(rootDir) {
  const files = [];
  const stack = [rootDir];
  while (stack.length) {
    const dir = stack.pop();
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch { continue; }
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(full);
      else if (entry.name.toLowerCase().endsWith('.png')) files.push(full);
    }
  }
  return files.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
}

async function ensureSlidevPreview(conferenceId) {
  const confDir = conferenceDir(conferenceId);
  const manifest = readManifest(conferenceId);
  const previewDir = path.join(confDir, manifest.format === 'fat' ? 'previews' : 'preview');
  const existing = findPngFiles(previewDir).filter((file) => /^slide-[0-9]+\.png$/i.test(path.basename(file)));
  if (existing.length) return existing;
  if (slidevPreviewGenerations.has(conferenceId)) return slidevPreviewGenerations.get(conferenceId);

  const mdFile = manifest.sourceFile ? path.join(confDir, manifest.sourceFile) : null;
  if (manifest.provider !== 'SLIDEV' || !mdFile || !fs.existsSync(mdFile)) return [];

  const generation = (async () => {
    fs.rmSync(previewDir, { recursive: true, force: true });
    fs.mkdirSync(previewDir, { recursive: true });
    const outputPrefix = path.join(previewDir, 'slide');
    await runSlidevSerialized([
      'export', mdFile,
      '--format', 'png',
      '--output', outputPrefix,
      '--timeout', '120000',
      '--executable-path', CHROMIUM_PATH,
    ]);
    const generated = findPngFiles(previewDir).filter((file) => path.basename(file) !== 'thumbnail.png');
    const normalized = [];
    generated.slice(0, PREVIEW_SLIDE_LIMIT).forEach((file, index) => {
      const target = path.join(previewDir, `slide-${index + 1}.png`);
      if (file !== target) fs.copyFileSync(file, target);
      normalized.push(target);
    });
    return normalized;
  })().finally(() => slidevPreviewGenerations.delete(conferenceId));
  slidevPreviewGenerations.set(conferenceId, generation);
  return generation;
}

async function ensureThumbnail(conferenceId) {
  const confDir = conferenceDir(conferenceId);
  const thumbnail = path.join(confDir, 'thumbnail.png');
  if (fs.existsSync(thumbnail)) return thumbnail;

  if (thumbnailGenerations.has(conferenceId)) return thumbnailGenerations.get(conferenceId);

  const manifest = readManifest(conferenceId);
  if (manifest.provider === 'SLIDEV') {
    const images = await ensureSlidevPreview(conferenceId);
    if (!images.length) return null;
    fs.copyFileSync(images[0], thumbnail);
    return thumbnail;
  }

  const srcDir = path.join(confDir, 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  if (!mdFile) return null;
  const themeFile = findFile(srcDir, (name, full) => name === 'theme.css' && full.includes(`${path.sep}css${path.sep}`));

  const baseArgs = [mdFile, '--allow-local-files', '--image', 'png'];
  if (themeFile) baseArgs.push('--theme', themeFile);

  const generation = runMarp([...baseArgs, '-o', thumbnail])
    .then(() => thumbnail)
    .finally(() => thumbnailGenerations.delete(conferenceId));
  thumbnailGenerations.set(conferenceId, generation);
  return generation;
}

app.get('/api/v1/conferences/:id/presentation/thumbnail', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  setPresentationAccessCookie(req, res, req.params.id);
  try {
    const file = await ensureThumbnail(req.params.id);
    if (!file) return res.status(404).json({ error: 'not_found' });
    res.sendFile(file);
  } catch (err) {
    console.error('thumbnail_generation_failed', err);
    res.status(500).json({ error: 'thumbnail_generation_failed', message: err.message });
  }
});

app.get('/api/v1/conferences/:id/presentation/pdf', async (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!await requireConferenceAccess(req, res)) return;
  try {
    const file = await ensurePdf(req.params.id);
    if (!file) return res.status(404).json({ error: 'not_found' });
    recordDownload(req.params.id, 'presentation');
    res.download(file, 'presentacion.pdf');
  } catch (err) {
    console.error('pdf_generation_failed', err);
    res.status(500).json({ error: 'pdf_generation_failed', message: err.message });
  }
});

const FRIENDLY_ID_RE = /^[a-z0-9-]{1,64}$/i;

// Página de previsualización para redes sociales (WhatsApp/Telegram/Facebook/etc.): estos
// crawlers no ejecutan JS, así que la SPA no les sirve. nginx redirige aquí solo a los
// user-agents conocidos de bots; los navegadores normales van directo a la SPA.
app.get('/api/v1/share/:friendlyId', async (req, res) => {
  const { friendlyId } = req.params;
  if (!FRIENDLY_ID_RE.test(friendlyId)) return res.status(400).send('invalid_friendly_id');

  const canonicalUrl = `${FRONTEND_BASE_URL}/c/${friendlyId}/presentation`;
  let conference;
  try {
    const r = await fetch(`${USERS_URL}/api/v1/conferences/by-friendly/${friendlyId}`);
    if (!r.ok) return res.redirect(302, canonicalUrl);
    conference = (await r.json()).data;
  } catch (err) {
    console.error('share_lookup_failed', friendlyId, err.message);
    return res.redirect(302, canonicalUrl);
  }

  const title = conference.name || friendlyId;
  const presentationManifest = readManifest(conference.uuid);
  const srcDir = path.join(conferenceDir(conference.uuid), 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  const description = mdFile
    ? (extractFrontmatterDescription(fs.readFileSync(mdFile, 'utf8')) || 'Presentación en InsightBloom')
    : (presentationManifest.title || 'Presentación en InsightBloom');

  const thumbnailPath = path.join(conferenceDir(conference.uuid), 'thumbnail.png');
  let imageUrl = `${FRONTEND_BASE_URL}/pwa-512x512.png`;
  if (fs.existsSync(thumbnailPath)) {
    imageUrl = `${FRONTEND_BASE_URL}/api/presentations/api/v1/conferences/${conference.uuid}/presentation/thumbnail`;
  } else if (mdFile || presentationManifest.format === 'fat') {
    // Genera la miniatura en segundo plano para que la próxima vez que se comparta el
    // enlace (o el crawler vuelva a pedirlo) ya esté lista; no bloquea esta respuesta.
    ensureThumbnail(conference.uuid).catch((err) => console.error('thumbnail_bg_generation_failed', err.message));
  }

  res.type('html').send(`<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta property="og:type" content="website">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description)}">
<meta property="og:image" content="${escapeHtml(imageUrl)}">
<meta property="og:url" content="${escapeHtml(canonicalUrl)}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${escapeHtml(title)}">
<meta name="twitter:description" content="${escapeHtml(description)}">
<meta name="twitter:image" content="${escapeHtml(imageUrl)}">
<meta http-equiv="refresh" content="0;url=${escapeHtml(canonicalUrl)}">
</head>
<body>
<p>Redirigiendo a <a href="${escapeHtml(canonicalUrl)}">${escapeHtml(canonicalUrl)}</a>&hellip;</p>
</body>
</html>`);
});

app.get('/api/v1/conferences/:id/presentation/status', (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  const confDir = conferenceDir(req.params.id);
  const manifest = readManifest(req.params.id);
  res.json({
    ready: fs.existsSync(presentationIndexFile(req.params.id, manifest)),
    provider: manifest.provider,
    presentationProvider: manifest.provider,
    presentationFormat: manifest.format || 'source',
    engineVersion: manifest.engineVersion || null,
    artifactAudit: manifest.artifactAudit || null,
    exports: {
      pdf: fs.existsSync(path.join(confDir, 'slides.pdf'))
        || (manifest.format === 'fat' && typeof manifest.exports?.pdf === 'string' && fs.existsSync(path.join(confDir, manifest.exports.pdf))),
      preview: manifest.provider === 'SLIDEV'
        ? fs.existsSync(path.join(confDir, manifest.format === 'fat' ? 'previews' : 'preview', 'slide-1.png'))
        : true,
    },
  });
});

app.delete('/api/v1/conferences/:id/presentation', (req, res) => {
  const key = req.headers['x-internal-api-key'];
  if (!INTERNAL_API_KEY || key !== INTERNAL_API_KEY) {
    return res.status(403).json({ error: 'forbidden' });
  }
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  fs.rmSync(conferenceDir(req.params.id), { recursive: true, force: true });
  res.json({ status: 'deleted' });
});

app.post('/api/v1/conferences/:id/presentation/remote-token', async (req, res) => {
  const authHeader = req.headers.authorization || '';
  const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7) : null;
  const remoteToken = await issueRemoteToken(req.params.id, token);
  if (!remoteToken) return res.status(403).json({ error: 'not_authorized' });
  res.json({ token: remoteToken });
});

app.get('/version', (_req, res) => res.json({
  service: 'insightbloom-presentations',
  version: process.env.APP_VERSION || 'dev',
  gitSha: process.env.GIT_SHA || 'unknown',
}));

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

const server = http.createServer(app);
attachLiveSync(server, {
  usersUrl: USERS_URL,
  natsUrl: NATS_URL,
  natsToken: NATS_AUTH_TOKEN,
  internalApiKey: INTERNAL_API_KEY,
});

server.listen(PORT, () => {
  console.log(`insightbloom-presentations listening on :${PORT}`);
});
