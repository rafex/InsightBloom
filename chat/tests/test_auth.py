"""Tests unitarios para modelos Pydantic y validación de auth."""

import pytest
from pydantic import ValidationError

from models.schemas import JoinRequest, LoginRequest, RegisterRequest, SsoRequest


class TestRegisterRequest:
    def test_valid(self):
        r = RegisterRequest(phone="+525512345678", nickname="testuser", password="secret")
        assert r.phone == "+525512345678"
        assert r.nickname == "testuser"

    def test_empty_phone(self):
        with pytest.raises(ValidationError):
            RegisterRequest(phone="", nickname="test", password="s")

    def test_nickname_too_short(self):
        with pytest.raises(ValidationError):
            RegisterRequest(phone="+525512345678", nickname="a", password="s")

    def test_nickname_min_length(self):
        r = RegisterRequest(phone="+525512345678", nickname="ab", password="s")
        assert r.nickname == "ab"


class TestLoginRequest:
    def test_valid(self):
        r = LoginRequest(phone="+525512345678", password="secret")
        assert r.phone == "+525512345678"

    def test_empty_phone(self):
        with pytest.raises(ValidationError):
            LoginRequest(phone="", password="s")

    def test_empty_password(self):
        with pytest.raises(ValidationError):
            LoginRequest(phone="+525512345678", password="")


class TestSsoRequest:
    def test_valid(self):
        r = SsoRequest(code="abc123")
        assert r.code == "abc123"

    def test_empty_token(self):
        with pytest.raises(ValidationError):
            SsoRequest(code="")


class TestJoinRequest:
    def test_valid(self):
        r = JoinRequest(token="tok123", conference_id="conf456")
        assert r.token == "tok123"
        assert r.conference_id == "conf456"

    def test_empty_token(self):
        with pytest.raises(ValidationError):
            JoinRequest(token="", conference_id="c")

    def test_empty_conference_id(self):
        with pytest.raises(ValidationError):
            JoinRequest(token="t", conference_id="")
