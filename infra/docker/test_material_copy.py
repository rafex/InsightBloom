import importlib.util
import os
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

from test_material_cache import material_cache


MODULE_PATH = Path(__file__).with_name("material-copy")
LOADER = importlib.machinery.SourceFileLoader("material_copy", str(MODULE_PATH))
SPEC = importlib.util.spec_from_loader("material_copy", LOADER)
material_copy = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(material_copy)


class MaterialCopyTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.old_root = material_cache.ROOT
        material_cache.ROOT = Path(self.temporary.name)
        self.key = "a" * 64
        self.revision = "b" * 40
        self.release = material_cache.ROOT / self.key / "releases" / self.revision
        (self.release / "exercises").mkdir(parents=True)
        (self.release / "exercises" / "starter.txt").write_text("from cache\n")
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), material_cache.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.old_env = {name: os.environ.get(name) for name in (
            "INSIGHTBLOOM_MATERIALS_ROOT", "INSIGHTBLOOM_MATERIAL_SOURCE",
            "INSIGHTBLOOM_MATERIAL_REVISION", "INSIGHTBLOOM_MATERIAL_CACHE_URL")}
        os.environ.update({
            "INSIGHTBLOOM_MATERIALS_ROOT": "/opt/insightbloom/materials",
            "INSIGHTBLOOM_MATERIAL_SOURCE": self.key + "/current",
            "INSIGHTBLOOM_MATERIAL_REVISION": self.revision,
            "INSIGHTBLOOM_MATERIAL_CACHE_URL": f"http://127.0.0.1:{self.server.server_port}",
        })

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        material_cache.ROOT = self.old_root
        for name, value in self.old_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.temporary.cleanup()

    def test_downloads_only_requested_subtree_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as workspace:
            destination = Path(workspace)
            (destination / "starter.txt").write_text("student work\n")
            material_copy.copy_material(
                f"/opt/insightbloom/materials/{self.key}/current/exercises", workspace)
            self.assertEqual((destination / "starter.txt").read_text(), "student work\n")

    def test_rejects_paths_outside_configured_virtual_material_root(self):
        with tempfile.TemporaryDirectory() as workspace:
            with self.assertRaises(SystemExit) as error:
                material_copy.copy_material("/etc/passwd", workspace)
            self.assertEqual(error.exception.code, 64)

    def test_rejects_symlink_destination_escape(self):
        with tempfile.TemporaryDirectory() as workspace, tempfile.TemporaryDirectory() as outside:
            destination = Path(workspace)
            (destination / "starter.txt").symlink_to(Path(outside) / "outside.txt")
            with self.assertRaises(SystemExit):
                material_copy.copy_material(
                    f"/opt/insightbloom/materials/{self.key}/current/exercises", workspace)
            self.assertFalse((Path(outside) / "outside.txt").exists())


if __name__ == "__main__":
    unittest.main()
