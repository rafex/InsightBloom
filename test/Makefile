# InsightBloom Integration Tests
# Requiere: Docker Compose, Python 3.12+, venv

.PHONY: install test clean

VENV := .venv
PYTHON := $(VENV)/bin/python

install:
	python3 -m venv $(VENV)
	$(VENV)/bin/pip install -r requirements.txt

test:
	DOCKER_BUILDKIT=1 $(PYTHON) -m pytest -v --tb=short .

test-fast:
	$(PYTHON) -m pytest -v --tb=short -k "not compose_up" .

clean:
	rm -rf $(VENV) __pycache__ .pytest_cache
	docker compose -f ../container/compose.yml -p insightbloom-test down -v 2>/dev/null || true
