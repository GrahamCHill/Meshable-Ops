import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const crawlerHeaders = { 'X-Robots-Tag': 'noindex, nofollow, noarchive, nosnippet, noimageindex' }

export default defineConfig({
  plugins: [react()],
  server: {
    headers: crawlerHeaders,
    port: 5173,
    watch: {
      usePolling: process.env.CHOKIDAR_USEPOLLING === 'true',
      interval: 300,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: { headers: crawlerHeaders },
})
