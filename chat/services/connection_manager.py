from __future__ import annotations

import asyncio
import json
import logging
import os
import uuid

from fastapi import WebSocket

log = logging.getLogger("chat")

HOSTNAME = os.getenv("HOSTNAME", "local")
BROADCAST_SUBJECT = "chat.broadcast"
PRESENCE_SUBJECT = "chat.presence"


class ConnectionManager:
    """
    Cada pod reparte directo a sus sockets locales (sin cambios respecto a
    antes). Si hay un cliente NATS conectado (ver attach_nats), además publica
    los mismos eventos para que otros pods (si el servicio corre con 2+
    réplicas) también se enteren — si NATS no está disponible, todo sigue
    funcionando igual que con un solo pod.
    """

    def __init__(self):
        # (websocket, user_dict, conn_id) — conn_id identifica la conexión
        # de forma cruzable entre pods (a diferencia del objeto WebSocket).
        self._conns: list[tuple[WebSocket, dict, str]] = []
        # conference_id -> {conn_id: nickname}; fuente de verdad para count()/
        # online_nicknames(), actualizada localmente de forma optimista y
        # también desde eventos de NATS originados en otros pods.
        self._presence: dict[str | None, dict[str, str]] = {}
        self._nc = None

    def attach_nats(self, nc) -> None:
        self._nc = nc
        if nc is None:
            return
        asyncio.create_task(self._subscribe())

    async def _subscribe(self) -> None:
        try:
            await self._nc.subscribe(BROADCAST_SUBJECT, cb=self._on_remote_broadcast)
            await self._nc.subscribe(PRESENCE_SUBJECT, cb=self._on_remote_presence)
        except Exception:
            log.warning("nats_subscribe_failed", exc_info=True)

    async def connect(self, ws: WebSocket, user: dict) -> None:
        await ws.accept()
        conn_id = uuid.uuid4().hex
        conference_id = user.get("conference_id")
        nickname = user.get("nickname", "")
        self._conns.append((ws, user, conn_id))
        self._presence.setdefault(conference_id, {})[conn_id] = nickname
        await self._publish_presence(conference_id, conn_id, nickname, joined=True)

    def disconnect(self, ws: WebSocket) -> None:
        removed = [(u, cid) for w, u, cid in self._conns if w is ws]
        self._conns = [(w, u, cid) for w, u, cid in self._conns if w is not ws]
        for user, conn_id in removed:
            conference_id = user.get("conference_id")
            bucket = self._presence.get(conference_id)
            if bucket is not None:
                bucket.pop(conn_id, None)
            if self._nc is not None:
                # disconnect() es sync (se llama sin await en varios lugares);
                # el aviso a otros pods se dispara como tarea en background.
                asyncio.create_task(
                    self._publish_presence(conference_id, conn_id, user.get("nickname", ""), joined=False)
                )

    def _scoped(self, conference_id: str | None) -> list[tuple[WebSocket, dict, str]]:
        if conference_id is None:
            return self._conns
        return [(w, u, cid) for w, u, cid in self._conns if u.get("conference_id") == conference_id]

    async def broadcast(self, payload: dict, conference_id: str | None = None) -> None:
        """Si se pasa conference_id, solo llega a las conexiones de esa conferencia."""
        dead: list[WebSocket] = []
        for ws, _, _ in self._scoped(conference_id):
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

        if self._nc is not None:
            try:
                await self._nc.publish(BROADCAST_SUBJECT, json.dumps({
                    "payload": payload, "conference_id": conference_id, "origin": HOSTNAME,
                }).encode())
            except Exception:
                log.warning("nats_publish_failed", exc_info=True)

    async def _on_remote_broadcast(self, msg) -> None:
        try:
            data = json.loads(msg.data.decode())
        except Exception:
            return
        if data.get("origin") == HOSTNAME:
            return  # ya se entregó por el camino local
        payload = data.get("payload")
        conference_id = data.get("conference_id")
        dead: list[WebSocket] = []
        for ws, _, _ in self._scoped(conference_id):
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    async def _publish_presence(self, conference_id: str | None, conn_id: str, nickname: str, joined: bool) -> None:
        if self._nc is None:
            return
        try:
            await self._nc.publish(PRESENCE_SUBJECT, json.dumps({
                "conference_id": conference_id, "conn_id": conn_id,
                "nickname": nickname, "joined": joined,
            }).encode())
        except Exception:
            log.warning("nats_publish_failed", exc_info=True)

    async def _on_remote_presence(self, msg) -> None:
        try:
            data = json.loads(msg.data.decode())
        except Exception:
            return
        conference_id = data.get("conference_id")
        conn_id = data.get("conn_id")
        bucket = self._presence.setdefault(conference_id, {})
        if data.get("joined"):
            bucket[conn_id] = data.get("nickname", "")
        else:
            bucket.pop(conn_id, None)

    def _presence_buckets(self, conference_id: str | None) -> list[dict[str, str]]:
        if conference_id is None:
            return list(self._presence.values())
        return [self._presence.get(conference_id, {})]

    def online_nicknames(self, conference_id: str | None = None) -> list[str]:
        names: list[str] = []
        for bucket in self._presence_buckets(conference_id):
            names.extend(bucket.values())
        return names

    def count(self, conference_id: str | None = None) -> int:
        return sum(len(bucket) for bucket in self._presence_buckets(conference_id))
