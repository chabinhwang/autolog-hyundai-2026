import { describe, expect, it } from 'vitest'
import { ApiError, apiRequest, buildUrl } from '../api/client'
import { getToken, setToken } from '../auth/tokenStorage'
import loginInvalid from './fixtures/auth-login-invalid-credentials.json'
import meSuccess from './fixtures/auth-me-success.json'
import meUnauthorized from './fixtures/auth-me-unauthorized.json'
import tripsValidation from './fixtures/trips-list-validation-failed.json'
import { mockFetch } from './mockFetch'

describe('API 클라이언트', () => {
  it('저장된 토큰을 Authorization: Bearer 헤더로 붙이고 data를 풀어 돌려준다', async () => {
    setToken('jwt-token')
    const mock = mockFetch({ 'GET /api/v1/auth/me': { body: meSuccess } })

    const data = await apiRequest('/auth/me')

    expect(data).toEqual(meSuccess.data)
    const [request] = mock.calls
    expect(request.url).toBe('/api/v1/auth/me')
    expect(request.headers.get('Authorization')).toBe('Bearer jwt-token')
    expect(request.headers.get('Accept')).toBe('application/json')
  })

  it('토큰이 없으면 Authorization 헤더를 붙이지 않는다', async () => {
    const mock = mockFetch({ 'GET /api/v1/auth/me': { body: meSuccess } })
    await apiRequest('/auth/me')
    expect(mock.calls[0].headers.has('Authorization')).toBe(false)
  })

  it('본문이 있으면 JSON으로 보내고 Content-Type을 붙인다', async () => {
    const mock = mockFetch({ 'POST /api/v1/drivers': { status: 201, body: { success: true, data: { id: 1 }, error: null } } })
    await apiRequest('/drivers', { method: 'POST', body: { dept: '영업1팀', name: '김영업' } })
    expect(mock.calls[0].headers.get('Content-Type')).toBe('application/json')
    expect(mock.calls[0].body).toEqual({ dept: '영업1팀', name: '김영업' })
  })

  it('실패 응답의 error.code·message를 담은 ApiError를 던진다', async () => {
    mockFetch({ 'GET /api/v1/trips': { status: 400, body: tripsValidation } })
    const error = await apiRequest('/trips').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'VALIDATION_FAILED', message: 'to: from 이후 366일 이내여야 합니다.', status: 400 })
  })

  it('401이면 토큰을 지우고 로그인 화면으로 보낸다 (돌아올 경로 포함)', async () => {
    setToken('expired-token')
    window.history.replaceState(null, '', '/trips?from=2026-11-01')
    mockFetch({ 'GET /api/v1/auth/me': { status: 401, body: meUnauthorized } })

    const error = await apiRequest('/auth/me').catch((e: unknown) => e)

    expect(error).toMatchObject({ code: 'UNAUTHORIZED', status: 401 })
    expect(getToken()).toBeNull()
    expect(window.location.pathname).toBe('/login')
    const params = new URLSearchParams(window.location.search)
    expect(params.get('reason')).toBe('expired')
    expect(params.get('next')).toBe('/trips?from=2026-11-01')
  })

  it('auth: false 요청(로그인)의 401은 토큰을 지우거나 이동하지 않는다', async () => {
    setToken('keep-me')
    window.history.replaceState(null, '', '/login')
    const mock = mockFetch({ 'POST /api/v1/auth/login': { status: 401, body: loginInvalid } })

    const error = await apiRequest('/auth/login', { method: 'POST', body: {}, auth: false }).catch((e: unknown) => e)

    expect(error).toMatchObject({ code: 'INVALID_CREDENTIALS', message: '아이디 또는 비밀번호가 올바르지 않습니다.' })
    expect(mock.calls[0].headers.has('Authorization')).toBe(false)
    expect(getToken()).toBe('keep-me')
  })

  it('JSON이 아닌 오류 응답은 HTTP 상태로 코드를 정한다', async () => {
    mockFetch({ 'GET /api/v1/auth/me': { status: 502, raw: '<html>Bad Gateway</html>', headers: { 'Content-Type': 'text/html' } } })
    const error = await apiRequest('/auth/me').catch((e: unknown) => e)
    expect(error).toMatchObject({ code: 'INTERNAL_ERROR', status: 502 })
  })

  it('네트워크 오류는 NETWORK_ERROR로 바꾼다', async () => {
    const mock = mockFetch({})
    mock.fn.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const error = await apiRequest('/auth/me').catch((e: unknown) => e)
    expect(error).toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
  })

  it('값이 없는 쿼리는 URL에서 뺀다', () => {
    expect(buildUrl('/trips', { from: '2026-11-01', to: '2026-11-30', vehicleId: undefined, purpose: '', page: 0 })).toBe(
      '/api/v1/trips?from=2026-11-01&to=2026-11-30&page=0',
    )
  })
})
