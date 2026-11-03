import type { ReactNode } from 'react'
import { useAuth, useSession } from '../auth/context'
import { Link } from '../router/Link'
import { useLocation } from '../router/router'
import { Icon, LogoMark, type IconName } from './Icon'

/** short: 휴대폰 하단 탭에서 쓰는 짧은 이름 */
const NAV_ITEMS: { to: string; label: string; short: string; icon: IconName }[] = [
  { to: '/', label: '홈', short: '홈', icon: 'home' },
  { to: '/trips', label: '운행 기록', short: '운행', icon: 'route' },
  { to: '/vehicles', label: '차량', short: '차량', icon: 'car' },
  { to: '/drivers', label: '직원', short: '직원', icon: 'users' },
  { to: '/places', label: '장소', short: '장소', icon: 'pin' },
  { to: '/export', label: '기록부 내보내기', short: '내보내기', icon: 'download' },
]

function isActive(to: string, pathname: string): boolean {
  return to === '/' ? pathname === '/' : pathname === to || pathname.startsWith(`${to}/`)
}

export function Layout({ children }: { children: ReactNode }) {
  const session = useSession()
  const { logout } = useAuth()
  const { pathname } = useLocation()
  const current = NAV_ITEMS.find((item) => isActive(item.to, pathname))

  return (
    <div className="layout">
      <a className="skip-link" href="#main">
        본문으로 건너뛰기
      </a>
      <aside className="sidebar">
        <Link to="/" className="sidebar__brand" aria-label="AutoLog 홈">
          <LogoMark />
          <span className="sidebar__brand-text">
            <span className="sidebar__logo">AutoLog</span>
            <span className="sidebar__tagline">업무용승용차 운행기록</span>
          </span>
        </Link>
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
                    title={item.label}
                  >
                    <Icon name={item.icon} size={20} />
                    <span className="sidenav__label">{item.label}</span>
                    <span className="sidenav__short" aria-hidden="true">
                      {item.short}
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>
        <p className="sidebar__foot">
          <Icon name="shield" size={16} />
          <span>운행 기록은 차량이 자동으로 남기고, 개인 운행은 장소·시각을 가립니다.</span>
        </p>
      </aside>
      <div className="layout__main">
        <header className="topbar">
          <div className="topbar__context">
            <span className="topbar__logo">
              <LogoMark size={28} />
            </span>
            <span className="topbar__org" data-testid="org-name">
              {session.organization.name}
            </span>
            {current && (
              <>
                <Icon name="chevronRight" size={14} className="topbar__sep" />
                <span className="topbar__page">{current.label}</span>
              </>
            )}
          </div>
          <div className="topbar__user">
            <span className="avatar" aria-hidden="true">
              {session.admin.displayName.slice(0, 1)}
            </span>
            <span className="topbar__name">{session.admin.displayName}</span>
            <button type="button" className="button button--small button--ghost topbar__logout" onClick={logout}>
              <Icon name="logout" size={16} />
              <span className="topbar__logout-label">로그아웃</span>
            </button>
          </div>
        </header>
        <main id="main" className="content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  )
}
