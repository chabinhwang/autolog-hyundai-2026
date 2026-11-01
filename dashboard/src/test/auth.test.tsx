import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import loginInvalid from './fixtures/auth-login-invalid-credentials.json'
import loginRateLimited from './fixtures/auth-login-rate-limited.json'
import loginSuccess from './fixtures/auth-login-success.json'
import meUnauthorized from './fixtures/auth-me-unauthorized.json'
import summary from './fixtures/dashboard-summary-success.json'
import { renderApp, TEST_TOKEN } from './renderApp'

function fillLogin(loginId: string, password: string) {
  fireEvent.change(screen.getByLabelText('아이디 (이메일)'), { target: { value: loginId } })
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: '로그인' }))
}

describe('로그인', () => {
  it('토큰이 없으면 로그인 화면을 보여 주고, 로그인하면 토큰을 저장하고 홈으로 간다', async () => {
    const { mock } = renderApp(
      '/',
      {
        'POST /api/v1/auth/login': { body: loginSuccess },
        'GET /api/v1/dashboard/summary': { body: summary },
      },
      { token: null },
    )

    expect(await screen.findByRole('heading', { name: 'AutoLog 관리자' })).toBeTruthy()
    expect(window.location.pathname).toBe('/login')

    fillLogin('admin@autolog-demo.kr', 'secret-password')

    expect(await screen.findByRole('heading', { name: '월간 업무사용비율' })).toBeTruthy()
    expect(window.location.pathname).toBe('/')
    expect(window.sessionStorage.getItem('autolog.accessToken')).toBe(loginSuccess.data.accessToken)
    expect(screen.getByTestId('org-name').textContent).toBe('오토로그 주식회사')

    const [loginRequest] = mock.requests('POST', '/api/v1/auth/login')
    expect(loginRequest.body).toEqual({ loginId: 'admin@autolog-demo.kr', password: 'secret-password' })
    expect(loginRequest.headers.has('Authorization')).toBe(false)
    // 로그인 후 요청에는 새 토큰이 붙음
    const [summaryRequest] = mock.requests('GET', '/api/v1/dashboard/summary')
    expect(summaryRequest.headers.get('Authorization')).toBe(`Bearer ${loginSuccess.data.accessToken}`)
  })

  it('INVALID_CREDENTIALS 메시지를 보여 주고 로그인 화면에 머문다', async () => {
    renderApp('/login', { 'POST /api/v1/auth/login': { status: 401, body: loginInvalid } }, { token: null })
    fillLogin('admin@autolog-demo.kr', 'wrong')
    expect((await screen.findByRole('alert')).textContent).toContain('아이디 또는 비밀번호가 올바르지 않습니다.')
    expect(window.location.pathname).toBe('/login')
    expect(window.sessionStorage.getItem('autolog.accessToken')).toBeNull()
  })

  it('RATE_LIMITED 메시지를 보여 준다', async () => {
    renderApp('/login', { 'POST /api/v1/auth/login': { status: 429, body: loginRateLimited } }, { token: null })
    fillLogin('admin@autolog-demo.kr', 'pw')
    expect((await screen.findByRole('alert')).textContent).toContain('요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.')
  })

  it('빈 입력은 서버에 보내지 않는다', async () => {
    const { mock } = renderApp('/login', {}, { token: null })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))
    expect((await screen.findByRole('alert')).textContent).toContain('아이디와 비밀번호를 입력해 주세요.')
    expect(mock.requests('POST', '/api/v1/auth/login')).toHaveLength(0)
  })

  it('로그인 전 열었던 화면(next)으로 돌아간다', async () => {
    renderApp(
      '/drivers',
      {
        'POST /api/v1/auth/login': { body: loginSuccess },
        'GET /api/v1/drivers': { body: { success: true, data: [], error: null } },
      },
      { token: null },
    )
    await screen.findByLabelText('아이디 (이메일)')
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/drivers')
    fillLogin('admin@autolog-demo.kr', 'pw')
    expect(await screen.findByRole('heading', { name: '직원' })).toBeTruthy()
    expect(window.location.pathname).toBe('/drivers')
  })
})

describe('앱 시작 시 토큰 확인', () => {
  it('저장된 토큰이 있으면 GET /auth/me로 확인하고 회사명을 표시한다', async () => {
    const { mock } = renderApp('/?month=2026-11', { 'GET /api/v1/dashboard/summary': { body: summary } })
    expect(await screen.findByTestId('org-name')).toBeTruthy()
    expect(screen.getByTestId('org-name').textContent).toBe('오토로그 주식회사')
    const [me] = mock.requests('GET', '/api/v1/auth/me')
    expect(me.headers.get('Authorization')).toBe(`Bearer ${TEST_TOKEN}`)
  })

  it('/auth/me가 401이면 토큰을 지우고 로그인 화면으로 보낸다', async () => {
    renderApp('/vehicles', { 'GET /api/v1/auth/me': { status: 401, body: meUnauthorized } })
    expect(await screen.findByLabelText('아이디 (이메일)')).toBeTruthy()
    expect(window.location.pathname).toBe('/login')
    expect(window.sessionStorage.getItem('autolog.accessToken')).toBeNull()
    expect(screen.getByRole('status').textContent).toContain('로그인이 만료되었습니다')
  })

  it('화면 사용 중 API가 401이면 로그인 화면으로 이동한다', async () => {
    renderApp('/?month=2026-11', { 'GET /api/v1/dashboard/summary': { status: 401, body: meUnauthorized } })
    expect(await screen.findByLabelText('아이디 (이메일)')).toBeTruthy()
    expect(window.sessionStorage.getItem('autolog.accessToken')).toBeNull()
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/?month=2026-11')
  })

  it('로그아웃하면 토큰을 지우고 로그인 화면으로 간다', async () => {
    renderApp('/?month=2026-11', { 'GET /api/v1/dashboard/summary': { body: summary } })
    const banner = await screen.findByRole('banner')
    fireEvent.click(within(banner).getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByLabelText('아이디 (이메일)')).toBeTruthy()
    expect(window.sessionStorage.getItem('autolog.accessToken')).toBeNull()
  })
})
