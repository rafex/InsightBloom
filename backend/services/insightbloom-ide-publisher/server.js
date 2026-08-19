const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const express = require('express');
const AdmZip = require('adm-zip');
const {
  auditZipBuffer,
  normalizeEntryName,
  isSymlink,
  isExcludedFromPublication,
} = require('./tools/audit-web-artifact');

const PORT = Number(process.env.PORT || 8096);
const DATA_DIR = process.env.DATA_DIR || '/data';
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY || '';
const PUBLIC_BASE_URL = process.env.PREVIEW_PUBLIC_BASE_URL || 'https://preview-insightbloom.v1.rafex.cloud/p';
const APP_PUBLIC_BASE_URL = process.env.APP_PREVIEW_BASE_URL || 'https://app-insightbloom.v1.rafex.cloud';
const TTL_SECONDS = Math.min(Math.max(Number(process.env.PREVIEW_TTL_SECONDS || 3600), 300), 24 * 60 * 60);
const ROOT = path.join(DATA_DIR, 'publications');
const REGISTRY = path.join(DATA_DIR, 'app-publications.json');
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const app = express();
app.disable('x-powered-by');
app.use((_req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  next();
});
app.use(express.json({ limit: '128kb' }));
fs.mkdirSync(path.join(DATA_DIR, 'tmp'), { recursive: true });
fs.mkdirSync(ROOT, { recursive: true });

function constantTime(value, expected) {
  if (typeof value !== 'string' || !expected) return false;
  const left = Buffer.from(value);
  const right = Buffer.from(expected);
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function requireInternal(req, res) {
  const supplied = req.headers['x-internal-api-key'] || req.headers['x-internal-auth'];
  if (!constantTime(supplied, INTERNAL_API_KEY)) {
    res.status(403).json({ error: 'forbidden' });
    return false;
  }
  return true;
}

function publicationPath(id) { return path.join(ROOT, id); }
function manifestPath(id) { return path.join(publicationPath(id), 'publication.json'); }
function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}
function readManifest(id) {
  if (!UUID_RE.test(String(id || ''))) return null;
  const manifest = readJson(manifestPath(id), null);
  return manifest && manifest.publicationId === id ? manifest : null;
}
function expired(item) { return item.status !== 'active' || Date.parse(item.expiresAt) <= Date.now(); }
function loadRegistry() { return readJson(REGISTRY, {}); }
function saveRegistry(value) {
  const temporary = `${REGISTRY}.tmp-${process.pid}`;
  fs.writeFileSync(temporary, JSON.stringify(value, null, 2), { mode: 0o640 });
  fs.renameSync(temporary, REGISTRY);
}
function publicUrl(id) { return `${PUBLIC_BASE_URL.replace(/\/$/, '')}/${id}/`; }
function appUrl(id) { return `${APP_PUBLIC_BASE_URL.replace(/\/$/, '')}/p/${id}`; }

function contentType(file) {
  const ext = path.extname(file).toLowerCase();
  return ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg', '.gif': 'image/gif', '.webp': 'image/webp', '.txt': 'text/plain; charset=utf-8',
    '.mp4': 'video/mp4', '.webm': 'video/webm', '.mp3': 'audio/mpeg', '.wav': 'audio/wav' })[ext] || 'application/octet-stream';
}

function csp(res, nonce) {
  res.setHeader('Content-Security-Policy', `default-src 'none'; base-uri 'none'; object-src 'none'; form-action 'self'; frame-ancestors 'self' https://insightbloom.v1.rafex.cloud; script-src 'self' 'nonce-${nonce}'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; media-src 'self' blob:; connect-src 'self'; frame-src 'none'`);
  res.setHeader('Cache-Control', 'no-store, max-age=0');
  res.setHeader('Cross-Origin-Resource-Policy', 'cross-origin');
}

function sendFile(target, res, next) {
  if (!['.html', '.htm'].includes(path.extname(target).toLowerCase())) {
    res.type(contentType(target));
    return res.sendFile(target, { dotfiles: 'deny' }, error => error && next(error));
  }
  try {
    const nonce = crypto.randomBytes(18).toString('base64');
    const source = fs.readFileSync(target, 'utf8');
    const html = source.replace(/<script\b(?![^>]*\bnonce=)([^>]*)>/gi,
      (_match, attrs) => `<script nonce="${nonce}"${attrs}>`);
    csp(res, nonce);
    return res.type(contentType(target)).send(html);
  } catch (error) { return next(error); }
}

