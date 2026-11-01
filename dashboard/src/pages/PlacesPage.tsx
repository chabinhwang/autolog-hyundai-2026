import { useState, type FormEvent } from 'react'
import { createPlace, deletePlace, fetchPlaces, updatePlace } from '../api/places'
import type { Place, PlaceType } from '../api/types'
import { Alert } from '../components/Alert'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { Modal } from '../components/Modal'
import { useApiData } from '../hooks/useApiData'
import { errorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/format'
import { PLACE_TYPE_LABEL } from '../lib/labels'

// 검증 범위 (master-data.md)
const LAT_MIN = -90
const LAT_MAX = 90
const LNG_MIN = -180
const LNG_MAX = 180
const RADIUS_MIN = 50
const RADIUS_MAX = 2000
const RADIUS_DEFAULT = 200
const PLACE_TYPES: PlaceType[] = ['COMPANY', 'CLIENT']

type DialogState = { type: 'create' } | { type: 'edit'; place: Place } | { type: 'delete'; place: Place } | null

export function PlacesPage() {
  const [typeFilter, setTypeFilter] = useState<PlaceType | ''>('')
  const places = useApiData(`places:${typeFilter}`, (signal) => fetchPlaces(typeFilter || undefined, signal))
  const [dialog, setDialog] = useState<DialogState>(null)
  const [notice, setNotice] = useState<string | null>(null)

  function open(next: DialogState) {
    setNotice(null)
    setDialog(next)
  }

  function done(message: string) {
    setDialog(null)
    setNotice(message)
    places.reload()
  }

  return (
    <section aria-labelledby="places-title">
      <div className="page-header">
        <h1 id="places-title">장소 (회사·거래처)</h1>
        <div className="page-header__actions">
          <div className="field field--inline">
            <label htmlFor="places-type">유형</label>
            <select id="places-type" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value as PlaceType | '')}>
              <option value="">전체</option>
              {PLACE_TYPES.map((t) => (
                <option key={t} value={t}>
                  {PLACE_TYPE_LABEL[t]}
                </option>
              ))}
            </select>
          </div>
          <button type="button" className="button button--primary" onClick={() => open({ type: 'create' })}>
            장소 추가
          </button>
        </div>
      </div>
      <p className="muted">
        차량 앱이 도착 장소를 판정하는 기준입니다. 집 위치는 서버에 저장하지 않습니다(차량 앱에만 보관). 바꾼 내용은 차량의 다음 설정
        내려받기 때 반영됩니다.
      </p>

      {notice && <Alert kind="success">{notice}</Alert>}
      <LoadingState loading={places.loading && !places.data} error={places.error} onRetry={places.reload} />

      {places.data && places.data.length === 0 && <p className="empty">등록된 장소가 없습니다.</p>}
      {places.data && places.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">이름</th>
                <th scope="col">유형</th>
                <th scope="col" className="num">위도</th>
                <th scope="col" className="num">경도</th>
                <th scope="col" className="num">반경</th>
                <th scope="col">수정 시각</th>
                <th scope="col">관리</th>
              </tr>
            </thead>
            <tbody>
              {places.data.map((place) => (
                <tr key={place.id}>
                  <th scope="row">{place.name}</th>
                  <td>{PLACE_TYPE_LABEL[place.type] ?? place.type}</td>
                  <td className="num">{place.lat.toFixed(6)}</td>
                  <td className="num">{place.lng.toFixed(6)}</td>
                  <td className="num">{place.radiusM} m</td>
                  <td>{formatDateTime(place.updatedAt)}</td>
                  <td>
                    <div className="actions">
                      <button
                        type="button"
                        className="button button--small"
                        onClick={() => open({ type: 'edit', place })}
                        aria-label={`${place.name} 수정`}
                      >
                        수정
                      </button>
                      <button
                        type="button"
                        className="button button--small button--danger-outline"
                        onClick={() => open({ type: 'delete', place })}
                        aria-label={`${place.name} 삭제`}
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(dialog?.type === 'create' || dialog?.type === 'edit') && (
        <PlaceFormDialog
          place={dialog.type === 'edit' ? dialog.place : undefined}
          onClose={() => setDialog(null)}
          onSaved={(place, created) => done(created ? `${place.name} 장소를 추가했습니다.` : `${place.name} 장소를 저장했습니다.`)}
        />
      )}

      {dialog?.type === 'delete' && (
        <ConfirmDialog
          title="장소 삭제"
          confirmLabel="삭제"
          danger
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await deletePlace(dialog.place.id)
            done(`${dialog.place.name} 장소를 삭제했습니다.`)
          }}
        >
          <p>
            <strong>{dialog.place.name}</strong> 장소를 삭제할까요?
          </p>
          <p className="muted">차량 앱에도 삭제가 전달됩니다. 과거 운행 기록의 장소 이름은 그대로 남습니다.</p>
        </ConfirmDialog>
      )}
    </section>
  )
}

