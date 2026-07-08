import { defineConfig } from 'eslint/config'
import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'

export default defineConfig(
  {
    ignores: ['dist/**', 'dev-dist/**', 'node_modules/**']
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser
      }
    }
  },
  {
    rules: {
      // Options-API components declare props via runtime objects; TS infers most
      // of what @typescript-eslint would otherwise flag as explicit-any, and the
      // codebase leans on `any` deliberately at API boundaries (axios responses).
      '@typescript-eslint/no-explicit-any': 'off',
      // Best-effort catch blocks (`catch (e: any) {}`) are an intentional pattern
      // throughout this codebase for degraded-mode fallbacks.
      'no-empty': ['error', { allowEmptyCatch: true }],
      '@typescript-eslint/no-unused-vars': ['warn', {
        argsIgnorePattern: '^_', varsIgnorePattern: '^_',
        // `catch (e: any) { ... }` without using `e` is a deliberate best-effort/
        // degraded-mode fallback pattern used throughout this codebase.
        caughtErrors: 'none'
      }],
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'off',
      // conferenceId/friendlyId-style props always come from the router and are
      // never meaningfully optional at runtime; a default would just mask bugs.
      'vue/require-default-prop': 'off'
    }
  },
  {
    files: ['**/*.test.js', '**/__tests__/**'],
    languageOptions: {
      globals: {
        describe: 'readonly',
        it: 'readonly',
        expect: 'readonly',
        vi: 'readonly',
        beforeEach: 'readonly',
        afterEach: 'readonly'
      }
    }
  },
  {
    languageOptions: {
      globals: {
        window: 'readonly',
        document: 'readonly',
        localStorage: 'readonly',
        console: 'readonly',
        fetch: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        FormData: 'readonly',
        FileReader: 'readonly',
        Blob: 'readonly',
        WebSocket: 'readonly',
        EventSource: 'readonly',
        MouseEvent: 'readonly',
        TouchEvent: 'readonly',
        MessageEvent: 'readonly',
        KeyboardEvent: 'readonly',
        HTMLElement: 'readonly',
        HTMLInputElement: 'readonly',
        HTMLIFrameElement: 'readonly',
        HTMLDivElement: 'readonly',
        HTMLCanvasElement: 'readonly',
        SVGTextElement: 'readonly',
        DOMRect: 'readonly',
        Event: 'readonly'
      }
    }
  }
)