function findIndex(root) {
  const found = [];
  const stack = [root];
  while (stack.length) {
    const dir = stack.pop();
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const target = path.join(dir, entry.name);
      if (entry.isDirectory()) stack.push(target);
      else if (/^index\.html?$/i.test(entry.name)) found.push(target);
    }
  }
  if (found.length !== 1) throw new Error(found.length ? 'preview_multiple_indexes' : 'preview_index_missing');
  return found[0];
}

function sanitizeSvg(buffer) {
  const source = buffer.toString('utf8');
  return Buffer.from(source
    .replace(/<\/?(?:script|foreignObject|iframe|object|embed|audio|video)\b[^>]*>/gi, '')
    .replace(/\s+on[a-z-]+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/(?:href|xlink:href|src)\s*=\s*(?:"|')?\s*(?:javascript:|data:text\/html)[^"'>\s]*(?:"|')?/gi, ''), 'utf8');
}

function extract(buffer, destination) {
  const audit = auditZipBuffer(buffer, 'workspace.zip');
  const zip = new AdmZip(buffer);
  for (const entry of zip.getEntries()) {
    const name = normalizeEntryName(entry.entryName);
    if (isSymlink(entry)) throw new Error('archive_symlink_not_allowed');
    const target = path.join(destination, ...name.split('/'));
    if (entry.isDirectory) { fs.mkdirSync(target, { recursive: true }); continue; }
    if (isExcludedFromPublication(name)) continue;
    fs.mkdirSync(path.dirname(target), { recursive: true });
    const data = entry.getData();
    fs.writeFileSync(target, path.extname(name).toLowerCase() === '.svg' ? sanitizeSvg(data) : data);
  }
  const index = findIndex(destination);
  const root = path.relative(destination, path.dirname(index)).split(path.sep).join('/');
  return { ...audit, staticRoot: root === '.' ? '' : root, indexPath: 'index.html' };
}

app.use('/p/:publicationId', (req, res, next) => {
  const requestPath = String(req.path || '/');
  if (requestPath === '/' && !String(req.originalUrl || '').split('?')[0].endsWith('/')) {
    return res.redirect(308, `/p/${req.params.publicationId}/${req.url.includes('?') ? req.url.slice(req.url.indexOf('?')) : ''}`);
  }
  const manifest = readManifest(req.params.publicationId);
  if (!manifest) return res.status(404).json({ error: 'preview_not_found' });
  if (expired(manifest)) { fs.rmSync(publicationPath(manifest.publicationId), { recursive: true, force: true }); return res.status(410).json({ error: 'preview_expired' }); }
  const root = path.resolve(publicationPath(manifest.publicationId), manifest.staticRoot || '.');
  let requested;
  try {
    requested = normalizeEntryName(requestPath === '/' ? manifest.indexPath : decodeURIComponent(requestPath.replace(/^\/+/, '')));
  }
  catch { return res.status(400).json({ error: 'invalid_preview_path' }); }
  if (path.basename(requested).toLowerCase() === 'publication.json') return res.status(404).json({ error: 'not_found' });
  let target = path.resolve(root, requested);
  if (target !== root && !target.startsWith(`${root}${path.sep}`)) return res.status(400).json({ error: 'invalid_preview_path' });
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
    if (!path.extname(requested)) target = path.resolve(root, manifest.indexPath);
    if (!fs.existsSync(target) || !fs.statSync(target).isFile()) return res.status(404).json({ error: 'preview_file_not_found' });
  }
  return sendFile(target, res, next);
});

app.post('/internal/v1/previews', express.raw({ type: 'application/zip', limit: '100mb' }), (req, res) => {
  if (!requireInternal(req, res)) return;
  const conferenceId = String(req.headers['x-conference-id'] || '');
  const ownerId = String(req.headers['x-owner-id'] || '');
  if (!UUID_RE.test(conferenceId) || !UUID_RE.test(ownerId)) return res.status(400).json({ error: 'invalid_preview_owner_or_conference' });
  const ttl = Math.min(Math.max(Number(req.headers['x-expires-in-seconds'] || TTL_SECONDS), 300), 24 * 60 * 60);
  const id = crypto.randomUUID();
  const staging = path.join(DATA_DIR, 'tmp', `preview-${id}`);
  try {
    if (!Buffer.isBuffer(req.body)) throw new Error('preview_archive_empty');
    fs.mkdirSync(staging, { recursive: true });
    const artifact = extract(req.body, staging);
    const manifest = { publicationId: id, conferenceId, ownerId, status: 'active', createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + ttl * 1000).toISOString(), artifactHash: artifact.artifactHash, totalBytes: artifact.totalBytes, files: artifact.files, staticRoot: artifact.staticRoot, indexPath: artifact.indexPath };
    fs.writeFileSync(path.join(staging, 'publication.json'), JSON.stringify(manifest, null, 2), { mode: 0o640 });
    fs.renameSync(staging, publicationPath(id));
    res.status(201).json({ publicationId: id, url: publicUrl(id), expiresAt: manifest.expiresAt, artifactHash: manifest.artifactHash, files: manifest.files.length });
  } catch (error) {
    fs.rmSync(staging, { recursive: true, force: true });
    const status = /^(?:preview_|archive_)/.test(error.message) ? 422 : 500;
    res.status(status).json({ error: error.message || 'preview_publication_failed', issues: error.issues || undefined });
  }
});

