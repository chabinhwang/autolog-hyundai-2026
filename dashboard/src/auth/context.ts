import { createContext, useContext } from 'react'
import type { Admin, LoginResponse, Organization } from '../api/types'

export interface Session {
  admin: Admin
  organization: Organization
  /** 토큰 만료 시각 (ISO-8601) */
  expiresAt: string | null
}

export type AuthState =
  | { status: 'checking' }
  | { status: 'anonymous' }
  | { status: 'error'; message: string }
  | { status: 'authenticated'; session: Session }

export interface AuthContextValue {
  state: AuthState
  /** 로그인 성공 응답을 받아 토큰을 저장하고 세션을 시작 */
  completeLogin: (response: LoginResponse) => void
  logout: () => void
  /** 앱 시작 확인(/auth/me)이 네트워크 오류로 실패했을 때 다시 시도 */
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서만 사용할 수 있습니다.')
  return value
}

/** 로그인한 상태에서만 쓰는 화면용 */
export function useSession(): Session {
  const { state } = useAuth()
  if (state.status !== 'authenticated') throw new Error('로그인이 필요합니다.')
  return state.session
}
