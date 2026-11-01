import type { ReactNode } from 'react'
import { useAuth, useSession } from '../auth/context'
import { Link } from '../router/Link'
import { useLocation } from '../router/router'

const NAV_ITEMS = [
  { to: '/', label: '홈' },
  { to: '/trips', label: '운행 기록' },
  { to: '/vehicles', label: '차량' },
  { to: '/drivers', label: '직원' },
  { to: '/places', label: '장소' },
  { to: '/export', label: '기록부 내보내기' },
]

function isActive(to: string, pathname: string): boolean {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

export function Layout({ children }: { children: ReactNode }) {
  const session = useSession()
  const { logout } = useAuth()
  const { pathname } = useLocation()

  return (
    <div className="layout">
      <a className="skip-link" href="#main">
        본문으로 건너뛰기
      </a>
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo">AutoLog</span>
          <span className="topbar__org" data-testid="org-name">
            {session.organization.name}
          </span>
        </div>
        <div className="topbar__user">
          <span className="muted">{session.admin.displayName}</span>
          <button type="button" className="button button--small" onClick={logout}>
            로그아웃
          </button>
        </div>
      </header>
      <div className="layout__body">
        <nav className="sidenav" aria-label="주 메뉴">
          <ul>
            {NAV_ITEMS.map((item) => {
              const active = isActive(item.to, pathname)
              return (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    className={active ? 'sidenav__link sidenav__link--active' : 'sidenav__link'}
                    aria-current={active ? 'page' : undefined}
                  >
                    {item.label}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
        <main id="main" className="content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  )
}
