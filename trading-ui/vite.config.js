import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Proxy all /api calls to Spring Boot
      // This avoids CORS issues in development
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // Proxy WebSocket connection
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    }
  }
})