import importlib.util
import pathlib
import unittest
from types import SimpleNamespace
from unittest.mock import patch


MODULE = pathlib.Path(__file__).parents[1] / "podman-agent.py"
SPEC = importlib.util.spec_from_file_location("podman_agent", MODULE)
podman_agent = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(podman_agent)


class PodmanAgentTest(unittest.TestCase):
    @patch.object(podman_agent, "run", return_value=SimpleNamespace(returncode=0, stderr=""))
    def test_build_and_run_uses_isolated_publication_name_and_port(self, run):
        result = podman_agent.build_and_run("FROM python:3.12\nEXPOSE 8000\n", 9500, 8000)

        self.assertEqual({"ok": True}, result)
        commands = [call.args[0] for call in run.call_args_list]
        self.assertEqual(commands[0][:4], ["podman", "build", "-t", "insightbloom-pub-9500"])
        self.assertIn("9500:8000", commands[-1])


if __name__ == "__main__":
    unittest.main()
