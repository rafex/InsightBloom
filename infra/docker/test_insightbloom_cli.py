import importlib.util
import json
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("insightbloom-publish.py")
SPEC = importlib.util.spec_from_file_location("insightbloom_publish", MODULE_PATH)
assert SPEC and SPEC.loader
cli = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cli)


def test_otp_login_never_requests_password(tmp_path):
    session = tmp_path / "session.json"
    calls = []

    def request_json(method, url, **kwargs):
        calls.append((method, url, kwargs.get("body")))
        if url.endswith("/otp/request"):
            return {}
        return {"data": {"token": "otp-token", "expiresAt": "2099-01-01T00:00:00Z"}}

    with patch.object(cli, "session_file", return_value=session), \
         patch.object(cli, "request_json", side_effect=request_json), \
         patch("builtins.input", return_value="speaker@example.com"), \
         patch.object(cli.getpass, "getpass", return_value="123456") as getpass:
        token = cli.login_session("https://api.example", otp_only=True)

    assert token == "otp-token"
    assert [url.rsplit("/", 1)[-1] for _, url, _ in calls] == ["request", "verify"]
    assert all(b"password" not in (body or b"") for _, _, body in calls)
    assert getpass.call_args.args[0] == "Código de acceso (oculto): "
    assert json.loads(session.read_text())["authMethod"] == "otp_email"


def test_expired_otp_session_renews_without_password(tmp_path):
    session = tmp_path / "session.json"
    session.write_text(json.dumps({"token": "expired", "authMethod": "otp_email"}))
    args = Namespace(token="", token_prompt=False, token_stdin=False, otp=False)
    calls = []

    def operation(auth):
        calls.append(auth.token)
        if len(calls) == 1:
            raise cli.ApiError(401, "expired")
        return {"ok": True}

    with patch.object(cli, "session_file", return_value=session), \
         patch.object(cli, "login_session", return_value="renewed") as login:
        result = cli.authenticated_request(args, operation, "https://api.example")

    assert result == {"ok": True}
    assert calls == ["expired", "renewed"]
    login.assert_called_once_with("https://api.example", otp_only=True)


def test_sandbox_capability_has_priority_over_saved_session(tmp_path):
    session = tmp_path / "session.json"
    session.write_text(json.dumps({"token": "account-token", "authMethod": "otp_email"}))
    capability = tmp_path / "sandbox-token"
    capability.write_text("sandbox-capability\n")
    args = Namespace(token="", token_prompt=False, token_stdin=False, otp=False)
    calls = []

    def operation(auth):
        calls.append(auth)
        return {"ok": True}

    with patch.object(cli, "session_file", return_value=session), \
         patch.dict("os.environ", {"INSIGHTBLOOM_SANDBOX_TOKEN_FILE": str(capability)}, clear=False):
        result = cli.authenticated_request(args, operation, "https://api.example")

    assert result == {"ok": True}
    assert calls[0] == cli.AuthContext(sandbox_capability="sandbox-capability")
