import { apiRequest } from './client'
import type { LoginRequest, LoginResponse, MeResponse } from './types'

export function login(body: LoginRequest): Promise<LoginResponse> {
  // 로그인 실패(401 INVALID_CREDENTIALS)는 세션 만료가 아니므로 auth: false
  return apiRequest<LoginResponse>('/auth/login', { method: 'POST', body, auth: false })
}

export function fetchMe(signal?: AbortSignal): Promise<MeResponse> {
  return apiRequest<MeResponse>('/auth/me', { signal })
}
