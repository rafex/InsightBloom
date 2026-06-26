"""
Fixtures compartidos para tests de integración de InsightBloom.

Estrategia: levantar el stack completo con Docker Compose, esperar healthchecks,
ejecutar tests HTTP contra los servicios reales, y derribar al finalizar.
"""

import os
import subprocess
import time

import httpx
import pytest

COMPOSE_FILE = os.path.join(
    os.path.dirname(__file__), "..", "container", "compose.yml"
)
COMPOSE_PROJECT = "insightbloom-test"


def _run_compose(cmd: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["docker", "compose", "-f", COMPOSE_FILE, "-p", COMPOSE_PROJECT] + cmd,
        capture_output=True,
        text=True,
    )


@pytest.fixture(scope="session")
def compose_up():
    """Levanta el stack completo antes de los tests y lo derriba al finalizar."""
    print("\n[conftest] Levantando stack Docker Compose...")
    result = _run_compose(["up", "-d", "--build"])
    if result.returncode != 0:
        print(f"[conftest] ERROR compose up:\n{result.stderr}")
        raise RuntimeError("Failed to start Docker Compose")

    # Esperar healthchecks
    services = [
        ("users", 8081, "/health"),
        ("ingest", 8082, "/health"),
        ("query", 8083, "/health"),
        ("moderation", 8084, "/health"),
        ("stats", 8085, "/health"),
    ]
    deadline = time.time() + 120
    for name, port, path in services:
        url = f"http://localhost:{port}{path}"
        healthy = False
        while time.time() < deadline:
            try:
                r = httpx.get(url, timeout=3)
                if r.status_code == 200:
                    healthy = True
                    break
            except Exception:
                pass
            time.sleep(2)
        if not healthy:
            _run_compose(["down", "-v"])
            raise RuntimeError(f"Timeout esperando healthcheck de {name} en {url}")

    print("[conftest] Stack listo.")

    yield

    print("\n[conftest] Derribando stack...")
    _run_compose(["down", "-v"])


@pytest.fixture(scope="session")
def users_url():
    return "http://localhost:8081"


@pytest.fixture(scope="session")
def ingest_url():
    return "http://localhost:8082"


@pytest.fixture(scope="session")
def query_url():
    return "http://localhost:8083"


@pytest.fixture(scope="session")
def moderation_url():
    return "http://localhost:8084"
