import { useState, type FormEvent } from 'react'
import { fetchDrivers } from '../api/drivers'
import { fetchTrips } from '../api/trips'
import type { Purpose } from '../api/types'
import { fetchVehicles } from '../api/vehicles'
import { Alert } from '../components/Alert'
import { LoadingState } from '../components/LoadingState'
import { useApiData } from '../hooks/useApiData'
import { MAX_RANGE_DAYS, monthRange, validateRange } from '../lib/dates'
import { currentMonthInSeoul, formatKm, formatTripDate } from '../lib/format'
import { PURPOSES, PURPOSE_LABEL, PURPOSE_SOURCE_LABEL, labelOf } from '../lib/labels'
import { describeDriver, describePlace, describePurpose, describeTimeRange } from '../lib/trip'
import { Link } from '../router/Link'
import { navigate, useLocation } from '../router/router'

const PAGE_SIZE = 20

interface Filters {
  from: string
  to: string
  vehicleId: string
  driverId: string
  purpose: string
}

function readFilters(params: URLSearchParams): Filters {
  const defaults = monthRange(currentMonthInSeoul())
  return {
    from: params.get('from') ?? defaults.from,
    to: params.get('to') ?? defaults.to,
    vehicleId: params.get('vehicleId') ?? '',
    driverId: params.get('driverId') ?? '',
    purpose: params.get('purpose') ?? '',
  }
}

function toSearch(filters: Filters, page: number): string {
  const params = new URLSearchParams()
  params.set('from', filters.from)
  params.set('to', filters.to)
  if (filters.vehicleId) params.set('vehicleId', filters.vehicleId)
  if (filters.driverId) params.set('driverId', filters.driverId)
  if (filters.purpose) params.set('purpose', filters.purpose)
  if (page > 0) params.set('page', String(page))
  return `?${params.toString()}`
}

function toNumber(value: string): number | undefined {
  const n = Number(value)
  return value && Number.isInteger(n) ? n : undefined
}

