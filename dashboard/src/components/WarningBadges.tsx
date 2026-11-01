import { WARNING_LABEL } from '../lib/labels'

export function WarningBadges({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return <span className="muted">없음</span>
  return (
    <ul className="badges" aria-label="경고">
      {warnings.map((code) => {
        const info = WARNING_LABEL[code]
        return (
          <li key={code} className={`badge badge--warn badge--${code.toLowerCase()}`} title={info?.description}>
            {info?.label ?? code}
          </li>
        )
      })}
    </ul>
  )
}
