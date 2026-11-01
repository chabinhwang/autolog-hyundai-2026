// fetch 목: "METHOD /경로" 키로 응답을 정합니다. 쿼리는 키에 넣지 않고 request.query로 확인합니다.
import { vi } from 'vitest'

export interface MockRequest {
  method: string
  url: string
  path: string
  query: URLSearchParams
  headers: Headers
  body: unknown
}

export interface MockResponse {
  status?: number
  /** JSON으로 직렬화할 본문 (계약 문서 예시 그대로) */
  body?: unknown
  /** 바이너리 등 직렬화하지 않을 본문 */
  raw?: BodyInit
  headers?: Record<string, string>
}

export type MockHandler = MockResponse | ((request: MockRequest) => MockResponse)
export type MockRoutes = Record<string, MockHandler>

export function mockFetch(routes: MockRoutes) {
  const calls: MockRequest[] = []
  const unmatched: string[] = []

  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const parsed = new URL(url, 'http://localhost')
    const method = (init?.method ?? 'GET').toUpperCase()
    const request: MockRequest = {
      method,
      url,
      path: parsed.pathname,
      query: parsed.searchParams,
      headers: new Headers(init?.headers),
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
    }
    calls.push(request)

    const key = `${method} ${parsed.pathname}`
    const handler = routes[key]
    if (!handler) {
      unmatched.push(key)
      return new Response(JSON.stringify({ success: false, data: null, error: { code: 'TEST_UNMATCHED', message: key } }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    const spec = typeof handler === 'function' ? handler(request) : handler
    const isRaw = spec.raw !== undefined
    return new Response(isRaw ? spec.raw : JSON.stringify(spec.body ?? null), {
      status: spec.status ?? 200,
      headers: isRaw ? spec.headers : { 'Content-Type': 'application/json', ...spec.headers },
    })
  })

  vi.stubGlobal('fetch', fn)

  return {
    fn,
    calls,
    unmatched,
    /** 특정 메서드·경로로 보낸 요청 */
    requests(method: string, path: string): MockRequest[] {
      return calls.filter((c) => c.method === method && c.path === path)
    },
  }
}
