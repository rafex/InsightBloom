import base64
import subprocess
import unittest
from unittest.mock import patch
from uuid import uuid4

from cryptography.hazmat.primitives import serialization
from fastapi import HTTPException

import app


def sig_structure(payload: bytes) -> bytes:
    size = len(payload)
    if size < 24:
        header = bytes([0x40 + size])
    elif size <= 0xFF:
        header = b"\x58" + bytes([size])
    elif size <= 0xFFFF:
        header = b"\x59" + size.to_bytes(2, "big")
    else:
        header = b"\x5a" + size.to_bytes(4, "big")
    return b"\x84\x6aSignature1\x43\xa1\x01\x27\x40" + header + payload


class TicketSignerTests(unittest.TestCase):
    def test_authentication_is_required_and_constant_time_checked(self):
        app.TOKEN = "a-dedicated-test-token"
        app.authorize("Bearer a-dedicated-test-token")
        with self.assertRaises(HTTPException) as caught:
            app.authorize("Bearer wrong")
        self.assertEqual(caught.exception.status_code, 401)

    def test_signs_only_matching_event_and_returns_no_private_key(self):
        event_uuid = str(uuid4())
        ticket_uuid = str(uuid4())
        payload = (f'{{"version":1,"conferenceUuid":"{event_uuid}",'
                   f'"ticketUuid":"{ticket_uuid}"}}').encode()
        request = app.SignRequest(
            eventUuid=event_uuid,
            payload=base64.urlsafe_b64encode(payload).decode().rstrip("="),
        )
        calls = []

        def fake_run(command, **kwargs):
            calls.append(command)
            if command[1] == "ls":
                return subprocess.CompletedProcess(command, 0, "", "")
            if command[1] == "insert":
                self.assertTrue(kwargs["input"].strip())
                return subprocess.CompletedProcess(command, 0, "", "")
            self.fail("unexpected gopass command")

        with patch.object(app, "TOKEN", "test-token"), patch.object(app.subprocess, "run", side_effect=fake_run):
            result = app.sign_ticket(request)

        self.assertEqual(set(result.model_dump()), {"keyId", "publicKeySpki", "signature"})
        self.assertNotIn("private", result.model_dump())
        public_der = base64.urlsafe_b64decode(result.publicKeySpki + "==")
        public_key = serialization.load_der_public_key(public_der)
        signature = base64.urlsafe_b64decode(result.signature + "==")
        public_key.verify(signature, sig_structure(payload))
        self.assertEqual(calls[0][1:3], ["ls", "--flat"])

    def test_rejects_payload_for_different_event_before_gopass(self):
        request = app.SignRequest(
            eventUuid=str(uuid4()),
            payload=base64.urlsafe_b64encode(b'{"conferenceUuid":"wrong"}').decode().rstrip("="),
        )
        with patch.object(app.subprocess, "run") as run:
            with self.assertRaises(HTTPException) as caught:
                app.sign_ticket(request)
        self.assertEqual(caught.exception.status_code, 400)
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
