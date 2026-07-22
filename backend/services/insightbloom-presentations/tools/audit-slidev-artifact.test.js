const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const AdmZip = require('adm-zip');
const { auditArchive } = require('./audit-slidev-artifact');

function digest(buffer) {
  return `sha256:${crypto.createHash('sha256').update(buffer).digest('hex')}`;
}

function createZip(entries) {
  const zip = new AdmZip();
  for (const [name, value] of Object.entries(entries)) zip.addFile(name, Buffer.from(value));
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'slidev-audit-')), 'artifact.zip');
  zip.writeZip(file);
  return file;
}

test('accepts a static Slidev artifact with matching hashes', () => {
  const html = '<!doctype html><html><body><script src="./assets/index.js"></script></body></html>';
  const js = 'console.log("slidev");';
  const manifest = JSON.stringify({
    engine: 'slidev',
    engineVersion: '52.18.0',
    artifactFormat: 'static',
    base: 'relative',
    indexFile: 'index.html',
    files: {
      'dist/index.html': digest(Buffer.from(html)),
      'dist/assets/index.js': digest(Buffer.from(js)),
    },
    signature: { algorithm: 'ed25519', keyId: 'test', value: 'test' },
  });
  const zipPath = createZip({
    'slidev-artifact.json': manifest,
    'dist/index.html': html,
    'dist/assets/index.js': js,
  });
  try {
    const report = auditArchive(zipPath);
    assert.equal(report.decision, 'ACCEPT');
    assert.equal(report.issues.length, 0);
  } finally {
    fs.rmSync(path.dirname(zipPath), { recursive: true, force: true });
  }
});

test('rejects source files outside the static artifact allowlist', () => {
  const zipPath = createZip({
    'slidev-artifact.json': JSON.stringify({ engine: 'slidev', artifactFormat: 'static', files: {} }),
    'dist/index.html': '<!doctype html>',
    'source/slides.md': '# source no publicable',
  });
  try {
    const report = auditArchive(zipPath);
    assert.equal(report.decision, 'REJECT');
    assert.ok(report.issues.some((item) => item.rule === 'ARCHIVE-006'));
  } finally {
    fs.rmSync(path.dirname(zipPath), { recursive: true, force: true });
  }
});