interface PlaceFormDialogProps {
  place?: Place
  onClose: () => void
  onSaved: (place: Place, created: boolean) => void
}

/** 숫자 입력 검증. 오류 문구 또는 null */
function checkNumber(label: string, raw: string, min: number, max: number, integer = false): string | null {
  if (raw.trim() === '') return `${label}을(를) 입력해 주세요.`
  const value = Number(raw)
  if (!Number.isFinite(value)) return `${label}은(는) 숫자여야 합니다.`
  if (integer && !Number.isInteger(value)) return `${label}은(는) 정수여야 합니다.`
  if (value < min || value > max) return `${label}은(는) ${min}~${max} 범위여야 합니다.`
  return null
}

function PlaceFormDialog({ place, onClose, onSaved }: PlaceFormDialogProps) {
  const [name, setName] = useState(place?.name ?? '')
  const [type, setType] = useState<PlaceType>(place?.type ?? 'CLIENT')
  const [lat, setLat] = useState(place ? String(place.lat) : '')
  const [lng, setLng] = useState(place ? String(place.lng) : '')
  const [radius, setRadius] = useState(String(place?.radiusM ?? RADIUS_DEFAULT))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message =
      (name.trim() ? null : '이름을 입력해 주세요.') ??
      checkNumber('위도', lat, LAT_MIN, LAT_MAX) ??
      checkNumber('경도', lng, LNG_MIN, LNG_MAX) ??
      checkNumber('반경', radius, RADIUS_MIN, RADIUS_MAX, true)
    if (message) {
      setError(message)
      return
    }
    const body = { name: name.trim(), type, lat: Number(lat), lng: Number(lng), radiusM: Number(radius) }
    setBusy(true)
    setError(null)
    try {
      const saved = place ? await updatePlace(place.id, body) : await createPlace(body)
      onSaved(saved, !place)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={place ? '장소 수정' : '장소 추가'} onClose={onClose} busy={busy}>
      <form className="form" onSubmit={handleSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="place-name">이름</label>
          <input id="place-name" required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="place-type">유형</label>
          <select id="place-type" value={type} onChange={(e) => setType(e.target.value as PlaceType)}>
            {PLACE_TYPES.map((t) => (
              <option key={t} value={t}>
                {PLACE_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </div>
        <div className="form__row">
          <div className="field">
            <label htmlFor="place-lat">위도</label>
            <input
              id="place-lat"
              type="number"
              inputMode="decimal"
              step="0.000001"
              min={LAT_MIN}
              max={LAT_MAX}
              required
              value={lat}
              onChange={(e) => setLat(e.target.value)}
              aria-describedby="place-lat-help"
            />
            <span id="place-lat-help" className="muted small">
              {LAT_MIN} ~ {LAT_MAX}, 소수 6자리까지
            </span>
          </div>
          <div className="field">
            <label htmlFor="place-lng">경도</label>
            <input
              id="place-lng"
              type="number"
              inputMode="decimal"
              step="0.000001"
              min={LNG_MIN}
              max={LNG_MAX}
              required
              value={lng}
              onChange={(e) => setLng(e.target.value)}
              aria-describedby="place-lng-help"
            />
            <span id="place-lng-help" className="muted small">
              {LNG_MIN} ~ {LNG_MAX}, 소수 6자리까지
            </span>
          </div>
          <div className="field">
            <label htmlFor="place-radius">도착 판정 반경 (m)</label>
            <input
              id="place-radius"
              type="number"
              inputMode="numeric"
              step="1"
              min={RADIUS_MIN}
              max={RADIUS_MAX}
              value={radius}
              onChange={(e) => setRadius(e.target.value)}
              aria-describedby="place-radius-help"
            />
            <span id="place-radius-help" className="muted small">
              {RADIUS_MIN} ~ {RADIUS_MAX}m, 기본 {RADIUS_DEFAULT}m
            </span>
          </div>
        </div>
        <p className="muted small">지도 검색은 아직 없습니다. 지도 서비스에서 좌표를 확인해 직접 입력해 주세요.</p>
        <div className="modal__actions">
          <button type="button" className="button" onClick={onClose} disabled={busy}>
            취소
          </button>
          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? '저장 중…' : '저장'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
