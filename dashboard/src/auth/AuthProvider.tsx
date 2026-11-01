import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe } from '../api/auth'
import { isAbortError, isApiError } from '../api/client'
import type { LoginResponse } from '../api/types'
import { errorMessage } from '../lib/errors'
import { navigate } from '../router/router'
import { AuthContext, type AuthContextValue, type AuthState } from './context'
import { clearToken, getToken, setToken, subscribeToken } from './tokenStorage'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => (getToken() ? { status: 'checking' } : { status: 'anonymous' }))
  const [attempt, setAttempt] = useState(0)

  // 앱 시작 시 저장된 토큰이 있으면 /auth/me로 확인
  useEffect(() => {
    if (!getToken()) return
    const controller = new AbortController()
    fetchMe(controller.signal).then(
      (me) => {
        setState({
          status: 'authenticated',
          session: { admin: me.admin, organization: me.organization, expiresAt: me.expiresAt },
        })
      },
      (error: unknown) => {
        if (controller.signal.aborted || isAbortError(error)) return
        if (isApiError(error) && error.status === 401) {
          // 클라이언트가 이미 토큰을 지우고 로그인 화면으로 보냈음
          setState({ status: 'anonymous' })
        } else {
          setState({ status: 'error', message: errorMessage(error) })
        }
      },
    )
    return () => controller.abort()
  }, [attempt])

  // 토큰이 지워지면(401·로그아웃) 세션 종료
  useEffect(
    () =>
      subscribeToken(() => {
        if (!getToken()) setState({ status: 'anonymous' })
      }),
    [],
  )

  const completeLogin = useCallback((response: LoginResponse) => {
    setToken(response.accessToken)
    setState({
      status: 'authenticated',
      session: {
        admin: response.admin,
        organization: response.organization,
        expiresAt: new Date(Date.now() + response.expiresIn * 1000).toISOString(),
      },
    })
  }, [])

  const logout = useCallback(() => {
    clearToken()
    navigate('/login', { replace: true })
  }, [])

  const retry = useCallback(() => {
    setState({ status: 'checking' })
    setAttempt((n) => n + 1)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ state, completeLogin, logout, retry }),
    [state, completeLogin, logout, retry],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
