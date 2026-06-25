const path = require('path');
const fs = require('fs');
const { execFile } = require('child_process');
const express = require('express');
const multer = require('multer');
const AdmZip = require('adm-zip');

const PORT = process.env.PORT || 8091;
const DATA_DIR = process.env.DATA_DIR || '/data';
const MARP_BIN = path.join(__dirname, 'node_modules', '.bin', 'marp');

const upload = multer({ dest: path.join(DATA_DIR, 'tmp') });

const app = express();

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
    const slidesPdf = path.join(confDir, 'slides.pdf');

    const baseArgs = [mdFile, '--allow-local-files', '--html'];
    if (themeFile) baseArgs.push('--theme', themeFile);

    await runMarp([...baseArgs, '-o', slidesHtml]);
    await runMarp([...baseArgs, '-o', slidesPdf]);

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
  const file = path.join(conferenceDir(req.params.id), 'src', 'slides.html');
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  res.sendFile(file);
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

app.get('/api/v1/conferences/:id/presentation/pdf', (req, res) => {
  const file = path.join(conferenceDir(req.params.id), 'slides.pdf');
  if (!fs.existsSync(file)) return res.status(404).json({ error: 'not_found' });
  res.download(file, 'presentacion.pdf');
});

app.get('/api/v1/conferences/:id/presentation/status', (req, res) => {
  const confDir = conferenceDir(req.params.id);
  res.json({
    ready: fs.existsSync(path.join(confDir, 'src', 'slides.html')) && fs.existsSync(path.join(confDir, 'slides.pdf')),
  });
});

app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.listen(PORT, () => {
  console.log(`insightbloom-presentations listening on :${PORT}`);
});
