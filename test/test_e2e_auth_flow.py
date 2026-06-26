"""
Test end-to-end del flujo de autenticación y conferencias.

Nota: el login solo acepta username/email (no teléfono) — ver phase
"Login: restrict to email only" — por eso estos tests autentican con
el campo `email` en vez de `phone`, a diferencia de la versión original
de este test.
"""

import httpx
import pytest


pytestmark = pytest.mark.usefixtures("compose_up")


def test_health_check(users_url):
    r = httpx.get(f"{users_url}/health")
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_register_login(users_url):
    phone = "+525512345678"
    email = "test@example.com"
    password = "test123"

    # Registrar
    r = httpx.post(
        f"{users_url}/api/v1/auth/register",
        json={
            "displayName": "TestUser",
            "phone": phone,
            "email": email,
            "password": password,
            "socialLinks": [],
        },
    )
    assert r.status_code in (201, 409)  # 409 si ya existe

    # Login con email (el login por teléfono ya no está soportado)
    r = httpx.post(
        f"{users_url}/api/v1/auth/login",
        json={"username": email, "password": password},
    )
    assert r.status_code == 201
    data = r.json()
    assert "data" in data
    assert "token" in data["data"]


def test_register_defaults_to_attendee_and_cannot_create_conference(users_url):
    """
    El registro público (POST /api/v1/auth/register) siempre asigna el rol
    ATTENDEE — solo el usuario organizador (sembrado fuera de este flujo,
    p.ej. por el CLI admin en producción) puede crear conferencias. Este
    test documenta esa restricción en vez de asumir que cualquier cuenta
    registrada puede crear una.
    """
    email = "attendee@example.com"
    password = "conf123"

    httpx.post(
        f"{users_url}/api/v1/auth/register",
        json={
            "displayName": "AttendeeUser",
            "phone": "+525598765432",
            "email": email,
            "password": password,
            "socialLinks": [],
        },
    )
    r = httpx.post(
        f"{users_url}/api/v1/auth/login",
        json={"username": email, "password": password},
    )
    token = r.json()["data"]["token"]

    r = httpx.post(
        f"{users_url}/api/v1/conferences",
        json={"name": "TestConf 2026"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 403


def test_get_conference_by_friendly_id_not_found(users_url):
    r = httpx.get(f"{users_url}/api/v1/conferences/by-friendly/does-not-exist")
    assert r.status_code == 404
