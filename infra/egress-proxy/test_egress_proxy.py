import time
import unittest

from egress_proxy import Policy


def fixed_fetch(response):
    return lambda source_ip: response


class PolicyTest(unittest.TestCase):
    def test_allows_and_blocks_per_effective_policy(self):
        policy = Policy(fetch_fn=fixed_fetch({
            "known": True, "internetEnabled": True,
            "allowed": ["github.com", "*.githubusercontent.com"],
            "blocked": ["raw.githubusercontent.com"],
        }))
        self.assertTrue(policy.permits("10.0.0.1", "github.com", 443))
        self.assertTrue(policy.permits("10.0.0.1", "objects.githubusercontent.com", 443))

    def test_blocklist_has_precedence_over_allowlist(self):
        policy = Policy(fetch_fn=fixed_fetch({
            "known": True, "internetEnabled": True,
            "allowed": ["raw.githubusercontent.com"],
            "blocked": ["raw.githubusercontent.com"],
        }))
        self.assertFalse(policy.permits("10.0.0.1", "raw.githubusercontent.com", 443))

    def test_rejects_hosts_not_in_allowlist_and_bad_ports_and_raw_ips(self):
        policy = Policy(fetch_fn=fixed_fetch({
            "known": True, "internetEnabled": True, "allowed": ["github.com"], "blocked": [],
        }))
        self.assertFalse(policy.permits("10.0.0.1", "example.com", 443))
        self.assertFalse(policy.permits("10.0.0.1", "github.com", 22))
        self.assertFalse(policy.permits("10.0.0.1", "127.0.0.1", 443))

    def test_denies_when_internet_disabled_for_the_event(self):
        policy = Policy(fetch_fn=fixed_fetch({
            "known": True, "internetEnabled": False, "allowed": ["github.com"], "blocked": [],
        }))
        self.assertFalse(policy.permits("10.0.0.1", "github.com", 443))

    def test_denies_unknown_source_ip(self):
        policy = Policy(fetch_fn=fixed_fetch({"known": False}))
        self.assertFalse(policy.permits("10.0.0.1", "github.com", 443))

    def test_denies_when_never_resolved_and_fetch_fails(self):
        policy = Policy(fetch_fn=fixed_fetch(None))
        self.assertFalse(policy.permits("10.0.0.1", "github.com", 443))

    def test_fail_open_serves_last_known_policy_when_fetch_fails(self):
        calls = {"n": 0}

        def flaky_fetch(source_ip):
            calls["n"] += 1
            if calls["n"] == 1:
                return {"known": True, "internetEnabled": True, "allowed": ["github.com"], "blocked": []}
            return None  # insightbloom-users "cae" en la segunda llamada

        policy = Policy(fetch_fn=flaky_fetch, cache_ttl_seconds=0, stale_max_seconds=300)
        self.assertTrue(policy.permits("10.0.0.1", "github.com", 443))
        # TTL=0 fuerza un refetch inmediato; como el segundo fetch falla, debe servir
        # la ultima politica buena en vez de denegar todo de golpe.
        self.assertTrue(policy.permits("10.0.0.1", "github.com", 443))
        self.assertEqual(2, calls["n"])

    def test_fail_closed_once_stale_window_expires(self):
        def failing_fetch(source_ip):
            return None

        policy = Policy(fetch_fn=failing_fetch, cache_ttl_seconds=0, stale_max_seconds=0.05)
        policy._cache["10.0.0.1"] = __import__("egress_proxy").PolicyEntry(
            fetched_at=time.time() - 1, known=True, internet_enabled=True,
            allowed=frozenset({"github.com"}), blocked=frozenset())

        self.assertFalse(policy.permits("10.0.0.1", "github.com", 443))

    def test_cache_avoids_refetching_within_ttl(self):
        calls = {"n": 0}

        def counting_fetch(source_ip):
            calls["n"] += 1
            return {"known": True, "internetEnabled": True, "allowed": ["github.com"], "blocked": []}

        policy = Policy(fetch_fn=counting_fetch, cache_ttl_seconds=60)
        policy.permits("10.0.0.1", "github.com", 443)
        policy.permits("10.0.0.1", "github.com", 443)
        policy.permits("10.0.0.1", "github.com", 443)

        self.assertEqual(1, calls["n"])

    def test_cache_is_per_source_ip(self):
        def fetch(source_ip):
            if source_ip == "10.0.0.1":
                return {"known": True, "internetEnabled": True, "allowed": ["a.example.com"], "blocked": []}
            return {"known": True, "internetEnabled": True, "allowed": ["b.example.com"], "blocked": []}

        policy = Policy(fetch_fn=fetch)
        self.assertTrue(policy.permits("10.0.0.1", "a.example.com", 443))
        self.assertFalse(policy.permits("10.0.0.1", "b.example.com", 443))
        self.assertTrue(policy.permits("10.0.0.2", "b.example.com", 443))
        self.assertFalse(policy.permits("10.0.0.2", "a.example.com", 443))


if __name__ == "__main__":
    unittest.main()
