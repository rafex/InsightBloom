import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("seed-node-types.sh")


def typescript_api_path():
    configured = os.environ.get("INSIGHTBLOOM_TYPESCRIPT_API")
    if configured and Path(configured).is_file():
        return configured
    tsc = shutil.which("tsc")
    if not tsc:
        raise unittest.SkipTest("TypeScript no está instalado en el host de pruebas")
    current = Path(tsc).resolve()
    for parent in current.parents:
        candidate = parent / "lib" / "typescript.js"
        if candidate.is_file():
            return str(candidate)
    raise unittest.SkipTest("No se encontró la API de TypeScript junto a tsc")


class SeedNodeTypesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.home = self.root / "home"
        self.home.mkdir()
        self.workspace = self.home / "student" / "workspace"
        self.workspace.mkdir(parents=True)
        self.source = self.root / "node-types" / "node_modules"
        node_types = self.source / "@types" / "node"
        node_types.mkdir(parents=True)
        (node_types / "package.json").write_text('{"name":"@types/node","types":"index.d.ts"}\n')
        (node_types / "index.d.ts").write_text(
            'declare class Buffer { static from(value: Buffer): Buffer; }\n'
            'declare module "node:fs" { export function readFileSync(path: string): Buffer; }\n'
            'declare const process: { cwd(): string };\n'
        )

    def tearDown(self):
        self.temporary.cleanup()

    def run_seeder(self, *arguments):
        return subprocess.run(
            ["sh", str(SCRIPT), *map(str, arguments)],
            env={
                **os.environ,
                "INSIGHTBLOOM_NODE_TYPES_SOURCE": str(self.source),
                "INSIGHTBLOOM_TYPESCRIPT_API": typescript_api_path(),
            },
            capture_output=True,
            text=True,
            check=False,
        )

    def test_global_mode_creates_read_only_ancestor_links_idempotently(self):
        target = self.home / "node_modules"
        first = self.run_seeder("--global", target)
        self.assertEqual(first.returncode, 0, first.stderr)
        link = target / "@types" / "node"
        self.assertTrue((target / "@types").is_symlink() or link.is_symlink())
        self.assertEqual(link.resolve(), (self.source / "@types" / "node").resolve())
        self.assertEqual(target.stat().st_mode & 0o777, 0o755)
        second = self.run_seeder("--global", target)
        self.assertEqual(second.returncode, 0, second.stderr)

    def test_typescript_resolves_node_symbols_from_ancestor_without_workspace_node_modules(self):
        result = self.run_seeder("--global", self.home / "node_modules")
        self.assertEqual(result.returncode, 0, result.stderr)
        source_file = self.workspace / "index.ts"
        source_file.write_text(
            'import { readFileSync } from "node:fs";\n'
            'const cwd: string = process.cwd();\n'
            'const data: Buffer = readFileSync(cwd);\n'
            'const buffer: Buffer = Buffer.from(data);\n'
        )
        api = typescript_api_path()
        compile = subprocess.run(
            ["node", "-e", """
const ts = require(process.argv[1]);
const file = process.argv[2];
const program = ts.createProgram([file], { strict: true, noEmit: true, types: ['node'] });
const errors = ts.getPreEmitDiagnostics(program);
if (errors.length) {
  console.error(ts.formatDiagnosticsWithColorAndContext(errors, {
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    getCanonicalFileName: (name) => name,
    getNewLine: () => '\\n',
  }));
  process.exit(1);
}
""", api, str(source_file)],
            cwd=self.workspace,
            capture_output=True,
            text=True,
        )
        self.assertEqual(compile.returncode, 0, compile.stderr)
        self.assertFalse((self.workspace / "node_modules").exists())

    def test_workspace_mode_does_not_seed_normal_workspace(self):
        result = self.run_seeder("--workspace", self.workspace)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.workspace / "node_modules").exists())

    def test_effective_type_roots_inherited_through_extends_get_local_fallback(self):
        base = self.workspace / "base.json"
        base.write_text('{"compilerOptions":{"typeRoots":["./node_modules/@types"]}}\n')
        (self.workspace / "tsconfig.json").write_text('{"extends":"./base.json"}\n')
        result = self.run_seeder("--workspace", self.workspace)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue((self.workspace / "node_modules/@types").is_symlink(),
                        result.stdout + result.stderr)

    def test_existing_local_package_is_never_overwritten(self):
        (self.workspace / "tsconfig.json").write_text(
            '{"compilerOptions":{"typeRoots":["./node_modules/@types"]}}\n')
        local = self.workspace / "node_modules/@types/node"
        local.mkdir(parents=True)
        (local / "local.d.ts").write_text("// project package\n")
        result = self.run_seeder("--workspace", self.workspace)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(local.is_symlink())
        self.assertTrue((local / "local.d.ts").is_file())


if __name__ == "__main__":
    unittest.main()
