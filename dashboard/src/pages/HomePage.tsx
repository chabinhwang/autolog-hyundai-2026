import { fetchSummary } from '../api/dashboard'
import type { DashboardSummary } from '../api/types'
import { DistanceBar } from '../components/DistanceBar'
import { Icon } from '../components/Icon'
import { LoadingState } from '../components/LoadingState'
import { WarningBadges } from '../components/WarningBadges'
import { useApiData } from '../hooks/useApiData'
import { isValidMonth, monthRange } from '../lib/dates'
import { currentMonthInSeoul, formatDateTime, formatKm, formatRatio } from '../lib/format'
import { WARNING_LABEL } from '../lib/labels'
import { Link } from '../router/Link'
import { navigate, useLocation } from '../router/router'

function tripsLink(month: string, vehicleId: number, purpose?: string): string {
  const { from, to } = monthRange(month)
  const params = new URLSearchParams({ from, to, vehicleId: String(vehicleId) })
  if (purpose) params.set('purpose', purpose)
  return `/trips?${params.toString()}`
}

export function HomePage() {
  const { searchParams } = useLocation()
  const requested = searchParams.get('month') ?? ''
  const month = isValidMonth(requested) ? requested : currentMonthInSeoul()
  const { data, error, loading, reload } = useApiData(month, (signal) => fetchSummary(month, signal))
  const summary = data && data.month === month ? data : undefined

  return (
    <section aria-labelledby="home-title">
      <div className="page-header">
        <div>
          <h1 id="home-title">월간 업무사용비율</h1>
          <p className="page-header__desc">차량별 주행거리와 업무사용비율, 확인이 필요한 경고를 한눈에 봅니다.</p>
        </div>
        <div className="field field--inline">
          <label htmlFor="summary-month">조회 월</label>
          <input
            id="summary-month"
            type="month"
            value={month}
            onChange={(event) => {
              if (isValidMonth(event.target.value)) navigate(`/?month=${event.target.value}`, { replace: true })
            }}
          />
        </div>
      </div>

      <LoadingState loading={loading && !summary} error={error} onRetry={reload} />

      {summary && summary.vehicles.length === 0 && <p className="empty">활성 차량이 없습니다. 차량 메뉴에서 차량을 추가해 주세요.</p>}

      {summary && summary.vehicles.length > 0 && <SummaryCards summary={summary} />}

      {summary && summary.vehicles.length > 0 && (
        <div className="table-wrap">
          <div className="table-wrap__head">
            <h2>차량별 집계</h2>
            <span className="muted small">단위 km · 업무사용비율 = (출퇴근 + 업무) ÷ 총주행거리</span>
          </div>
          <table className="table" aria-describedby="home-title">
            <caption className="sr-only">{summary.month} 차량별 운행 집계 (단위 km)</caption>
            <thead>
              <tr>
                <th scope="col">차량</th>
                <th scope="col">기본 운전자</th>
                <th scope="col" className="num">운행</th>
                <th scope="col" className="num">총주행</th>
                <th scope="col" className="num">출퇴근</th>
                <th scope="col" className="num">업무</th>
                <th scope="col" className="num">개인</th>
                <th scope="col" className="num">미확인</th>
                <th scope="col" className="num">업무비율</th>
                <th scope="col" className="num">최대 비율</th>
                <th scope="col">마지막 동기화</th>
                <th scope="col">경고</th>
              </tr>
            </thead>
            <tbody>
              {summary.vehicles.map((vehicle) => (
                <tr key={vehicle.vehicleId} data-testid={`summary-row-${vehicle.vehicleId}`}>
                  <th scope="row">
                    <Link to={tripsLink(summary.month, vehicle.vehicleId)}>{vehicle.regNo}</Link>
                    <div className="muted small">{vehicle.modelName}</div>
                  </th>
                  <td>{vehicle.defaultDriver ? `${vehicle.defaultDriver.name} (${vehicle.defaultDriver.dept})` : '-'}</td>
                  <td className="num">{vehicle.tripCount}건</td>
                  <td className="num">{formatKm(vehicle.totalKm)}</td>
                  <td className="num">{formatKm(vehicle.commuteKm)}</td>
                  <td className="num">{formatKm(vehicle.businessKm)}</td>
                  <td className="num">{formatKm(vehicle.personalKm)}</td>
                  <td className="num">
                    {formatKm(vehicle.unconfirmedKm)}
                    {vehicle.unconfirmedCount > 0 && (
                      <div className="small">
                        <Link to={tripsLink(summary.month, vehicle.vehicleId, 'UNCONFIRMED')}>
                          {vehicle.unconfirmedCount}건 보기
                        </Link>
                      </div>
                    )}
                  </td>
                  <td className="num">
                    <span className="ratio">{formatRatio(vehicle.businessRatio)}</span>
                    {vehicle.totalKm > 0 && <DistanceBar parts={vehicle} />}
                  </td>
                  <td className="num">{formatRatio(vehicle.maxBusinessRatio)}</td>
                  <td>
                    {vehicle.paired ? (
                      <span className="sync">
                        <span className="dot dot--ok" aria-hidden="true" />
                        {formatDateTime(vehicle.lastSyncedAt)}
                      </span>
                    ) : (
                      <span className="sync muted">
                        <span className="dot dot--off" aria-hidden="true" />
                        미연결
                      </span>
                    )}
                  </td>
                  <td>
                    <WarningBadges warnings={vehicle.warnings} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr data-testid="summary-totals">
                <th scope="row">합계</th>
                <td />
                <td />
                <td className="num">{formatKm(summary.totals.totalKm)}</td>
                <td className="num">{formatKm(summary.totals.commuteKm)}</td>
                <td className="num">{formatKm(summary.totals.businessKm)}</td>
                <td className="num">{formatKm(summary.totals.personalKm)}</td>
                <td className="num">
                  {formatKm(summary.totals.unconfirmedKm)}
                  {summary.totals.unconfirmedCount > 0 && <div className="small">{summary.totals.unconfirmedCount}건</div>}
                </td>
                <td className="num strong">{formatRatio(summary.totals.businessRatio)}</td>
                <td />
                <td />
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      <details className="legend card">
        <summary>경고 설명</summary>
        <dl>
          {Object.entries(WARNING_LABEL).map(([code, info]) => (
            <div key={code}>
              <dt>{info.label}</dt>
              <dd>{info.description}</dd>
            </div>
          ))}
        </dl>
      </details>
    </section>
  )
}

function ratioOf(part: number, total: number): number | null {
  return total > 0 ? Math.round((part / total) * 1000) / 10 : null
}

/** 홈 상단 요약 카드: 총주행, 업무사용비율, 미확인, 경고 차량 */
function SummaryCards({ summary }: { summary: DashboardSummary }) {
  const { totals } = summary
  const ratio = totals.businessRatio
  const maxRatio = ratioOf(totals.commuteKm + totals.businessKm + totals.unconfirmedKm, totals.totalKm)
  const warned = summary.vehicles.filter((vehicle) => vehicle.warnings.length > 0).length
  const trips = summary.vehicles.reduce((sum, vehicle) => sum + vehicle.tripCount, 0)
  return (
    <div className="kpis" aria-label="이번 달 요약">
      <div className="kpi kpi--primary">
        <div className="kpi__label">
          <Icon name="gauge" size={18} />
          업무사용비율
        </div>
        <div className="kpi__value">{formatRatio(ratio)}</div>
        <div className="meter" aria-hidden="true">
          <span className="meter__fill" style={{ width: `${Math.min(ratio ?? 0, 100)}%` }} />
          {maxRatio !== null && <span className="meter__max" style={{ left: `${Math.min(maxRatio, 100)}%` }} />}
        </div>
        <div className="kpi__sub">미확인이 모두 업무라면 최대 {formatRatio(maxRatio)}</div>
      </div>
      <div className="kpi">
        <div className="kpi__label">
          <Icon name="route" size={18} />
          총주행거리
        </div>
        <div className="kpi__value">
          {formatKm(totals.totalKm)}
          <span className="kpi__unit">km</span>
        </div>
        <DistanceBar parts={totals} showLegend />
      </div>
      <div className={totals.unconfirmedCount > 0 ? 'kpi kpi--attention' : 'kpi'}>
        <div className="kpi__label">
          <Icon name="question" size={18} />
          미확인 운행
        </div>
        <div className="kpi__value">
          {totals.unconfirmedCount}
          <span className="kpi__unit">건</span>
        </div>
        <div className="kpi__sub">전체 {trips}건 중 · {formatKm(totals.unconfirmedKm)} km</div>
      </div>
      <div className={warned > 0 ? 'kpi kpi--attention' : 'kpi'}>
        <div className="kpi__label">
          <Icon name="alert" size={18} />
          경고 차량
        </div>
        <div className="kpi__value">
          {warned}
          <span className="kpi__unit">/ {summary.vehicles.length}대</span>
        </div>
        <div className="kpi__sub">{warned > 0 ? '표의 경고 항목을 확인해 주세요' : '확인할 경고가 없습니다'}</div>
      </div>
    </div>
  )
}
