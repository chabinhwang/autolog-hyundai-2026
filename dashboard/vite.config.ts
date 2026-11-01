import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const serverUrl = process.env.AUTOLOG_SERVER_URL ?? 'http://localhost:8080'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 중에는 같은 출처로 백엔드를 호출해 CORS 설정이 필요 없게 합니다.
    proxy: {
      '/api': serverUrl,
      '/actuator': serverUrl,
    },
  },
})
