const test = require('node:test');
const assert = require('node:assert/strict');
const AdmZip = require('adm-zip');
const { auditZipBuffer } = require('../tools/audit-web-artifact');

function archive(files) {
  const zip = new AdmZip();
  for (const [name, body] of Object.entries(files)) zip.addFile(name, Buffer.from(body));
  return zip.toBuffer();
}

test('audita una página estática válida y excluye metadatos de proyecto', () => {
  const result = auditZipBuffer(archive({ 'index.html': '<h1>ok</h1>', 'app.js': 'console.log(1)', 'package.json': '{}' }));
  assert.equal(result.files.length, 2);
  assert.deepEqual(result.excludedFiles, ['package.json']);
});

test('rechaza contenido activo y escapes del sandbox', () => {
  assert.throws(() => auditZipBuffer(archive({ 'index.html': '<iframe src="https://example.com"></iframe>' })), /preview_artifact_rejected/);
  assert.throws(() => auditZipBuffer(archive({ 'index.html': '<script>document.cookie</script>' })), /preview_artifact_rejected/);
});
