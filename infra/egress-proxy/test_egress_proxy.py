import os
import tempfile
import unittest
from pathlib import Path

os.environ["EGRESS_PROXY_ALLOWED_HOSTS"] = "github.com,*.githubusercontent.com"
os.environ["EGRESS_PROXY_BLOCKED_HOSTS"] = "raw.githubusercontent.com"

from egress_proxy import Policy


class PolicyTest(unittest.TestCase):
    def setUp(self):
        self.policy = Policy()

    def test_allows_github(self):
        self.assertTrue(self.policy.permits("github.com", 443))
        self.assertTrue(self.policy.permits("objects.githubusercontent.com", 443))

    def test_blocklist_has_precedence(self):
        self.assertFalse(self.policy.permits("raw.githubusercontent.com", 443))

    def test_rejects_other_hosts_and_ports(self):
        self.assertFalse(self.policy.permits("example.com", 443))
        self.assertFalse(self.policy.permits("github.com", 22))
        self.assertFalse(self.policy.permits("127.0.0.1", 443))

    def test_reloads_policy_from_configmap_files(self):
        with tempfile.TemporaryDirectory() as directory:
            config_dir = Path(directory)
            (config_dir / "EGRESS_PROXY_ENABLED").write_text("true")
            (config_dir / "EGRESS_PROXY_ALLOWED_HOSTS").write_text("github.com")
            (config_dir / "EGRESS_PROXY_BLOCKED_HOSTS").write_text("")
            policy = Policy(config_dir=config_dir)

            self.assertTrue(policy.permits("github.com", 443))
            (config_dir / "EGRESS_PROXY_ALLOWED_HOSTS").write_text("api.github.com")
            self.assertFalse(policy.permits("github.com", 443))
            self.assertTrue(policy.permits("api.github.com", 443))


if __name__ == "__main__":
    unittest.main()
