import { fetchSummary } from '../api/dashboard'
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
        <h1 id="home-title">월간 업무사용비율</h1>
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

      <p className="muted">
        업무사용비율 = (출퇴근 + 업무) ÷ 총주행거리. 최대 비율은 미확인 운행이 모두 업무일 때의 비율입니다.
      </p>

      <LoadingState loading={loading && !summary} error={error} onRetry={reload} />

      {summary && summary.vehicles.length === 0 && <p className="empty">활성 차량이 없습니다. 차량 메뉴에서 차량을 추가해 주세요.</p>}

      {summary && summary.vehicles.length > 0 && (
        <div className="table-wrap">
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
                  <td className="num strong">{formatRatio(vehicle.businessRatio)}</td>
                  <td className="num">{formatRatio(vehicle.maxBusinessRatio)}</td>
                  <td>{vehicle.paired ? formatDateTime(vehicle.lastSyncedAt) : <span className="muted">미연결</span>}</td>
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

      <details className="legend">
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
