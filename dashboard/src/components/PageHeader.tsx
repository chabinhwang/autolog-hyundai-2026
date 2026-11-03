import type { ReactNode } from 'react'

interface PageHeaderProps {
  id: string
  title: ReactNode
  description?: ReactNode
  /** 제목 위 작은 줄 (뒤로 가기 링크 등) */
  eyebrow?: ReactNode
  actions?: ReactNode
}

/** 모든 화면 상단의 제목·설명·주요 동작 영역 */
export function PageHeader({ id, title, description, eyebrow, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div className="page-header__text">
        {eyebrow && <div className="page-header__eyebrow">{eyebrow}</div>}
        <h1 id={id}>{title}</h1>
        {description && <p className="page-header__desc">{description}</p>}
      </div>
      {actions && <div className="page-header__actions">{actions}</div>}
    </div>
  )
}
