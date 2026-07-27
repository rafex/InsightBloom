from pydantic import BaseModel, Field


class RegisterRequest(BaseModel):
    phone: str = Field(min_length=1)
    nickname: str = Field(min_length=2, max_length=20)
    password: str = Field(min_length=1)


class LoginRequest(BaseModel):
    phone: str = Field(min_length=1)
    password: str = Field(min_length=1)


class SsoRequest(BaseModel):
    code: str = Field(min_length=1)


class JoinRequest(BaseModel):
    token: str = Field(min_length=1)
    conference_id: str = Field(min_length=1)


class WebhookPayload(BaseModel):
    word: str = ""
    kind: str = "doubt"
    author: str = "alguien"
