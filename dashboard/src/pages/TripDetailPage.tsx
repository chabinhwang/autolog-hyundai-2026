import { useState, type FormEvent } from 'react'
import { isApiError } from '../api/client'
import { fetchDrivers } from '../api/drivers'
import { editTrip, fetchTrip } from '../api/trips'
import type { BusinessCategory, Driver, EditValue, Purpose, TripDetail, TripEditChanges } from '../api/types'
import { Alert } from '../components/Alert'
import { LoadingState } from '../components/LoadingState'
import { useApiData } from '../hooks/useApiData'
import { monthRange } from '../lib/dates'
import { errorMessage } from '../lib/errors'
import { formatDateTime, formatKm, formatTripDate } from '../lib/format'
import {
  ACTOR_TYPE_LABEL,
  BUSINESS_CATEGORIES,
  BUSINESS_CATEGORY_LABEL,
  EDIT_FIELD_LABEL,
  PURPOSE_LABEL,
  PURPOSE_SOURCE_LABEL,
  labelOf,
} from '../lib/labels'
import { describeDriver, describePlace, describePurpose } from '../lib/trip'
import { Link } from '../router/Link'

const NOTE_MAX = 200
const REASON_MAX = 200

/** 관리자가 고를 수 있는 용도. PERSONAL은 운전자만 지정 (D4) */
const ADMIN_PURPOSES: Purpose[] = ['COMMUTE', 'BUSINESS']

function formatEditValue(field: string, value: EditValue, drivers: Driver[] | undefined, masked: boolean): string {
  if (field === 'note' && masked && value === null) return '(비공개)'
  if (value === null || value === '') return '(없음)'
  switch (field) {
    case 'purpose':
      return labelOf(PURPOSE_LABEL, String(value))
    case 'purposeSource':
      return labelOf(PURPOSE_SOURCE_LABEL, String(value))
    case 'businessCategory':
      return labelOf(BUSINESS_CATEGORY_LABEL, String(value))
    case 'driverId': {
      const driver = drivers?.find((d) => d.id === Number(value))
      return driver ? describeDriver(driver) : `직원 #${value}`
    }
    default:
      return String(value)
  }
}

