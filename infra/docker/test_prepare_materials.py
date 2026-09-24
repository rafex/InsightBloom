import os
import subprocess
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

from test_material_cache import material_cache


DOCKER_DIR = Path(__file__).parent


class PrepareMaterialsTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.old_root = material_cache.ROOT
        material_cache.ROOT = Path(self.temporary.name) / "cache"
        self.key = "a" * 64
        self.revision = "b" * 40
        release = material_cache.ROOT / self.key / "releases" / self.revision
        release.mkdir(parents=True)
        (release / "setup.sh").write_text(
            'printf "prepared\\n" > "$INSIGHTBLOOM_WORKSPACE/material.txt"\n')
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), material_cache.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.workspace = Path(self.temporary.name) / "workspace"
        self.workspace.mkdir()
        self.copy_wrapper = Path(self.temporary.name) / "material-copy-test"
        self.copy_wrapper.write_text(f"#!/bin/sh\nexec python3 '{DOCKER_DIR / 'material-copy'}' \"$@\"\n")
        self.copy_wrapper.chmod(0o755)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        material_cache.ROOT = self.old_root
        self.temporary.cleanup()

    def run_preparer(self, **overrides):
        env = {
            **os.environ,
            "INSIGHTBLOOM_WORKSPACE": str(self.workspace),
            "INSIGHTBLOOM_MATERIAL_COPY_BIN": str(self.copy_wrapper),
            "INSIGHTBLOOM_MATERIALS_ROOT": "/opt/insightbloom/materials",
            "INSIGHTBLOOM_MATERIAL_SOURCE": self.key + "/current",
            "INSIGHTBLOOM_MATERIAL_REVISION": self.revision,
            "INSIGHTBLOOM_MATERIAL_CACHE_URL": f"http://127.0.0.1:{self.server.server_port}",
            **overrides,
        }
        return subprocess.run(["sh", str(DOCKER_DIR / "prepare-materials.sh"), str(self.workspace)],
                              env=env, capture_output=True, text=True, check=True, timeout=20)

    def test_versioned_script_is_downloaded_at_pinned_revision_before_ide(self):
        self.run_preparer(
            INSIGHTBLOOM_BOOTSTRAP_KIND="shell",
            INSIGHTBLOOM_BOOTSTRAP_SOURCE="material",
            INSIGHTBLOOM_BOOTSTRAP_PATH="setup.sh",
        )
        log = (self.workspace / ".insightbloom/bootstrap.log").read_text()
        self.assertIn('"status": "ready"',
                      (self.workspace / ".insightbloom/bootstrap-status.json").read_text(), log)
        self.assertEqual((self.workspace / "material.txt").read_text(), "prepared\n",
                         log)
        status = (self.workspace / ".insightbloom/bootstrap-status.json").read_text()
        self.assertIn(self.revision, status)
        self.assertIn('"status": "ready"', status)

    def test_inline_script_runs_and_records_revision(self):
        self.run_preparer(
            INSIGHTBLOOM_BOOTSTRAP_KIND="shell",
            INSIGHTBLOOM_BOOTSTRAP_SOURCE="inline",
            INSIGHTBLOOM_BOOTSTRAP_INLINE='printf "inline\\n" > "$INSIGHTBLOOM_WORKSPACE/inline.txt"',
        )
        self.assertEqual((self.workspace / "inline.txt").read_text(), "inline\n")
        self.assertIn(self.revision, (self.workspace / ".insightbloom/bootstrap-status.json").read_text())

    def test_inline_python_script_runs_as_configured_kind(self):
        self.run_preparer(
            INSIGHTBLOOM_BOOTSTRAP_KIND="python",
            INSIGHTBLOOM_BOOTSTRAP_SOURCE="inline",
            INSIGHTBLOOM_BOOTSTRAP_INLINE=(
                'from pathlib import Path; import os; '
                'Path(os.environ["INSIGHTBLOOM_WORKSPACE"], "python.txt").write_text("python\\n")'),
        )
        self.assertEqual((self.workspace / "python.txt").read_text(), "python\n")


if __name__ == "__main__":
    unittest.main()
