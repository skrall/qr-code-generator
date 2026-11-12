import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// During development the UI runs on its own dev server and proxies API calls to the
// Spring Boot app, so the browser still sees a single origin and CORS never comes up.
// In a built app both are served by the same process, so no proxy is involved.
const apiTarget = process.env.QR_CODE_API ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': apiTarget,
      '/v3': apiTarget,
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.spec.ts'],
  },
})
