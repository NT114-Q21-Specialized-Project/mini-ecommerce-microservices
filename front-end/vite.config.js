import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const devProxyTarget = process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:9000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    allowedHosts: [
      'mini-ecommerce.local'
    ],
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true,
      },
    },
    watch: {
      usePolling: true, // Giúp hot-reload ổn định trong Docker
    },
  },
})
