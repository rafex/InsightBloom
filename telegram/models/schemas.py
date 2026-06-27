from __future__ import annotations

from pydantic import BaseModel


class NotifyRequest(BaseModel):
    conferenceUuid: str
    message: str
