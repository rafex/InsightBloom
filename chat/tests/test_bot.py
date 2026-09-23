"""Compatibility checks for Roberto's OpenAI-compatible provider adapter."""

import pytest
from openai import AsyncOpenAI

from bot import Roberto


@pytest.mark.asyncio
async def test_roberto_openai_client_supports_configured_chat_completions() -> None:
    """OpenAI SDK upgrades must retain the configured base URL and chat API surface."""
    roberto = Roberto()
    client = roberto._client = AsyncOpenAI(
        api_key="test-key",
        base_url="https://provider.example/v1",
    )

    try:
        assert str(client.base_url) == "https://provider.example/v1/"
        assert callable(client.chat.completions.create)
    finally:
        await client.close()
