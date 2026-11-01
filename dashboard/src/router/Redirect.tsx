import { useEffect } from 'react'
import { navigate } from './router'

/** 렌더링되면 지정한 경로로 이동합니다 (기록을 남기지 않음) */
export function Redirect({ to }: { to: string }) {
  useEffect(() => {
    navigate(to, { replace: true })
  }, [to])
  return null
}
