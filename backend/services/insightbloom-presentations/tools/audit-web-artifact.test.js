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
    'index.html': '<!doctype html><form action="/resultado"><input name="q"></form><script src="app.js"></script>',
    'app.js': 'document.querySelector("body").textContent = "ok";',
    'app.css': 'body { color: #222; }',
  }));
  assert.equal(report.files.length, 3);
  assert.equal(report.artifactHash.length, 64);
});

test('accepts harmless http-equiv metadata but rejects redirects', () => {
  const report = auditZipBuffer(zip({
    'index.html': '<meta http-equiv="Content-Security-Policy" content="default-src \'self\'"><meta http-equiv="X-UA-Compatible" content="IE=edge"><h1>ok</h1><script>localStorage.setItem("ready", "1"); setTimeout(() => {}, 0);</script>',
  }));
  assert.equal(report.files.length, 1);
  assert.throws(() => auditZipBuffer(zip({
    'index.html': '<meta http-equiv="refresh" content="0;url=https://example.test">',
  })), (error) => error.message === 'preview_artifact_rejected'
    && error.issues.some((item) => item.rule === 'HTML-REDIRECT-001'));
});

test('rejects forms that can exfiltrate data outside the publication', () => {
  for (const action of ['https://example.test/collect', '//example.test/collect', '../collect']) {
    assert.throws(() => auditZipBuffer(zip({
      'index.html': `<form action="${action}"><input name="q"></form>`,
    })), (error) => error.message === 'preview_artifact_rejected'
      && error.issues.some((item) => item.rule === 'HTML-FORM-001'));
  }
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
