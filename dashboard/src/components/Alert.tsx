import type { ReactNode } from 'react'

interface AlertProps {
  kind?: 'error' | 'success' | 'info' | 'warning'
  children: ReactNode
}

/** 오류는 role="alert"(즉시 읽어 줌), 나머지는 role="status" */
export function Alert({ kind = 'info', children }: AlertProps) {
  return (
    <div className={`alert alert--${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}
