import importlib.util
import json
import tempfile
import threading
import unittest
from unittest.mock import patch
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen


MODULE_PATH = Path(__file__).with_name("material-cache.py")
SPEC = importlib.util.spec_from_file_location("material_cache", MODULE_PATH)
material_cache = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(material_cache)


class MaterialCacheTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.old_root = material_cache.ROOT
        self.old_token = material_cache.SYNC_TOKEN
        material_cache.ROOT = Path(self.temporary.name)
        material_cache.SYNC_TOKEN = "test-sync-secret"
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), material_cache.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"
        self.key = "a" * 64
        self.revision = "b" * 40
        self.release = material_cache.ROOT / self.key / "releases" / self.revision
        (self.release / "exercises" / "starter").mkdir(parents=True)
        (self.release / "exercises" / "starter" / "README.md").write_text("starter\n")
        (self.release / "one.txt").write_text("one\n")

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        material_cache.ROOT = self.old_root
        material_cache.SYNC_TOKEN = self.old_token
        self.temporary.cleanup()

    def test_sync_requires_internal_bearer_token(self):
        request = Request(self.base_url + "/sync", data=b'{"url":"https://github.com/a/b"}', method="POST")
        with self.assertRaises(HTTPError) as error:
            urlopen(request, timeout=2)
        self.assertEqual(error.exception.code, 403)

    def test_metrics_exposes_cache_usage_and_limit(self):
        with urlopen(self.base_url + "/metrics", timeout=2) as response:
            metrics = response.read().decode()
        self.assertIn("insightbloom_material_cache_bytes ", metrics)
        self.assertIn(f"insightbloom_material_cache_limit_bytes {material_cache.MAX_CACHE_BYTES}", metrics)

    def test_sync_returns_snapshot_revision_for_authorized_users_client(self):
        original_sync = material_cache.sync
        material_cache.sync = lambda url, ref: (self.key, self.revision)
        try:
            request = Request(
                self.base_url + "/sync",
                data=json.dumps({"url": "https://github.com/a/b", "ref": "main"}).encode(),
                headers={"Authorization": "Bearer test-sync-secret", "Content-Type": "application/json"},
                method="POST",
            )
            with urlopen(request, timeout=2) as response:
                result = json.load(response)
            self.assertEqual(result, {"sourceKey": self.key, "revision": self.revision})
        finally:
            material_cache.sync = original_sync

    def test_download_streams_only_requested_subtree_at_pinned_revision(self):
        with urlopen(f"{self.base_url}/material/{self.key}/{self.revision}/exercises", timeout=2) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.headers.get_content_type(), "application/x-tar")
            data = response.read()
        with tempfile.TemporaryDirectory() as target:
            archive_path = Path(target, "response.tar")
            archive_path.write_bytes(data)
            extracted = Path(target, "extracted")
            extracted.mkdir()
            material_cache._extract_archive(archive_path, extracted)
            self.assertEqual(Path(extracted, "starter", "README.md").read_text(), "starter\n")
            self.assertFalse(Path(extracted, "one.txt").exists())

    def test_download_rejects_traversal_and_unknown_revision(self):
        with self.assertRaises(HTTPError) as traversal:
            urlopen(f"{self.base_url}/material/{self.key}/{self.revision}/%2e%2e/one.txt", timeout=2)
        self.assertEqual(traversal.exception.code, 404)
        with self.assertRaises(HTTPError) as unknown:
            urlopen(f"{self.base_url}/material/{self.key}/{'c' * 40}/one.txt", timeout=2)
        self.assertEqual(unknown.exception.code, 404)

    def test_download_rejects_symlink_that_escapes_release(self):
        outside = Path(self.temporary.name).parent / "outside-material-cache-test"
        outside.write_text("private\n")
        try:
            (self.release / "escape").symlink_to(outside)
            with self.assertRaises(ValueError):
                material_cache.resolve_material(self.key, self.revision, "escape")
        finally:
            outside.unlink(missing_ok=True)

    def test_cache_prune_keeps_current_and_one_previous_release(self):
        releases = self.release.parent
        for letter in "cde":
            (releases / (letter * 40)).mkdir()
        material_cache._prune(self.release.parent.parent, self.revision)
        names = {path.name for path in releases.iterdir() if path.is_dir()}
        self.assertIn(self.revision, names)
        self.assertEqual(len(names), 2)

    def test_sync_rejects_invalid_git_ref_before_fetch(self):
        with self.assertRaises(material_cache.CacheError):
            material_cache.sync("https://github.com/a/b", "--upload-pack=bad")

    def test_sync_rolls_back_snapshot_when_global_cache_limit_is_exceeded(self):
        old_limit = material_cache.MAX_CACHE_BYTES
        old_archive_limit = material_cache.MAX_ARCHIVE_BYTES
        material_cache.MAX_CACHE_BYTES = 100
        material_cache.MAX_ARCHIVE_BYTES = 50
        try:
            with patch.object(material_cache.subprocess, "run"), \
                    patch.object(material_cache.subprocess, "check_output", return_value=self.revision), \
                    patch.object(material_cache, "_archive_to_file", side_effect=lambda _mirror, _rev, out: out.write_bytes(b"tar")), \
                    patch.object(material_cache, "_extract_archive", side_effect=lambda _archive, tree: (tree / "file").write_text("data")), \
                    patch.object(material_cache, "_cache_size", side_effect=[0, 101]):
                with self.assertRaisesRegex(material_cache.CacheError, "material_cache_limit_reached"):
                    material_cache.sync("https://github.com/a/b", "main")
            key = material_cache.source_key("https://github.com/a/b", "main")
            self.assertFalse((material_cache.ROOT / key / "current").exists())
            self.assertFalse((material_cache.ROOT / key / "releases" / self.revision).exists())
        finally:
            material_cache.MAX_CACHE_BYTES = old_limit
            material_cache.MAX_ARCHIVE_BYTES = old_archive_limit


if __name__ == "__main__":
    unittest.main()