export function TripDetailPage({ clientUuid }: { clientUuid: string }) {
  const trip = useApiData(`trip:${clientUuid}`, (signal) => fetchTrip(clientUuid, signal))
  const drivers = useApiData('drivers:all', (signal) => fetchDrivers(true, signal))
  const [notice, setNotice] = useState<string | null>(null)
  const detail = trip.data && trip.data.clientUuid === clientUuid ? trip.data : undefined

  if (!detail) {
    return (
      <section>
        <p>
          <Link to="/trips">← 운행 기록</Link>
        </p>
        <LoadingState loading={trip.loading} error={trip.error} onRetry={isApiError(trip.error, 'TRIP_NOT_FOUND') ? undefined : trip.reload} />
      </section>
    )
  }

  const { from, to } = monthRange(detail.tripDate.slice(0, 7))
  const backLink = `/trips?from=${from}&to=${to}&vehicleId=${detail.vehicle.id}`

  return (
    <section aria-labelledby="trip-title">
      <p>
        <Link to={backLink}>← 운행 기록</Link>
      </p>
      <div className="page-header">
        <h1 id="trip-title">
          운행 상세 <span className="muted small">{formatTripDate(detail.tripDate)} · {detail.vehicle.regNo}</span>
        </h1>
      </div>

      {detail.masked && (
        <Alert kind="info">
          {detail.purpose === 'PERSONAL'
            ? '개인 운행입니다. 장소·메모·시각은 공개되지 않습니다.'
            : '미확인 운행입니다. 집·미등록 장소, 메모, 시각은 공개되지 않습니다.'}
        </Alert>
      )}

      <div className="grid-2">
        <section className="card" aria-labelledby="trip-current-title">
          <h2 id="trip-current-title">현재 기록</h2>
          <dl className="dl">
            <dt>사용일자</dt>
            <dd>{formatTripDate(detail.tripDate)}</dd>
            <dt>차량</dt>
            <dd>{detail.vehicle.regNo}</dd>
            <dt>직원</dt>
            <dd>{describeDriver(detail.driver)}</dd>
            <dt>출발</dt>
            <dd>{describePlace(detail.origin, detail.masked)}</dd>
            <dt>도착</dt>
            <dd>{describePlace(detail.dest, detail.masked)}</dd>
            <dt>용도</dt>
            <dd>
              <span className={`purpose purpose--${detail.purpose.toLowerCase()}`}>
                {describePurpose(detail.purpose, detail.businessCategory)}
              </span>
            </dd>
            <dt>판정 출처</dt>
            <dd>{labelOf(PURPOSE_SOURCE_LABEL, detail.purposeSource)}</dd>
            <dt>메모</dt>
            <dd>{detail.note ?? (detail.masked ? '(비공개)' : '-')}</dd>
          </dl>
        </section>

        <section className="card" aria-labelledby="trip-auto-title">
          <h2 id="trip-auto-title">차량 자동 기록 (수정 불가)</h2>
          <dl className="dl">
            <dt>시작 시각</dt>
            <dd>{detail.startedAt ? formatDateTime(detail.startedAt) : '(비공개)'}</dd>
            <dt>종료 시각</dt>
            <dd>{detail.endedAt ? formatDateTime(detail.endedAt) : '(비공개)'}</dd>
            <dt>주행 전 계기판</dt>
            <dd>{formatKm(detail.odoStartKm)} km</dd>
            <dt>주행 후 계기판</dt>
            <dd>{formatKm(detail.odoEndKm)} km</dd>
            <dt>주행거리</dt>
            <dd>{formatKm(detail.distanceKm)} km</dd>
            <dt>자동 추정 용도</dt>
            <dd>{labelOf(PURPOSE_LABEL, detail.auto.purpose)}</dd>
            <dt>차량 저장 시각</dt>
            <dd>{formatDateTime(detail.recordedAt)}</dd>
            <dt>서버 수신 시각</dt>
            <dd>{formatDateTime(detail.syncedAt)}</dd>
            <dt>앱 버전</dt>
            <dd>{detail.appVersion ?? '-'}</dd>
          </dl>
        </section>
      </div>

      <section className="card" aria-labelledby="trip-edits-title">
        <h2 id="trip-edits-title">수정 이력</h2>
        {detail.edits.length === 0 ? (
          <p className="muted">수정 이력이 없습니다.</p>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">순번</th>
                  <th scope="col">수정 주체</th>
                  <th scope="col">변경 내용</th>
                  <th scope="col">사유</th>
                  <th scope="col">수정 시각</th>
                  <th scope="col">서버 수신</th>
                </tr>
              </thead>
              <tbody>
                {detail.edits.map((edit) => (
                  <tr key={edit.seq} data-testid={`edit-row-${edit.seq}`}>
                    <td>{edit.seq}</td>
                    <td>
                      {labelOf(ACTOR_TYPE_LABEL, edit.actorType)}
                      {edit.actorName && <div className="muted small">{edit.actorName}</div>}
                    </td>
                    <td className="wrap">
                      <ul className="changes">
                        {edit.changes.map((change) => (
                          <li key={change.field}>
                            <span className="strong">{EDIT_FIELD_LABEL[change.field] ?? change.field}</span>:{' '}
                            {formatEditValue(change.field, change.before, drivers.data, detail.masked)} →{' '}
                            {formatEditValue(change.field, change.after, drivers.data, detail.masked)}
                          </li>
                        ))}
                      </ul>
                    </td>
                    <td className="wrap">{edit.reason ?? '-'}</td>
                    <td>{formatDateTime(edit.editedAt)}</td>
                    <td>{formatDateTime(edit.receivedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="card" aria-labelledby="trip-edit-title">
        <h2 id="trip-edit-title">관리자 수정</h2>
        {detail.purpose === 'PERSONAL' ? (
          <Alert kind="warning">
            개인 운행은 관리자가 수정할 수 없습니다. 용도 변경은 차량에서 운전자만 할 수 있습니다.
          </Alert>
        ) : (
          <>
            {notice && <Alert kind="success">{notice}</Alert>}
            <EditForm
              key={detail.edits.length}
              trip={detail}
              drivers={drivers.data}
              onSaving={() => setNotice(null)}
              onSaved={(message) => {
                setNotice(message)
                trip.reload()
              }}
            />
          </>
        )}
      </section>
    </section>
  )
}

interface EditFormProps {
  trip: TripDetail
  drivers: Driver[] | undefined
  onSaving: () => void
  onSaved: (message: string) => void
}

function EditForm({ trip, drivers, onSaving, onSaved }: EditFormProps) {
  const [purpose, setPurpose] = useState<Purpose>(trip.purpose)
  const [category, setCategory] = useState<BusinessCategory>(trip.businessCategory ?? 'OTHER')
  const [note, setNote] = useState(trip.note ?? '')
  const [driverId, setDriverId] = useState(trip.driver ? String(trip.driver.id) : '')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 메모가 가려진 운행(미확인)은 원래 메모를 모르는 채 덮어쓰지 않도록 메모 수정을 막습니다.
  const noteEditable = !trip.masked
  const purposeOptions: Purpose[] = ADMIN_PURPOSES.includes(trip.purpose) ? ADMIN_PURPOSES : [trip.purpose, ...ADMIN_PURPOSES]
  const activeDrivers = (drivers ?? []).filter((d) => d.active || d.id === trip.driver?.id)
  if (trip.driver && !activeDrivers.some((d) => d.id === trip.driver?.id)) {
    activeDrivers.unshift({ ...trip.driver, active: true })
  }

  function buildChanges(): TripEditChanges {
    const changes: TripEditChanges = {}
    if (purpose !== trip.purpose) changes.purpose = purpose
    if (purpose === 'BUSINESS' && category !== trip.businessCategory) changes.businessCategory = category
    if (noteEditable) {
      const nextNote = note.trim() === '' ? null : note.trim()
      if (nextNote !== (trip.note ?? null)) changes.note = nextNote
    }
    const nextDriver = driverId === '' ? null : Number(driverId)
    if (nextDriver !== (trip.driver?.id ?? null)) changes.driverId = nextDriver
    return changes
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSaving()
    const trimmedReason = reason.trim()
    if (!trimmedReason) {
      setError('수정 사유를 입력해 주세요.')
      return
    }
    const changes = buildChanges()
    if (Object.keys(changes).length === 0) {
      setError('바뀐 항목이 없습니다.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const response = await editTrip(trip.clientUuid, { reason: trimmedReason, changes })
      setSubmitting(false)
      onSaved(response.edit ? '수정했습니다. 수정 이력에 기록되었습니다.' : '바뀐 항목이 없어 이력을 남기지 않았습니다.')
    } catch (err) {
      // EDIT_REASON_REQUIRED, PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN, AUTO_FIELD_IMMUTABLE 등 서버 메시지 표시
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate aria-describedby="trip-edit-help">
      <p id="trip-edit-help" className="muted small">
        수정할 수 있는 항목은 용도·업무 세부·메모·직원입니다. 시각·계기판 거리는 차량이 기록한 값이라 바꿀 수 없습니다. 관리자는 용도를
        개인으로 바꿀 수 없습니다.
      </p>
      {error && <Alert kind="error">{error}</Alert>}
      <div className="form__row">
        <div className="field">
          <label htmlFor="edit-purpose">용도</label>
          <select id="edit-purpose" value={purpose} onChange={(e) => setPurpose(e.target.value as Purpose)}>
            {purposeOptions.map((p) => (
              <option key={p} value={p}>
                {PURPOSE_LABEL[p]}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="edit-category">업무 세부</label>
          <select
            id="edit-category"
            value={category}
            disabled={purpose !== 'BUSINESS'}
            onChange={(e) => setCategory(e.target.value as BusinessCategory)}
            aria-describedby="edit-category-help"
          >
            {BUSINESS_CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {BUSINESS_CATEGORY_LABEL[c]}
              </option>
            ))}
          </select>
          <span id="edit-category-help" className="muted small">
            용도가 업무일 때만 저장됩니다.
          </span>
        </div>
        <div className="field">
          <label htmlFor="edit-driver">직원</label>
          <select id="edit-driver" value={driverId} onChange={(e) => setDriverId(e.target.value)}>
            <option value="">미지정</option>
            {activeDrivers.map((d) => (
              <option key={d.id} value={String(d.id)}>
                {describeDriver(d)}
                {d.active ? '' : ' (비활성)'}
              </option>
            ))}
          </select>
        </div>
      </div>
      <div className="field">
        <label htmlFor="edit-note">메모</label>
        <textarea
          id="edit-note"
          rows={2}
          maxLength={NOTE_MAX}
          value={noteEditable ? note : ''}
          placeholder={noteEditable ? '' : '(비공개 메모는 수정할 수 없습니다)'}
          disabled={!noteEditable}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor="edit-reason">
          수정 사유 <span className="required">(필수)</span>
        </label>
        <input
          id="edit-reason"
          type="text"
          required
          aria-required="true"
          maxLength={REASON_MAX}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>
      <div className="form__actions">
        <button type="submit" className="button button--primary" disabled={submitting}>
          {submitting ? '저장 중…' : '수정 저장'}
        </button>
      </div>
    </form>
  )
}

