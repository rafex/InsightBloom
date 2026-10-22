"""Event isolation checks for Roberto's chat context."""

from bot import Roberto


def test_roberto_keeps_message_history_isolated_by_event() -> None:
    roberto = Roberto()
    roberto._remember("user", "mensaje de evento A", "event-a")
    roberto._remember("user", "mensaje de evento B", "event-b")
    assert roberto._history["event-a"] == [{"role": "user", "content": "mensaje de evento A"}]
    assert roberto._history["event-b"] == [{"role": "user", "content": "mensaje de evento B"}]
