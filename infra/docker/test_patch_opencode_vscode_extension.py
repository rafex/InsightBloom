import json
import subprocess
import tempfile
import unittest
from pathlib import Path


PATCHER = Path(__file__).with_name("patch-opencode-vscode-extension.mjs")
EXPECTED = 'o.startsWith("opencode server listening")'


class OpenCodeExtensionPatchTest(unittest.TestCase):
    def make_extension(self, bundle: str) -> Path:
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        extension = Path(temp.name) / "extension"
        (extension / "dist").mkdir(parents=True)
        (extension / "package.json").write_text(
            json.dumps({"publisher": "sst-dev", "name": "opencode-v2", "version": "0.1.1"}),
            encoding="utf-8",
        )
        (extension / "dist" / "extension.js").write_text(bundle, encoding="utf-8")
        return extension

    def patch(self, extension: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["node", str(PATCHER), str(extension)],
            capture_output=True,
            text=True,
            check=False,
        )

    def test_patches_server_start_marker_and_authentication(self) -> None:
        extension = self.make_extension(f"const o={{ startsWith: () => false }}; const marker={EXPECTED};")

        result = self.patch(extension)

        self.assertEqual(result.returncode, 0, result.stderr)
        bundle = (extension / "dist" / "extension.js").read_text(encoding="utf-8")
        self.assertIn('o.startsWith("server listening")', bundle)
        self.assertIn('OPENCODE_SERVER_PASSWORD=password', bundle)
        self.assertIn('OPENCODE_SERVER_USERNAME="opencode"', bundle)
        self.assertIn('headers.set("Authorization",authorization)', bundle)
        self.assertIn('"/health":"/global/health"', bundle)
        self.assertIn('"/app/providers":"/config/providers"', bundle)
        self.assertEqual(
            subprocess.run(["node", "--check", str(extension / "dist" / "extension.js")], check=False).returncode,
            0,
        )

    def test_legacy_extension_routes_are_translated_and_authenticated(self) -> None:
        extension = self.make_extension(f"const o={{ startsWith: () => false }}; const marker={EXPECTED};")
        result = self.patch(extension)
        self.assertEqual(result.returncode, 0, result.stderr)

        runtime_check = r"""
;(async () => {
const fs = require("node:fs");
const calls = [];
globalThis.fetch = async (input, init = {}) => {
  calls.push({
    url: input instanceof Request ? input.url : String(input),
    authorization: new Headers(init.headers).get("Authorization"),
  });
  return new Response("{}", { status: 200 });
};
eval(fs.readFileSync(process.argv[1], "utf8"));
await fetch("http://localhost:4096/health");
await fetch("http://localhost:4096/app/providers");
console.log(JSON.stringify(calls));
})().catch((error) => { console.error(error); process.exitCode = 1; });
"""
        checked = subprocess.run(
            ["node", "-e", runtime_check, str(extension / "dist" / "extension.js")],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(checked.returncode, 0, checked.stderr)
        calls = json.loads(checked.stdout)
        self.assertEqual(
            [call["url"] for call in calls],
            ["http://localhost:4096/global/health", "http://localhost:4096/config/providers"],
        )
        self.assertTrue(all(call["authorization"].startswith("Basic ") for call in calls))

    def test_refuses_unknown_bundle_without_modifying_it(self) -> None:
        original = "const marker = 'different output';"
        extension = self.make_extension(original)

        result = self.patch(extension)

        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((extension / "dist" / "extension.js").read_text(encoding="utf-8"), original)

    def test_refuses_unexpected_extension_version(self) -> None:
        extension = self.make_extension(f"const marker={EXPECTED};")
        manifest = extension / "package.json"
        manifest.write_text(manifest.read_text(encoding="utf-8").replace("0.1.1", "0.1.2"), encoding="utf-8")

        result = self.patch(extension)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unexpected OpenCode extension package", result.stderr)


if __name__ == "__main__":
    unittest.main()
