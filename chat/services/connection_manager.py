from __future__ import annotations

from fastapi import WebSocket


class ConnectionManager:
    def __init__(self):
        # (websocket, user_dict)  donde user_dict incluye conference_id
        self._conns: list[tuple[WebSocket, dict]] = []

    async def connect(self, ws: WebSocket, user: dict) -> None:
        await ws.accept()
        self._conns.append((ws, user))

    def disconnect(self, ws: WebSocket) -> None:
        self._conns = [(w, u) for w, u in self._conns if w is not ws]

    def _scoped(self, conference_id: str | None) -> list[tuple[WebSocket, dict]]:
        if conference_id is None:
            return self._conns
        return [(w, u) for w, u in self._conns if u.get("conference_id") == conference_id]

    async def broadcast(self, payload: dict, conference_id: str | None = None) -> None:
        """Si se pasa conference_id, solo llega a las conexiones de esa conferencia."""
        dead: list[WebSocket] = []
        for ws, _ in self._scoped(conference_id):
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    def online_nicknames(self, conference_id: str | None = None) -> list[str]:
        return [u["nickname"] for _, u in self._scoped(conference_id)]

    def count(self, conference_id: str | None = None) -> int:
        return len(self._scoped(conference_id))
