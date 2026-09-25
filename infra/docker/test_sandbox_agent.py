import os
import importlib.util
from pathlib import Path
from unittest.mock import patch

MODULE_SPEC = importlib.util.spec_from_file_location(
    "sandbox_agent", Path(__file__).with_name("sandbox-agent.py")
)
sandbox_agent = importlib.util.module_from_spec(MODULE_SPEC)
assert MODULE_SPEC.loader is not None
MODULE_SPEC.loader.exec_module(sandbox_agent)


def test_multi_seat_remote_git_seed_runs_as_seat_user():
    with patch.dict(os.environ, {"REMOTE_GIT_URL": "https://github.com/example/repo.git"}, clear=False), \
            patch.object(sandbox_agent.subprocess, "run") as run:
        sandbox_agent._seed_remote_git(
            2000,
            0,
            "/home/user-uuid",
            "/home/user-uuid/workspace",
        )

    args, kwargs = run.call_args
    assert args[0] == [sandbox_agent.REMOTE_GIT_SEEDER, "/home/user-uuid/workspace"]
    assert kwargs["check"] is True
    assert kwargs["env"]["REMOTE_GIT_URL"] == "https://github.com/example/repo.git"
    assert kwargs["env"]["REMOTE_GIT_MIGRATE_SEED_ONLY"] == "1"


def test_multi_seat_node_types_uses_local_compatibility_mode_after_materials():
    calls = []

    def record_run(command, **kwargs):
        calls.append((command, kwargs))

    with patch.object(sandbox_agent.os, "makedirs"), \
            patch.object(sandbox_agent.os, "chmod"), \
            patch.object(sandbox_agent.os.path, "exists", return_value=False), \
            patch.object(sandbox_agent, "_user_exists", return_value=True), \
            patch.object(sandbox_agent, "_seed_remote_git") as seed_git, \
            patch.object(sandbox_agent.subprocess, "run", side_effect=record_run):
        sandbox_agent._ensure_seat_account(0, "12345678-1234-1234-1234-123456789abc")

    seed_git.assert_called_once()
    assert calls[-2][0] == [sandbox_agent.MATERIAL_PREPARER,
                           "/home/12345678-1234-1234-1234-123456789abc/workspace"]
    assert calls[-1][0] == [sandbox_agent.NODE_TYPES_SEEDER, "--workspace",
                           "/home/12345678-1234-1234-1234-123456789abc/workspace"]
    assert calls[-1][1]["env"]["HOME"] == "/home/12345678-1234-1234-1234-123456789abc"


def test_multi_seat_main_seeds_shared_node_types_before_listening():
    events = []

    class FakeServer:
        def __init__(self, address, handler):
            events.append(("server", address))

        def serve_forever(self):
            events.append(("serve", None))

    with patch("sys.argv", ["sandbox-agent.py", "--base-port", "8080", "--max-seats", "4"]), \
            patch.object(sandbox_agent.os, "chmod"), \
            patch.object(sandbox_agent.subprocess, "run",
                         side_effect=lambda command, **kwargs: events.append(("seed", command))), \
            patch.object(sandbox_agent, "_watchdog_loop"), \
            patch.object(sandbox_agent.threading, "Thread") as thread, \
            patch.object(sandbox_agent, "ThreadingHTTPServer", FakeServer):
        thread.return_value.start.side_effect = lambda: events.append(("watchdog", None))
        sandbox_agent.main()

    assert events[0] == ("seed", [sandbox_agent.NODE_TYPES_SEEDER, "--global", "/home/node_modules"])
    assert events.index(("seed", [sandbox_agent.NODE_TYPES_SEEDER, "--global", "/home/node_modules"])) \
        < next(i for i, event in enumerate(events) if event[0] == "server")
