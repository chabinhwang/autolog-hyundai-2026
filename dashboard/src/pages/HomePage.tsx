import { fetchSummary } from '../api/dashboard'
import type { DashboardSummary } from '../api/types'
import { DistanceBar } from '../components/DistanceBar'
import { EmptyState } from '../components/EmptyState'
import { Icon } from '../components/Icon'
import { LoadingState } from '../components/LoadingState'
import { PageHeader } from '../components/PageHeader'
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
      <PageHeader
        id="home-title"
        title="월간 업무사용비율"
        description="차량별 주행거리와 업무사용비율, 확인이 필요한 경고를 한눈에 봅니다."
        actions={
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
        }
      />

      <LoadingState loading={loading && !summary} error={error} onRetry={reload} />

      {summary && summary.vehicles.length === 0 && (
        <EmptyState icon="car">활성 차량이 없습니다. 차량 메뉴에서 차량을 추가해 주세요.</EmptyState>
      )}

      {summary && summary.vehicles.length > 0 && <SummaryCards summary={summary} />}

      {summary && summary.vehicles.length > 0 && (
        <div className="table-wrap">
          <div className="table-wrap__head">
            <h2>차량별 집계</h2>
            <p className="table-wrap__note">
              단위 km · 업무사용비율 = (출퇴근 + 업무) ÷ 총주행거리
            </p>
          </div>
          <table className="table table--cards" aria-describedby="home-title">
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
                  <th scope="row" className="cell-title">
                    <Link to={tripsLink(summary.month, vehicle.vehicleId)}>{vehicle.regNo}</Link>
                    <div className="cell-sub">{vehicle.modelName}</div>
                  </th>
                  <td className="span-all" data-label="기본 운전자">{vehicle.defaultDriver ? `${vehicle.defaultDriver.name} (${vehicle.defaultDriver.dept})` : '-'}</td>
                  <td className="num" data-label="운행">{vehicle.tripCount}건</td>
                  <td className="num" data-label="총주행">{formatKm(vehicle.totalKm)}</td>
                  <td className="num" data-label="출퇴근">{formatKm(vehicle.commuteKm)}</td>
                  <td className="num" data-label="업무">{formatKm(vehicle.businessKm)}</td>
                  <td className="num" data-label="개인">{formatKm(vehicle.personalKm)}</td>
                  <td className="num" data-label="미확인">
                    {formatKm(vehicle.unconfirmedKm)}
                    {vehicle.unconfirmedCount > 0 && (
                      <div className="cell-sub">
                        <Link to={tripsLink(summary.month, vehicle.vehicleId, 'UNCONFIRMED')}>
                          {vehicle.unconfirmedCount}건 보기
                        </Link>
                      </div>
                    )}
                  </td>
                  <td className="num" data-label="업무비율">
                    <span className="ratio">{formatRatio(vehicle.businessRatio)}</span>
                    {vehicle.totalKm > 0 && <DistanceBar parts={vehicle} />}
                  </td>
                  <td className="num" data-label="최대 비율">{formatRatio(vehicle.maxBusinessRatio)}</td>
                  <td className="span-all" data-label="마지막 동기화">
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
                  <td className="span-all" data-label="경고">
                    <WarningBadges warnings={vehicle.warnings} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr data-testid="summary-totals">
                <th scope="row" className="cell-title">합계</th>
                <td />
                <td />
                <td className="num" data-label="총주행">{formatKm(summary.totals.totalKm)}</td>
                <td className="num" data-label="출퇴근">{formatKm(summary.totals.commuteKm)}</td>
                <td className="num" data-label="업무">{formatKm(summary.totals.businessKm)}</td>
                <td className="num" data-label="개인">{formatKm(summary.totals.personalKm)}</td>
                <td className="num" data-label="미확인">
                  {formatKm(summary.totals.unconfirmedKm)}
                  {summary.totals.unconfirmedCount > 0 && <div className="cell-sub">{summary.totals.unconfirmedCount}건</div>}
                </td>
                <td className="num" data-label="업무비율">
                  <span className="ratio">{formatRatio(summary.totals.businessRatio)}</span>
                </td>
                <td />
                <td />
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      <details className="legend card">
        <summary>
          <Icon name="info" size={18} />
          <span>경고 설명</span>
          <Icon name="chevronDown" size={18} className="legend__chevron" />
        </summary>
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
          <span className="kpi__icon">
            <Icon name="gauge" size={18} />
          </span>
          업무사용비율
        </div>
        <div className="kpi__value">{formatRatio(ratio)}</div>
        <div className="meter" aria-hidden="true">
          <span className="meter__fill" style={{ width: `${Math.min(ratio ?? 0, 100)}%` }} />
          {maxRatio !== null && <span className="meter__max" style={{ left: `${Math.min(maxRatio, 100)}%` }} />}
        </div>
        <div className="kpi__sub">미확인이 모두 업무라면 최대 {formatRatio(maxRatio)}</div>
      </div>
      <div className="kpi kpi--wide">
        <div className="kpi__label">
          <span className="kpi__icon">
            <Icon name="route" size={18} />
          </span>
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
          <span className="kpi__icon">
            <Icon name="question" size={18} />
          </span>
          미확인 운행
        </div>
        <div className="kpi__value">
          {totals.unconfirmedCount}
          <span className="kpi__unit">건</span>
        </div>
        <div className="kpi__sub">
          전체 {trips}건 중 · {formatKm(totals.unconfirmedKm)} km
        </div>
      </div>
      <div className={warned > 0 ? 'kpi kpi--attention' : 'kpi'}>
        <div className="kpi__label">
          <span className="kpi__icon">
            <Icon name="alert" size={18} />
          </span>
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
