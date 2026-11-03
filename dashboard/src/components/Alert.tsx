import type { ReactNode } from 'react'
import { Icon, type IconName } from './Icon'

type AlertKind = 'error' | 'success' | 'info' | 'warning'

interface AlertProps {
  kind?: AlertKind
  children: ReactNode
}

const ICONS: Record<AlertKind, IconName> = {
  error: 'error',
  success: 'success',
  info: 'info',
  warning: 'alert',
}

/** 오류는 role="alert"(즉시 읽어 줌), 나머지는 role="status" */
export function Alert({ kind = 'info', children }: AlertProps) {
  return (
    <div className={`alert alert--${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon name={ICONS[kind]} size={18} className="alert__icon" />
      <div className="alert__body">{children}</div>
    </div>
  )
}