app.delete('/internal/v1/previews/:publicationId', (req, res) => {
  if (!requireInternal(req, res)) return;
  const manifest = readManifest(req.params.publicationId);
  if (!manifest) return res.status(404).json({ error: 'preview_not_found' });
  if (manifest.conferenceId !== req.headers['x-conference-id'] || manifest.ownerId !== req.headers['x-owner-id']) return res.status(403).json({ error: 'preview_owner_mismatch' });
  fs.rmSync(publicationPath(manifest.publicationId), { recursive: true, force: true });
  res.json({ revoked: true });
});

app.post('/internal/v1/app-previews', (req, res) => {
  if (!requireInternal(req, res)) return;
  const body = req.body || {};
  if (!UUID_RE.test(body.publicationId || '') || !UUID_RE.test(body.conferenceId || '') || !UUID_RE.test(body.ownerId || '') || !Number.isInteger(body.targetPort) || body.targetPort <= 0) return res.status(400).json({ error: 'invalid_app_preview' });
  const registry = loadRegistry();
  const current = registry[body.publicationId] || {};
  registry[body.publicationId] = { ...current, publicationId: body.publicationId, conferenceId: body.conferenceId, ownerId: body.ownerId, podName: String(body.podName || ''), targetPort: body.targetPort, accessToken: String(body.accessToken || ''), status: 'active', createdAt: current.createdAt || new Date().toISOString(), expiresAt: body.expiresAt || new Date(Date.now() + TTL_SECONDS * 1000).toISOString() };
  saveRegistry(registry);
  res.status(201).json({ publicationId: body.publicationId, url: appUrl(body.publicationId), expiresAt: registry[body.publicationId].expiresAt });
});

app.get('/internal/v1/app-previews/resolve', (req, res) => {
  if (!requireInternal(req, res)) return;
  const item = loadRegistry()[String(req.query.publicationId || '')];
  if (!item || expired(item) || item.accessToken !== String(req.query.token || '')) return res.status(404).json({ error: 'app_preview_not_found' });
  const runtimeService = process.env.RUNTIME_SERVICE_NAME || 'insightbloom-ide-runtime';
  const target = item.podName === runtimeService
    ? `http://${runtimeService}:${item.targetPort}`
    : `http://${item.podName}-svc.${process.env.SANDBOX_NAMESPACE || 'insightbloom-sandboxes'}.svc.cluster.local:${item.targetPort}`;
  // Mantiene la forma {data:{...}} que consume tools-gateway y deja target plano para
  // clientes internos de diagnóstico; nunca contiene tokens ni URLs privadas del sandbox.
  res.json({ target, data: { target } });
});

app.delete('/internal/v1/app-previews/:publicationId', (req, res) => {
  if (!requireInternal(req, res)) return;
  const registry = loadRegistry();
  const item = registry[req.params.publicationId];
  if (!item) return res.status(404).json({ error: 'app_preview_not_found' });
  if (item.conferenceId !== req.headers['x-conference-id'] || item.ownerId !== req.headers['x-owner-id']) return res.status(403).json({ error: 'app_preview_owner_mismatch' });
  delete registry[req.params.publicationId]; saveRegistry(registry); res.json({ revoked: true });
});

function cleanup() {
  for (const entry of fs.readdirSync(ROOT, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const manifest = readManifest(entry.name);
    if (manifest && expired(manifest)) fs.rmSync(publicationPath(entry.name), { recursive: true, force: true });
  }
  const registry = loadRegistry();
  let changed = false;
  for (const [id, item] of Object.entries(registry)) if (expired(item)) { delete registry[id]; changed = true; }
  if (changed) saveRegistry(registry);
}
setInterval(cleanup, 60_000).unref?.();
app.get('/version', (_req, res) => res.json({ service: 'insightbloom-ide-publisher', version: process.env.APP_VERSION || 'dev', gitSha: process.env.GIT_SHA || 'unknown' }));
app.get('/health', (_req, res) => res.json({ status: 'ok' }));
app.listen(PORT, () => console.log(`insightbloom-ide-publisher listening on :${PORT}`));
