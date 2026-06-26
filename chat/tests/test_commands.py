"""Tests unitarios para el parser de comandos y resolución de conferencias."""

import re

# Same regex pattern as in services/command_parser.py
# "/dudas" usa prefijo "/"; "#temas" usa "#".
_CMD_RE = re.compile(r"^(/dudas|#temas)\s+(\S+)\s+(.{1,300})$", re.DOTALL)
_UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.IGNORECASE,
)
_SHORT_RE = re.compile(r"^[0-9a-f]{7}$", re.IGNORECASE)


class TestCommandRegex:
    def test_dudas_valid(self):
        m = _CMD_RE.match("/dudas inteligencia ¿Cómo afecta la IA?")
        assert m is not None
        assert m.group(1) == "/dudas"
        assert m.group(2) == "inteligencia"
        assert m.group(3) == "¿Cómo afecta la IA?"

    def test_temas_valid(self):
        m = _CMD_RE.match("#temas blockchain Aplicaciones descentralizadas")
        assert m is not None
        assert m.group(1) == "#temas"

    def test_old_slash_temas_no_longer_matches(self):
        # /temas ya no es un comando válido; el trigger correcto es #temas.
        assert _CMD_RE.match("/temas blockchain Aplicaciones descentralizadas") is None

    def test_missing_space(self):
        assert _CMD_RE.match("/dudas") is None

    def test_no_description(self):
        assert _CMD_RE.match("/dudas word") is None

    def test_multiline_description(self):
        m = _CMD_RE.match("/dudas test linea1\nlinea2\nlinea3")
        assert m is not None

    def test_description_too_long(self):
        assert _CMD_RE.match("/dudas test " + "x" * 301) is None

    def test_invalid_command(self):
        assert _CMD_RE.match("/otro word desc") is None


class TestUuidRegex:
    def test_valid_uuid(self):
        assert _UUID_RE.match("550e8400-e29b-41d4-a716-446655440000") is not None

    def test_invalid_uuid(self):
        assert _UUID_RE.match("not-a-uuid") is None
        assert _UUID_RE.match("abc1234") is None


class TestShortCodeRegex:
    def test_valid_short(self):
        assert _SHORT_RE.match("abc1234") is not None

    def test_invalid_short(self):
        assert _SHORT_RE.match("abc123") is None
        assert _SHORT_RE.match("abc12345") is None