export function TripListPage() {
  const { searchParams, search } = useLocation()
  const applied = readFilters(searchParams)
  const page = Math.max(0, toNumber(searchParams.get('page') ?? '') ?? 0)
  const rangeError = validateRange(applied.from, applied.to)

  // 필터 입력값은 URL(적용된 조건)과 따로 둡니다. URL이 바뀌면 입력값을 다시 맞춥니다.
  const [draft, setDraft] = useState<Filters>(applied)
  const [draftSearch, setDraftSearch] = useState(search)
  if (draftSearch !== search) {
    setDraftSearch(search)
    setDraft(applied)
  }
  const [formError, setFormError] = useState<string | null>(null)

  const vehicles = useApiData('vehicles:all', (signal) => fetchVehicles(true, signal))
  const drivers = useApiData('drivers:all', (signal) => fetchDrivers(true, signal))
  const trips = useApiData(rangeError ? 'invalid' : `${search}`, (signal) =>
    rangeError
      ? Promise.resolve(undefined)
      : fetchTrips(
          {
            from: applied.from,
            to: applied.to,
            vehicleId: toNumber(applied.vehicleId),
            driverId: toNumber(applied.driverId),
            purpose: (applied.purpose || undefined) as Purpose | undefined,
            page,
            size: PAGE_SIZE,
          },
          signal,
        ),
  )

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = validateRange(draft.from, draft.to)
    setFormError(message)
    if (message) return
    navigate(`/trips${toSearch(draft, 0)}`)
  }

  function update<K extends keyof Filters>(key: K, value: Filters[K]) {
    setDraft((prev) => ({ ...prev, [key]: value }))
  }

  const result = trips.data

  return (
    <section aria-labelledby="trips-title">
      <div className="page-header">
        <h1 id="trips-title">운행 기록</h1>
      </div>

      <form className="filters" onSubmit={handleSubmit} aria-label="운행 기록 조회 조건">
        <div className="field">
          <label htmlFor="trips-from">시작일</label>
          <input id="trips-from" type="date" required value={draft.from} onChange={(e) => update('from', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="trips-to">종료일</label>
          <input id="trips-to" type="date" required value={draft.to} onChange={(e) => update('to', e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="trips-vehicle">차량</label>
          <select id="trips-vehicle" value={draft.vehicleId} onChange={(e) => update('vehicleId', e.target.value)}>
            <option value="">전체</option>
            {vehicles.data?.map((v) => (
              <option key={v.id} value={String(v.id)}>
                {v.regNo}
                {v.active ? '' : ' (비활성)'}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="trips-driver">직원</label>
          <select id="trips-driver" value={draft.driverId} onChange={(e) => update('driverId', e.target.value)}>
            <option value="">전체</option>
            {drivers.data?.map((d) => (
              <option key={d.id} value={String(d.id)}>
                {d.name} ({d.dept}){d.active ? '' : ' (비활성)'}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="trips-purpose">용도</label>
          <select id="trips-purpose" value={draft.purpose} onChange={(e) => update('purpose', e.target.value)}>
            <option value="">전체</option>
            {PURPOSES.map((p) => (
              <option key={p} value={p}>
                {PURPOSE_LABEL[p]}
              </option>
            ))}
          </select>
        </div>
        <div className="filters__actions">
          <button type="submit" className="button button--primary">
            조회
          </button>
        </div>
        <p className="muted small filters__hint">기간은 최대 {MAX_RANGE_DAYS}일까지 조회할 수 있습니다.</p>
      </form>

      {formError && <Alert kind="error">{formError}</Alert>}
      {!formError && rangeError && <Alert kind="error">{rangeError}</Alert>}

      {!rangeError && <LoadingState loading={trips.loading && !result} error={trips.error} onRetry={trips.reload} />}

      {!rangeError && result && result.content.length === 0 && <p className="empty">조건에 맞는 운행 기록이 없습니다.</p>}

      {!rangeError && result && result.content.length > 0 && (
        <>
          <div className="table-wrap">
            <table className="table">
              <caption className="sr-only">운행 기록 목록 (시작 시각 내림차순)</caption>
              <thead>
                <tr>
                  <th scope="col">사용일자</th>
                  <th scope="col">시간</th>
                  <th scope="col">차량</th>
                  <th scope="col">직원</th>
                  <th scope="col">출발 → 도착</th>
                  <th scope="col" className="num">주행 전</th>
                  <th scope="col" className="num">주행 후</th>
                  <th scope="col" className="num">주행거리</th>
                  <th scope="col">용도</th>
                  <th scope="col">판정</th>
                  <th scope="col">
                    <span className="sr-only">상세</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((trip) => {
                  const time = describeTimeRange(trip.startedAt, trip.endedAt)
                  return (
                    <tr key={trip.clientUuid} data-testid={`trip-row-${trip.clientUuid}`}>
                      <td>{formatTripDate(trip.tripDate)}</td>
                      <td>{time ?? <span className="muted">{trip.masked ? '비공개' : '-'}</span>}</td>
                      <td>{trip.vehicle.regNo}</td>
                      <td>{describeDriver(trip.driver)}</td>
                      <td>
                        <span className={trip.masked && !trip.origin?.label ? 'muted' : undefined}>
                          {describePlace(trip.origin, trip.masked)}
                        </span>
                        {' → '}
                        <span className={trip.masked && !trip.dest?.label ? 'muted' : undefined}>
                          {describePlace(trip.dest, trip.masked)}
                        </span>
                      </td>
                      <td className="num">{formatKm(trip.odoStartKm)}</td>
                      <td className="num">{formatKm(trip.odoEndKm)}</td>
                      <td className="num">{formatKm(trip.distanceKm)} km</td>
                      <td>
                        <span className={`purpose purpose--${trip.purpose.toLowerCase()}`}>
                          {describePurpose(trip.purpose, trip.businessCategory)}
                        </span>
                        {trip.edited && <span className="badge badge--info">수정됨</span>}
                      </td>
                      <td className="small">{labelOf(PURPOSE_SOURCE_LABEL, trip.purposeSource)}</td>
                      <td>
                        <Link to={`/trips/${encodeURIComponent(trip.clientUuid)}`} aria-label={`${trip.tripDate} 운행 상세`}>
                          상세
                        </Link>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <nav className="pagination" aria-label="페이지">
            <button
              type="button"
              className="button button--small"
              disabled={result.page <= 0}
              onClick={() => navigate(`/trips${toSearch(applied, result.page - 1)}`)}
            >
              이전
            </button>
            <span>
              {result.page + 1} / {Math.max(result.totalPages, 1)} 페이지 (총 {result.totalElements}건)
            </span>
            <button
              type="button"
              className="button button--small"
              disabled={result.page + 1 >= result.totalPages}
              onClick={() => navigate(`/trips${toSearch(applied, result.page + 1)}`)}
            >
              다음
            </button>
          </nav>
        </>
      )}
    </section>
  )
}
