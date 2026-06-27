"""Tests unitarios para el parser de comandos y resolución de conferencias."""

from services.command_parser import parse_conferencia, parse_doubt_or_topic


class TestParseDoubtOrTopic:
    def test_dudas_valid(self):
        result = parse_doubt_or_topic("/dudas inteligencia ¿Cómo afecta la IA?")
        assert result == ("doubt", "inteligencia", "¿Cómo afecta la IA?")

    def test_temas_valid(self):
        result = parse_doubt_or_topic("/temas blockchain Aplicaciones descentralizadas")
        assert result == ("topic", "blockchain", "Aplicaciones descentralizadas")

    def test_hash_temas_no_longer_matches(self):
        # En Telegram el comando es /temas (con barra), no #temas (eso es solo para el chat web).
        assert parse_doubt_or_topic("#temas blockchain Aplicaciones") is None

    def test_missing_space(self):
        assert parse_doubt_or_topic("/dudas") is None

    def test_no_description(self):
        assert parse_doubt_or_topic("/dudas word") is None

    def test_description_too_long(self):
        assert parse_doubt_or_topic("/dudas test " + "x" * 301) is None

    def test_invalid_command(self):
        assert parse_doubt_or_topic("/otro word desc") is None


class TestParseConferencia:
    def test_valid_chat(self):
        assert parse_conferencia("/conferencia demo-2026 chat") == ("demo-2026", "chat")

    def test_valid_notificaciones(self):
        assert parse_conferencia("/conferencia demo-2026 notificaciones") == ("demo-2026", "notifications")

    def test_case_insensitive_purpose(self):
        assert parse_conferencia("/conferencia demo-2026 CHAT") == ("demo-2026", "chat")

    def test_invalid_purpose(self):
        assert parse_conferencia("/conferencia demo-2026 admin") is None

    def test_missing_purpose(self):
        assert parse_conferencia("/conferencia demo-2026") is None

    def test_not_a_conferencia_command(self):
        assert parse_conferencia("/dudas word desc") is None
