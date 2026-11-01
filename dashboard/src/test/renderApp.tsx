import { render } from '@testing-library/react'
import App from '../App'
import meSuccess from './fixtures/auth-me-success.json'
import { mockFetch, type MockRoutes } from './mockFetch'

export const TEST_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.test.signature'

/** 로그인된 상태(sessionStorage에 토큰)로 앱을 path에서 렌더링 */
export function renderApp(path: string, routes: MockRoutes, options: { token?: string | null } = {}) {
  const token = options.token === undefined ? TEST_TOKEN : options.token
  if (token) window.sessionStorage.setItem('autolog.accessToken', token)
  window.history.replaceState(null, '', path)
  const mock = mockFetch({ 'GET /api/v1/auth/me': { body: meSuccess }, ...routes })
  const view = render(<App />)
  return { ...view, mock }
}
