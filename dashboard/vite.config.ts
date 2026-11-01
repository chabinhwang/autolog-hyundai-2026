/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const serverUrl = process.env.AUTOLOG_SERVER_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 중에는 같은 출처로 백엔드를 호출해 CORS 설정이 필요 없게 합니다.
    // VITE_API_BASE_URL을 비워 두면 API는 /api/v1 로 호출됩니다.
    proxy: {
      '/api': serverUrl,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // 테스트는 항상 개발 기본값(/api/v1)으로 실행
    env: { VITE_API_BASE_URL: '' },
    restoreMocks: true,
    unstubGlobals: true,
  },
})
