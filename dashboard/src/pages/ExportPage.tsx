import { useState, type FormEvent } from 'react'
import { fetchLogbook } from '../api/reports'
import { fetchVehicles } from '../api/vehicles'
import { Alert } from '../components/Alert'
import { LoadingState } from '../components/LoadingState'
import { useApiData } from '../hooks/useApiData'
import { MAX_RANGE_DAYS, monthRange, validateRange } from '../lib/dates'
import { saveBlob } from '../lib/download'
import { errorMessage } from '../lib/errors'
import { currentMonthInSeoul, todayInSeoul } from '../lib/format'

export function ExportPage() {
  const vehicles = useApiData('vehicles:all', (signal) => fetchVehicles(true, signal))
  const initial = monthRange(currentMonthInSeoul())
  const [vehicleId, setVehicleId] = useState('')
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  function setYearToDate() {
    const today = todayInSeoul()
    setFrom(`${today.slice(0, 4)}-01-01`)
    setTo(today)
  }

  function setLastYear() {
    const year = Number(todayInSeoul().slice(0, 4)) - 1
    setFrom(`${year}-01-01`)
    setTo(`${year}-12-31`)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setDone(null)
    const vehicle = vehicles.data?.find((v) => String(v.id) === vehicleId)
    if (!vehicle) {
      setError('차량을 선택해 주세요.')
      return
    }
    const rangeError = validateRange(from, to)
    if (rangeError) {
      setError(rangeError)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const file = await fetchLogbook({ vehicleId: vehicle.id, regNo: vehicle.regNo, from, to })
      saveBlob(file.blob, file.filename)
      setDone(`${file.filename} 파일을 내려받았습니다.`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-labelledby="export-title">
      <div className="page-header">
        <h1 id="export-title">운행기록부 내보내기</h1>
      </div>
      <p className="muted">
        업무용승용차 운행기록부 항목 순서에 맞춘 엑셀(XLSX) 파일을 만듭니다. 개인 운행은 장소·시각이 가려진 채로 들어가고, 미확인 운행이
        있으면 파일 상단에 경고가 표시됩니다.
      </p>

      <LoadingState loading={vehicles.loading && !vehicles.data} error={vehicles.error} onRetry={vehicles.reload} />

      <form className="card form" onSubmit={handleSubmit} noValidate aria-label="기록부 내보내기 조건">
        {error && <Alert kind="error">{error}</Alert>}
        {done && <Alert kind="success">{done}</Alert>}
        <div className="form__row">
          <div className="field">
            <label htmlFor="export-vehicle">차량</label>
            <select id="export-vehicle" required value={vehicleId} onChange={(e) => setVehicleId(e.target.value)}>
              <option value="">선택하세요</option>
              {vehicles.data?.map((v) => (
                <option key={v.id} value={String(v.id)}>
                  {v.regNo} · {v.modelName}
                  {v.active ? '' : ' (비활성)'}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="export-from">시작일</label>
            <input id="export-from" type="date" required value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="export-to">종료일</label>
            <input id="export-to" type="date" required value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <div className="actions">
          <button type="button" className="button button--small" onClick={setYearToDate}>
            올해 1월 1일 ~ 오늘
          </button>
          <button type="button" className="button button--small" onClick={setLastYear}>
            작년 1년
          </button>
        </div>
        <p className="muted small">기간은 날짜를 포함해 최대 {MAX_RANGE_DAYS}일입니다. 1차는 XLSX 형식만 지원합니다.</p>
        <div className="form__actions">
          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? '만드는 중…' : 'XLSX 내려받기'}
          </button>
        </div>
      </form>
    </section>
  )
}
