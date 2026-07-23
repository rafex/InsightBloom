const test = require('node:test');
const assert = require('node:assert/strict');
const AdmZip = require('adm-zip');
const { auditZipBuffer, normalizeEntryName } = require('./audit-web-artifact');

function zip(entries) {
  const archive = new AdmZip();
  for (const [name, value] of Object.entries(entries)) archive.addFile(name, Buffer.from(value));
  return archive.toBuffer();
}

test('accepts a static HTML workspace with local assets', () => {
  const report = auditZipBuffer(zip({
    'index.html': '<!doctype html><script src="app.js"></script>',
    'app.js': 'document.querySelector("body").textContent = "ok";',
    'app.css': 'body { color: #222; }',
  }));
  assert.equal(report.files.length, 3);
  assert.equal(report.artifactHash.length, 64);
});

test('excludes optional project metadata without requiring package.json', () => {
  const report = auditZipBuffer(zip({
    'index.html': '<h1>ok</h1>',
    'package.json': '{"scripts":{"build":"vite"}}',
    'vite.config.js': 'export default {}',
  }));
  assert.deepEqual(report.files.map(file => file.path), ['index.html']);
  assert.deepEqual(report.excludedFiles.sort(), ['package.json', 'vite.config.js']);
});

test('rejects credential files and parent traversal', () => {
  assert.throws(() => auditZipBuffer(zip({ 'index.html': '<p>ok</p>', '.env': 'SECRET=x' })), /preview_secret/);
  assert.throws(() => normalizeEntryName('../index.html'), /invalid_archive_path/);
  assert.throws(() => normalizeEntryName('/absolute/index.html'), /invalid_archive_path/);
});

test('rejects scripts that can reach the parent application', () => {
  assert.throws(() => auditZipBuffer(zip({
    'index.html': '<script src="app.js"></script>',
    'app.js': 'fetch("/api"); document.cookie;',
  })), (error) => error.message === 'preview_artifact_rejected' && error.issues.some((item) => item.rule === 'JS-RUNTIME-001'));
});
