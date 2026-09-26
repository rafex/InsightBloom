"""Verify the sandbox egress firewall opens only public IPv4 ICMP echo requests."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("lockdown-egress.sh")


class LockdownEgressTest(unittest.TestCase):
    def test_adds_public_echo_request_rule_without_opening_other_icmp(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            nft_log = temp / "nft.log"
            nft = temp / "nft"
            nft.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$NFT_LOG"\n')
            nft.chmod(0o755)
            env = os.environ | {
                "PATH": f"{temp}:{os.environ['PATH']}",
                "NFT_LOG": str(nft_log),
                "SANDBOX_CLUSTER_CIDR": "10.0.0.0/8",
            }

            subprocess.run(["sh", str(SCRIPT)], env=env, check=True, capture_output=True, text=True)
            commands = nft_log.read_text().splitlines()
            icmp_rules = [command for command in commands if "ip protocol icmp" in command]

        self.assertEqual(len(icmp_rules), 1)
        rule = icmp_rules[0]
        self.assertIn("icmp type echo-request", rule)
        self.assertIn("10.0.0.0/8", rule)
        self.assertIn("127.0.0.0/8", rule)
        self.assertIn("169.254.0.0/16", rule)
        self.assertIn("192.168.0.0/16", rule)
        self.assertTrue(rule.endswith("accept"))
        self.assertNotIn("icmp type echo-reply accept", rule)


if __name__ == "__main__":
    unittest.main()
