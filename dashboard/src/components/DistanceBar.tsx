import { formatKm } from '../lib/format'

interface DistanceParts {
  totalKm: number
  commuteKm: number
  businessKm: number
  personalKm: number
  unconfirmedKm: number
}

const SEGMENTS = [
  { key: 'commuteKm', label: '출퇴근', className: 'commute' },
  { key: 'businessKm', label: '업무', className: 'business' },
  { key: 'unconfirmedKm', label: '미확인', className: 'unconfirmed' },
  { key: 'personalKm', label: '개인', className: 'personal' },
] as const

/** 주행거리 구성(출퇴근·업무·미확인·개인)을 가로 막대로 표시. 숫자는 표에 있으므로 장식용 */
export function DistanceBar({ parts, showLegend = false }: { parts: DistanceParts; showLegend?: boolean }) {
  const total = parts.totalKm
  return (
    <div className="distance">
      <div className="distance__bar" aria-hidden="true">
        {total > 0 &&
          SEGMENTS.map((segment) => {
            const value = parts[segment.key]
            if (value <= 0) return null
            return (
              <span
                key={segment.key}
                className={`distance__seg distance__seg--${segment.className}`}
                style={{ width: `${(value / total) * 100}%` }}
                title={`${segment.label} ${formatKm(value)} km`}
              />
            )
          })}
      </div>
      {showLegend && (
        <ul className="distance__legend">
          {SEGMENTS.map((segment) => (
            <li key={segment.key}>
              <span className="distance__name">
                <span className={`dot dot--${segment.className}`} aria-hidden="true" />
                {segment.label}
              </span>
              <span className="distance__value">{formatKm(parts[segment.key])} km</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
