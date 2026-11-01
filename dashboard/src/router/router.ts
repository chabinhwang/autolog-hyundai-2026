// 간단한 History API 라우터. 화면 수가 적어 외부 라우터 없이 경로 매칭만 합니다.
import { useMemo, useSyncExternalStore } from 'react'

const listeners = new Set<() => void>()

function notify() {
  for (const listener of listeners) listener()
}

if (typeof window !== 'undefined') {
  window.addEventListener('popstate', notify)
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** 현재 경로 + 쿼리 (예: `/trips?from=2026-11-01`) */
export function currentLocation(): string {
  return window.location.pathname + window.location.search
}

export function navigate(to: string, options: { replace?: boolean } = {}): void {
  if (to === currentLocation()) return
  if (options.replace) {
    window.history.replaceState(null, '', to)
  } else {
    window.history.pushState(null, '', to)
  }
  notify()
}

export interface Location {
  pathname: string
  search: string
  searchParams: URLSearchParams
}

export function useLocation(): Location {
  const href = useSyncExternalStore(subscribe, currentLocation, currentLocation)
  return useMemo(() => {
    const url = new URL(href, 'http://localhost')
    return { pathname: url.pathname, search: url.search, searchParams: url.searchParams }
  }, [href])
}

/** `/trips/:clientUuid` 같은 패턴을 경로와 비교해 파라미터를 돌려줍니다. 맞지 않으면 null */
export function matchPath(pattern: string, pathname: string): Record<string, string> | null {
  const patternParts = pattern.split('/').filter(Boolean)
  const pathParts = pathname.replace(/\/+$/, '').split('/').filter(Boolean)
  if (patternParts.length !== pathParts.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < patternParts.length; i += 1) {
    const expected = patternParts[i]
    const actual = pathParts[i]
    if (expected.startsWith(':')) {
      try {
        params[expected.slice(1)] = decodeURIComponent(actual)
      } catch {
        return null
      }
    } else if (expected !== actual) {
      return null
    }
  }
  return params
}

/** 로그인 후 돌아갈 경로로 쓸 수 있는지 확인 (외부 주소로 이동 방지) */
export function isSafeInternalPath(path: string | null): path is string {
  return !!path && path.startsWith('/') && !path.startsWith('//') && !path.startsWith('/login')
}
