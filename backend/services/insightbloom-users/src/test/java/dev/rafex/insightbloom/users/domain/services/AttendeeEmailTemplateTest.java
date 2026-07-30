package dev.rafex.insightbloom.users.domain.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttendeeEmailTemplateTest {

    @Test
    void textFormatEscapesHtml() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto", "<script>alert(1)</script>", "text");
        assertTrue(result.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(result.contains("<script>"));
    }

    @Test
    void textFormatConvertsNewlinesToBr() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto", "Linea 1\nLinea 2", "text");
        assertTrue(result.contains("Linea 1<br>Linea 2"));
    }

    @Test
    void htmlFormatPreservesSemanticTags() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto",
                "<p><strong>Bold</strong> text</p>", "html");
        assertTrue(result.contains("<strong>Bold</strong>"));
        assertTrue(result.contains("<p>"));
    }

    @Test
    void htmlFormatStripsForbiddenTags() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto",
                "<html><head></head><body><script>alert(1)</script><p>Safe</p></body></html>", "html");
        assertTrue(result.contains("Safe"));
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("<html>"));
        assertFalse(result.contains("<head>"));
        assertFalse(result.contains("<body>"));
    }

    @Test
    void htmlFormatStripsStyleAndEventHandlerAttributes() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto",
                "<p style=\"color:red\" onclick=\"alert(1)\" class=\"foo\" id=\"x\">Text</p>", "html");
        assertTrue(result.contains("<p>Text</p>") || result.contains("<p >Text</p>") || result.contains("<p  >Text</p>"));
        assertFalse(result.contains("style=\"color:red\""));
        assertFalse(result.contains("onclick"));
        assertFalse(result.contains("class=\"foo\""));
        assertFalse(result.contains("id=\"x\""));
    }

    @Test
    void htmlFormatAllowsImgAndLinks() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto",
                "<p>Ver <a href=\"https://x.com\">link</a></p>", "html");
        assertTrue(result.contains("<a href=\"https://x.com\">link</a>"));
    }

    @Test
    void markdownFormatTreatedLikeHtml() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto",
                "<p>Hello</p><script>x</script>", "markdown");
        assertTrue(result.contains("<p>Hello</p>"));
        assertFalse(result.contains("<script>"));
    }

    @Test
    void nullMessageReturnsEmpty() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto", null, "text");
        assertNotNull(result);
        assertTrue(result.contains("InsightBloom") && result.contains("Evento"));
    }

    @Test
    void emptyMessageReturnsTemplate() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto", "", "text");
        assertNotNull(result);
        assertTrue(result.contains("InsightBloom"));
    }

    @Test
    void backwardCompatMethodUsesTextFormat() {
        final String result = AttendeeEmailTemplate.render("Evento", "Asunto", "Hello <b>world</b>");
        assertTrue(result.contains("&lt;b&gt;world&lt;/b&gt;"));
        assertFalse(result.contains("<b>world</b>"));
    }

    @Test
    void sanitizeHtmlNullReturnsEmpty() {
        assertEquals("", AttendeeEmailTemplate.sanitizeHtml(null));
    }

    @Test
    void sanitizeHtmlRemovesScriptTags() {
        final String result = AttendeeEmailTemplate.sanitizeHtml("<p>Hi</p><script>alert(1)</script>");
        assertFalse(result.contains("<script>"));
        assertFalse(result.contains("</script>"));
    }

    @Test
    void sanitizeHtmlRemovesStyleTags() {
        final String result = AttendeeEmailTemplate.sanitizeHtml("<style>body{}</style><p>X</p>");
        assertFalse(result.contains("<style>"));
        assertFalse(result.contains("</style>"));
    }
}
