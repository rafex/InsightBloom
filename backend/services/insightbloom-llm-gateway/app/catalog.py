"""Closed provider/model catalog. User input can never select a URL."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Model:
    provider: str
    model: str
    endpoint: str


CATALOG: dict[tuple[str, str], Model] = {
    ("openai", "gpt-4o-mini"): Model("openai", "gpt-4o-mini", "https://api.openai.com/v1/chat/completions"),
    ("groq", "llama-3.3-70b-versatile"): Model("groq", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1/chat/completions"),
    ("groq", "llama-3.1-8b-instant"): Model("groq", "llama-3.1-8b-instant", "https://api.groq.com/openai/v1/chat/completions"),
    ("groq", "openai/gpt-oss-120b"): Model("groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1/chat/completions"),
}


def get_model(provider: str, model: str) -> Model:
    try:
        return CATALOG[(provider, model)]
    except KeyError as exc:
        raise ValueError("provider_model_not_allowed") from exc
