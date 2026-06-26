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

    async def broadcast(self, payload: dict) -> None:
        dead: list[WebSocket] = []
        for ws, _ in self._conns:
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    def online_nicknames(self) -> list[str]:
        return [u["nickname"] for _, u in self._conns]

    def count(self) -> int:
        return len(self._conns)
