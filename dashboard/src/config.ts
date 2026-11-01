// 빌드 시점 환경변수에서 API Base URL을 읽습니다. 끝의 `/`는 제거합니다.
const rawBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const API_BASE_URL = (rawBaseUrl ? rawBaseUrl : '/api/v1').replace(/\/+$/, '')
