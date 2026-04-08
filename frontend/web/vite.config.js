import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue({
      template: {
        preprocessorOptions: {
          pug: {}
        }
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
      }
    }
  }
})
