import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

/** 목록이 비었을 때 보여 주는 안내 */
export function EmptyState({ icon = 'inbox', children }: { icon?: IconName; children: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty__icon">
        <Icon name={icon} size={22} />
      </span>
      <p className="empty__text">{children}</p>
    </div>
  )
}
