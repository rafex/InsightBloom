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
