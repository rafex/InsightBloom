const path = require('path');
const fs = require('fs');
const http = require('http');
const { execFile } = require('child_process');
const express = require('express');
const multer = require('multer');
const AdmZip = require('adm-zip');
const cheerio = require('cheerio');
const { attachLiveSync, issueRemoteToken } = require('./live');

const PREVIEW_SLIDE_LIMIT = 5;

const PORT = process.env.PORT || 8091;
const DATA_DIR = process.env.DATA_DIR || '/data';
const USERS_URL = process.env.USERS_URL || 'http://insightbloom-users:8081';
const FRONTEND_BASE_URL = process.env.FRONTEND_BASE_URL || 'https://insightbloom.v1.rafex.cloud';
const NATS_URL = process.env.NATS_URL || '';
const NATS_AUTH_TOKEN = process.env.NATS_AUTH_TOKEN || '';
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY || '';
const MARP_BIN = path.join(__dirname, 'node_modules', '.bin', 'marp');

const upload = multer({ dest: path.join(DATA_DIR, 'tmp'), limits: { fileSize: 100 * 1024 * 1024 } });

const app = express();

// PDF/miniatura se generan bajo demanda (corren Chromium headless vía Marp) y se cachean en
// disco; estos mapas deduplican generaciones concurrentes para la misma conferencia.
const pdfGenerations = new Map();
const thumbnailGenerations = new Map();

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function validConferenceId(id) {
  return typeof id === 'string' && UUID_RE.test(id);
}

function conferenceDir(conferenceId) {
  return path.join(DATA_DIR, 'presentations', conferenceId);
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

app.post('/api/v1/conferences/:id/presentation', upload.single('file'), async (req, res) => {
  const { id } = req.params;
  if (!validConferenceId(id)) return res.status(400).json({ error: 'invalid_conference_id' });
  if (!req.file) return res.status(400).json({ error: 'file_required' });

  const confDir = conferenceDir(id);
  const srcDir = path.join(confDir, 'src');

  try {
    fs.rmSync(confDir, { recursive: true, force: true });
    fs.mkdirSync(srcDir, { recursive: true });

    const zip = new AdmZip(req.file.path);
    zip.extractAllTo(srcDir, true);
    fs.unlinkSync(req.file.path);

    const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
    if (!mdFile) {
      return res.status(400).json({ error: 'no_markdown_found_in_zip' });
    }

    const themeFile = findFile(srcDir, (name, full) => name === 'theme.css' && full.includes(`${path.sep}css${path.sep}`));

    const slidesHtml = path.join(srcDir, 'slides.html');

    const baseArgs = [mdFile, '--allow-local-files', '--html'];
    if (themeFile) baseArgs.push('--theme', themeFile);

    await runMarp([...baseArgs, '-o', slidesHtml]);
    // El PDF (requiere Chromium headless vía Marp) se genera bajo demanda en el
    // endpoint /pdf, no aquí, para no pagar ese costo en cada subida.

    // Completa el nombre de certificado con el title del frontmatter si el organizador
    // no fijó uno explícito al crear/editar la conferencia (best-effort, no bloquea la subida).
    const title = extractFrontmatterTitle(fs.readFileSync(mdFile, 'utf8'));
    deriveConferenceName(id, title);

    res.json({
      ok: true,
      conferenceId: id,
      slidesUrl: `/api/v1/conferences/${id}/presentation/slides`,
      pdfUrl: `/api/v1/conferences/${id}/presentation/pdf`,
    });
  } catch (err) {
    console.error('presentation_generation_failed', err);
    res.status(500).json({ error: 'presentation_generation_failed', message: err.message });
  }
});

app.get('/api/v1/conferences/:id/presentation/slides', (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  const file = path.join(conferenceDir(req.params.id), 'src', 'slides.html');
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  res.sendFile(file);
});

app.get('/api/v1/conferences/:id/presentation/slides/preview', (req, res) => {
  if (!validConferenceId(req.params.id)) return res.status(400).json({ error: 'invalid_conference_id' });
  const file = path.join(conferenceDir(req.params.id), 'src', 'slides.html');
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  try {
    const $ = cheerio.load(fs.readFileSync(file, 'utf8'), { decodeEntities: false });
    $('section[data-marpit-pagination]').each((_, el) => {
      const page = parseInt($(el).attr('data-marpit-pagination'), 10);
      if (page > PREVIEW_SLIDE_LIMIT) $(el).remove();
    });
    $('body').append(
      '<div style="position:fixed;bottom:0;left:0;right:0;padding:10px 16px;' +
      'background:rgba(30,27,75,0.92);color:#fff;font-family:sans-serif;font-size:0.85rem;' +
      'text-align:center;z-index:9999;pointer-events:none;">' +
      'Vista previa &middot; primeras ' + PREVIEW_SLIDE_LIMIT + ' diapositivas &middot; ' +
      'inicia sesión para ver la presentación completa</div>'
    );
    res.send($.html());
  } catch (err) {
    res.status(500).json({ error: 'preview_generation_failed', message: err.message });
  }
});

app.get('/api/v1/conferences/:id/presentation/markdown', (req, res) => {
  const srcDir = path.join(conferenceDir(req.params.id), 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  if (!mdFile) return res.status(404).json({ error: 'not_found' });
  res.type('text/plain').send(fs.readFileSync(mdFile, 'utf8'));
});

app.use('/api/v1/conferences/:id/presentation', (req, res, next) => {
  express.static(path.join(conferenceDir(req.params.id), 'src'))(req, res, next);
});

async function ensurePdf(conferenceId) {
  const confDir = conferenceDir(conferenceId);
  const slidesPdf = path.join(confDir, 'slides.pdf');
  if (fs.existsSync(slidesPdf)) return slidesPdf;

  if (pdfGenerations.has(conferenceId)) return pdfGenerations.get(conferenceId);

  const srcDir = path.join(confDir, 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  if (!mdFile) return null;
  const themeFile = findFile(srcDir, (name, full) => name === 'theme.css' && full.includes(`${path.sep}css${path.sep}`));

  const baseArgs = [mdFile, '--allow-local-files', '--html'];
  if (themeFile) baseArgs.push('--theme', themeFile);

  const generation = runMarp([...baseArgs, '-o', slidesPdf])
    .then(() => slidesPdf)
    .finally(() => pdfGenerations.delete(conferenceId));
  pdfGenerations.set(conferenceId, generation);
  return generation;
}

async function ensureThumbnail(conferenceId) {
  const confDir = conferenceDir(conferenceId);
  const thumbnail = path.join(confDir, 'thumbnail.png');
  if (fs.existsSync(thumbnail)) return thumbnail;

  if (thumbnailGenerations.has(conferenceId)) return thumbnailGenerations.get(conferenceId);

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
  const srcDir = path.join(conferenceDir(conference.uuid), 'src');
  const mdFile = findFile(srcDir, (name) => name.toLowerCase().endsWith('.md'));
  const description = mdFile
    ? (extractFrontmatterDescription(fs.readFileSync(mdFile, 'utf8')) || 'Presentación en InsightBloom')
    : 'Presentación en InsightBloom';

  const thumbnailPath = path.join(conferenceDir(conference.uuid), 'thumbnail.png');
  let imageUrl = `${FRONTEND_BASE_URL}/pwa-512x512.png`;
  if (fs.existsSync(thumbnailPath)) {
    imageUrl = `${FRONTEND_BASE_URL}/api/presentations/api/v1/conferences/${conference.uuid}/presentation/thumbnail`;
  } else if (mdFile) {
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
  res.json({
    ready: fs.existsSync(path.join(confDir, 'src', 'slides.html')),
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
