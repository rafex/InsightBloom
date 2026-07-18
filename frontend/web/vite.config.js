import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue({
      template: {
        preprocessorOptions: {
          pug: {}
        }
      }
    }),
    VitePWA({
      registerType: 'autoUpdate',
      injectRegister: 'auto',
      manifest: {
        name: 'InsightBloom',
        short_name: 'InsightBloom',
        description: 'Plataforma de conferencias: presentaciones, dudas, encuestas y chat en vivo.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        theme_color: '#4f46e5',
        background_color: '#f5f3ff',
        icons: [
          { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' }
        ]
      },
      workbox: {
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        // El chunk principal de monaco-editor (visor de archivos del moderador, carga lazy
        // via import() dinamico -- ver WorkspaceFileEditor.vue) pesa ~3.3MB, por encima del
        // limite por-archivo de precacheo de workbox (2MiB) -- precachearlo en el service
        // worker forzaria a bajarlo en la instalacion inicial de la app para TODO usuario,
        // exactamente lo que la carga lazy busca evitar (la mayoria nunca abre el editor). Se
        // excluye del precache; sigue funcionando igual via fetch normal (cache HTTP del
        // navegador) la primera vez que un moderador realmente lo abre.
        globIgnores: ['**/editor.main-*.js'],
        runtimeCaching: [
          {
            // Slides de una presentación + sus assets estáticos (css/imágenes del
            // tema) — permite repasar una charla ya vista sin conexión. No cubre
            // /presentation/pdf (descarga pesada, sin beneficio de cachear).
            urlPattern: /\/api\/presentations\/api\/v1\/conferences\/[^/]+\/presentation\/(?!pdf)/,
            handler: 'StaleWhileRevalidate',
            options: { cacheName: 'insightbloom-presentation-slides' }
          }
        ]
      }
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api/users': {
        target: 'http://localhost:8081',
        rewrite: (path) => path.replace(/^\/api\/users/, '')
      },
      '/api/ingest': {
        target: 'http://localhost:8082',
        rewrite: (path) => path.replace(/^\/api\/ingest/, '')
      },
      '/api/query': {
        target: 'http://localhost:8083',
        rewrite: (path) => path.replace(/^\/api\/query/, '')
      },
      '/api/moderation': {
        target: 'http://localhost:8084',
        rewrite: (path) => path.replace(/^\/api\/moderation/, '')
      },
      '/api/survey': {
        target: 'http://localhost:8086',
        rewrite: (path) => path.replace(/^\/api\/survey/, '')
      },
      '/api/presentations': {
        target: 'http://localhost:8091',
        rewrite: (path) => path.replace(/^\/api\/presentations/, '')
      }
    }
  }
})
