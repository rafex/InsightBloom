import { describe, it, expect } from 'vitest'

const ALLOWED_TAGS_FOR_STRIP = /<\/?(?:html|head|body|script|style|iframe|link|meta|title|base|form|input|button|select|option|textarea|object|embed|param|applet|frame|frameset|noscript)(?:\s[^>]*)?>/gi
const STRIP_ATTRS = /\s(?:on\w+|style|id|class)\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi

function sanitizeHtmlForPreview(html) {
  if (!html) return ''
  return html.replace(ALLOWED_TAGS_FOR_STRIP, '').replace(STRIP_ATTRS, '')
}

function renderPlainText(text) {
  if (!text) return ''
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;')
    .replace(/\n/g, '<br>')
}

describe('emailCompose sanitization', () => {
  describe('sanitizeHtmlForPreview', () => {
    it('removes script tags and their content', () => {
      const result = sanitizeHtmlForPreview('<p>Hi</p><script>alert(1)</script>')
      expect(result).not.toContain('<script>')
      expect(result).not.toContain('</script>')
      expect(result).toContain('<p>Hi</p>')
    })

    it('removes style tags', () => {
      const result = sanitizeHtmlForPreview('<style>body{}</style><p>X</p>')
      expect(result).not.toContain('<style>')
      expect(result).not.toContain('</style>')
    })

    it('removes html, head, body tags', () => {
      const result = sanitizeHtmlForPreview('<html><head></head><body><p>Safe</p></body></html>')
      expect(result).not.toContain('<html>')
      expect(result).not.toContain('<head>')
      expect(result).not.toContain('<body>')
      expect(result).toContain('<p>Safe</p>')
    })

    it('removes iframe tags', () => {
      const result = sanitizeHtmlForPreview('<p>Hi</p><iframe src="x"></iframe>')
      expect(result).not.toContain('<iframe')
      expect(result).not.toContain('</iframe>')
    })

    it('removes on* event handler attributes', () => {
      const result = sanitizeHtmlForPreview('<p onclick="alert(1)">Text</p>')
      expect(result).not.toContain('onclick')
    })

    it('removes style, class, id attributes', () => {
      const result = sanitizeHtmlForPreview('<p style="red" class="foo" id="x">Text</p>')
      expect(result).not.toContain('style="red"')
      expect(result).not.toContain('class="foo"')
      expect(result).not.toContain('id="x"')
    })

    it('preserves allowed semantic tags', () => {
      const result = sanitizeHtmlForPreview('<p><strong>B</strong> <em>I</em> <a href="/">link</a></p>')
      expect(result).toContain('<strong>B</strong>')
      expect(result).toContain('<em>I</em>')
      expect(result).toContain('<a href="/">link</a>')
    })

    it('returns empty for null input', () => {
      expect(sanitizeHtmlForPreview(null)).toBe('')
    })

    it('returns empty for empty input', () => {
      expect(sanitizeHtmlForPreview('')).toBe('')
    })

    it('removes form, input, button tags', () => {
      const result = sanitizeHtmlForPreview('<form><input type="text"><button>Go</button></form>')
      expect(result).not.toContain('<form>')
      expect(result).not.toContain('<input')
      expect(result).not.toContain('<button')
    })
  })

  describe('renderPlainText', () => {
    it('converts newlines to br', () => {
      const result = renderPlainText('Linea 1\nLinea 2')
      expect(result).toBe('Linea 1<br>Linea 2')
    })

    it('escapes HTML entities', () => {
      const result = renderPlainText('<b>bold</b>')
      expect(result).toBe('&lt;b&gt;bold&lt;/b&gt;')
    })

    it('escapes quotes', () => {
      const result = renderPlainText('He said "hello"')
      expect(result).toContain('&quot;hello&quot;')
    })

    it('returns empty for null input', () => {
      expect(renderPlainText(null)).toBe('')
    })

    it('returns empty for empty input', () => {
      expect(renderPlainText('')).toBe('')
    })
  })
})
