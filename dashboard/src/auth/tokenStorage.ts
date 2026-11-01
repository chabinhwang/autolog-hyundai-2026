// 관리자 JWT 보관 (D1: sessionStorage, 탭을 닫으면 로그아웃)
const TOKEN_KEY = 'autolog.accessToken'

const listeners = new Set<() => void>()

function emit() {
  for (const listener of listeners) listener()
}

export function getToken(): string | null {
  try {
    return window.sessionStorage.getItem(TOKEN_KEY)
  } catch {
    // 저장소 접근이 막힌 환경(사생활 보호 모드 등)
    return null
  }
}

export function setToken(token: string): void {
  try {
    window.sessionStorage.setItem(TOKEN_KEY, token)
  } finally {
    emit()
  }
}

export function clearToken(): void {
  try {
    window.sessionStorage.removeItem(TOKEN_KEY)
  } finally {
    emit()
  }
}

/** 토큰이 저장·삭제될 때 알림을 받습니다. 반환값을 호출하면 구독 해제 */
export function subscribeToken(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
