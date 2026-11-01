import type { ReactNode } from 'react'
import { AuthProvider } from './auth/AuthProvider'
import { useAuth } from './auth/context'
import { Alert } from './components/Alert'
import { Layout } from './components/Layout'
import { DriversPage } from './pages/DriversPage'
import { ExportPage } from './pages/ExportPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PlacesPage } from './pages/PlacesPage'
import { TripDetailPage } from './pages/TripDetailPage'
import { TripListPage } from './pages/TripListPage'
import { VehiclesPage } from './pages/VehiclesPage'
import { Redirect } from './router/Redirect'
import { isSafeInternalPath, matchPath, useLocation } from './router/router'

interface Route {
  path: string
  render: (params: Record<string, string>) => ReactNode
}

const ROUTES: Route[] = [
  { path: '/', render: () => <HomePage /> },
  { path: '/trips', render: () => <TripListPage /> },
  { path: '/trips/:clientUuid', render: (p) => <TripDetailPage key={p.clientUuid} clientUuid={p.clientUuid} /> },
  { path: '/vehicles', render: () => <VehiclesPage /> },
  { path: '/drivers', render: () => <DriversPage /> },
  { path: '/places', render: () => <PlacesPage /> },
  { path: '/export', render: () => <ExportPage /> },
]

function renderRoute(pathname: string): ReactNode {
  for (const route of ROUTES) {
    const params = matchPath(route.path, pathname)
    if (params) return route.render(params)
  }
  return <NotFoundPage />
}

function AppRoutes() {
  const { state, retry } = useAuth()
  const { pathname, search, searchParams } = useLocation()

  if (state.status === 'checking') {
    return (
      <p className="splash" role="status">
        로그인 상태 확인 중…
      </p>
    )
  }

  if (state.status === 'error') {
    return (
      <main className="login">
        <div className="login__card">
          <Alert kind="error">{state.message}</Alert>
          <button type="button" className="button button--primary" onClick={retry}>
            다시 시도
          </button>
        </div>
      </main>
    )
  }

  if (state.status === 'anonymous') {
    if (pathname === '/login') return <LoginPage />
    const next = pathname === '/' && !search ? '' : `?next=${encodeURIComponent(pathname + search)}`
    return <Redirect to={`/login${next}`} />
  }

  if (pathname === '/login') {
    // 로그인 직후: next가 있으면 그 화면으로
    const next = searchParams.get('next')
    return <Redirect to={isSafeInternalPath(next) ? next : '/'} />
  }
  return <Layout>{renderRoute(pathname)}</Layout>
}

export default function App() {
  return (
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}
