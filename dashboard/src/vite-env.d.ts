/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API Base URL (`/api/v1`까지 포함). 비우면 `/api/v1` (개발 시 Vite 프록시) */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
