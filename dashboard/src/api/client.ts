// 공통 fetch 클라이언트: Base URL, Bearer 헤더, 응답 { success, data, error } 언래핑, 401 처리
import { clearToken, getToken } from '../auth/tokenStorage'
import { API_BASE_URL } from '../config'
import { ERROR_MESSAGES } from '../lib/errors'
import { parseContentDispositionFilename } from '../lib/contentDisposition'
import { currentLocation, navigate } from '../router/router'

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code)
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}

export type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, QueryValue>
  body?: unknown
  signal?: AbortSignal
  /** false면 Authorization 헤더를 붙이지 않고 401에도 로그인 화면으로 보내지 않음 (로그인 요청용) */
  auth?: boolean
  accept?: string
}

interface Envelope {
  success?: boolean
  data?: unknown
  error?: { code?: string; message?: string } | null
}

/** 값이 없는 쿼리(undefined·null·빈 문자열)는 빼고 URL을 만듭니다. */
export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === '') continue
    params.append(key, String(value))
  }
  const queryString = params.toString()
  return `${API_BASE_URL}${path}${queryString ? `?${queryString}` : ''}`
}

/** 401: 토큰을 지우고 로그인 화면으로 이동 (돌아올 경로를 next로 전달) */
function handleUnauthorized() {
  clearToken()
  const here = currentLocation()
  if (window.location.pathname === '/login') return
  navigate(`/login?reason=expired&next=${encodeURIComponent(here)}`, { replace: true })
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const useAuth = options.auth !== false
  const headers: Record<string, string> = { Accept: options.accept ?? 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (useAuth) {
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
  }

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new ApiError('NETWORK_ERROR', ERROR_MESSAGES.NETWORK_ERROR, 0)
  }

  if (response.status === 401 && useAuth) handleUnauthorized()
  return response
}

async function readEnvelope(response: Response): Promise<Envelope | null> {
  try {
    const text = await response.text()
    if (!text) return null
    const parsed: unknown = JSON.parse(text)
    return parsed && typeof parsed === 'object' ? (parsed as Envelope) : null
  } catch {
    return null
  }
}

function fallbackCode(status: number): string {
  if (status === 401) return 'UNAUTHORIZED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  if (status === 429) return 'RATE_LIMITED'
  if (status >= 500) return 'INTERNAL_ERROR'
  return 'UNKNOWN_ERROR'
}

function toApiError(response: Response, envelope: Envelope | null): ApiError {
  const code = envelope?.error?.code || fallbackCode(response.status)
  const message =
    envelope?.error?.message || ERROR_MESSAGES[code] || `${ERROR_MESSAGES.UNKNOWN_ERROR} (HTTP ${response.status})`
  return new ApiError(code, message, response.status)
}

/** JSON API 호출. 성공하면 data를 돌려주고, 실패하면 ApiError를 던집니다. */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await send(path, options)
  const envelope = await readEnvelope(response)
  if (!response.ok || !envelope || envelope.success !== true) {
    throw toApiError(response, envelope)
  }
  return envelope.data as T
}

export interface DownloadedFile {
  blob: Blob
  /** Content-Disposition의 filename*에서 읽은 파일명. 없으면 null */
  filename: string | null
}

/** 파일 다운로드 (성공은 파일 본문, 실패는 JSON 오류) */
export async function apiDownload(path: string, options: RequestOptions = {}): Promise<DownloadedFile> {
  const response = await send(path, { ...options, accept: options.accept ?? '*/*' })
  if (!response.ok) {
    throw toApiError(response, await readEnvelope(response))
  }
  const blob = await response.blob()
  return { blob, filename: parseContentDispositionFilename(response.headers.get('Content-Disposition')) }
}
