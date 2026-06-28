"""Tests unitarios para el ConnectionManager."""

import pytest

from services.connection_manager import ConnectionManager


class MockWebSocket:
    def __init__(self):
        self.accepted = False
        self.sent = []
        self.closed_code = None

    async def accept(self):
        self.accepted = True

    async def send_json(self, payload):
        self.sent.append(payload)

    async def close(self, code=None, reason=None):
        self.closed_code = code


@pytest.fixture
def manager():
    return ConnectionManager()


@pytest.fixture
def ws():
    return MockWebSocket()


@pytest.mark.asyncio
async def test_connect_and_count(manager, ws):
    user = {"nickname": "alice", "conference_id": "c1"}
    await manager.connect(ws, user)
    assert ws.accepted
    assert manager.count() == 1
    assert manager.online_nicknames() == ["alice"]


@pytest.mark.asyncio
async def test_broadcast(manager, ws):
    ws2 = MockWebSocket()
    await manager.connect(ws, {"nickname": "alice"})
    await manager.connect(ws2, {"nickname": "bob"})

    payload = {"type": "message", "text": "hola"}
    await manager.broadcast(payload)

    assert payload in ws.sent
    assert payload in ws2.sent


@pytest.mark.asyncio
async def test_disconnect(manager, ws):
    await manager.connect(ws, {"nickname": "alice"})
    assert manager.count() == 1
    manager.disconnect(ws)
    assert manager.count() == 0


@pytest.mark.asyncio
async def test_broadcast_removes_dead(manager, ws):
    dead = MockWebSocket()

    async def failing_send_json(_):
        raise RuntimeError("broken")

    dead.send_json = failing_send_json
    await manager.connect(dead, {"nickname": "zombie"})
    await manager.connect(ws, {"nickname": "alice"})

    await manager.broadcast({"type": "ping"})

    assert manager.count() == 1
    assert manager.online_nicknames() == ["alice"]


@pytest.mark.asyncio
async def test_online_nicknames(manager):
    ws1 = MockWebSocket()
    ws2 = MockWebSocket()
    await manager.connect(ws1, {"nickname": "alice"})
    await manager.connect(ws2, {"nickname": "bob"})
    assert manager.online_nicknames() == ["alice", "bob"]


class TestConferenceIsolation:
    @pytest.mark.asyncio
    async def test_broadcast_only_reaches_same_conference(self, manager):
        ws_a = MockWebSocket()
        ws_b = MockWebSocket()
        await manager.connect(ws_a, {"nickname": "alice", "conference_id": "conf-a"})
        await manager.connect(ws_b, {"nickname": "bob", "conference_id": "conf-b"})

        payload = {"type": "message", "text": "solo para conf-a"}
        await manager.broadcast(payload, "conf-a")

        assert payload in ws_a.sent
        assert payload not in ws_b.sent

    @pytest.mark.asyncio
    async def test_count_scoped_by_conference(self, manager):
        await manager.connect(MockWebSocket(), {"nickname": "alice", "conference_id": "conf-a"})
        await manager.connect(MockWebSocket(), {"nickname": "bob", "conference_id": "conf-a"})
        await manager.connect(MockWebSocket(), {"nickname": "carol", "conference_id": "conf-b"})

        assert manager.count("conf-a") == 2
        assert manager.count("conf-b") == 1
        assert manager.count() == 3  # sin filtro: comportamiento global preservado

    @pytest.mark.asyncio
    async def test_online_nicknames_scoped_by_conference(self, manager):
        await manager.connect(MockWebSocket(), {"nickname": "alice", "conference_id": "conf-a"})
        await manager.connect(MockWebSocket(), {"nickname": "bob", "conference_id": "conf-b"})

        assert manager.online_nicknames("conf-a") == ["alice"]
        assert manager.online_nicknames("conf-b") == ["bob"]

    @pytest.mark.asyncio
    async def test_broadcast_without_conference_id_stays_global(self, manager):
        ws_a = MockWebSocket()
        ws_b = MockWebSocket()
        await manager.connect(ws_a, {"nickname": "alice", "conference_id": "conf-a"})
        await manager.connect(ws_b, {"nickname": "bob", "conference_id": "conf-b"})

        payload = {"type": "system", "text": "broadcast global"}
        await manager.broadcast(payload)

        assert payload in ws_a.sent
        assert payload in ws_b.sent
