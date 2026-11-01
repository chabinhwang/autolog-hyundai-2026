import { useState, type FormEvent } from 'react'
import { fetchDrivers } from '../api/drivers'
import type { Driver, PairResponse, Vehicle } from '../api/types'
import { createVehicle, fetchVehicles, pairDevice, unpairDevice, updateVehicle } from '../api/vehicles'
import { Alert } from '../components/Alert'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { Modal } from '../components/Modal'
import { useApiData } from '../hooks/useApiData'
import { errorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/format'
import { describeDriver } from '../lib/trip'

type DialogState =
  | { type: 'create' }
  | { type: 'edit'; vehicle: Vehicle }
  | { type: 'pair'; vehicle: Vehicle }
  | { type: 'unpair'; vehicle: Vehicle }
  | { type: 'deactivate'; vehicle: Vehicle }
  | null

export function VehiclesPage() {
  const [includeInactive, setIncludeInactive] = useState(false)
  const vehicles = useApiData(`vehicles:${includeInactive}`, (signal) => fetchVehicles(includeInactive, signal))
  const drivers = useApiData('drivers:active', (signal) => fetchDrivers(false, signal))
  const [dialog, setDialog] = useState<DialogState>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)

  function clearMessages() {
    setNotice(null)
    setPageError(null)
  }

  function done(message: string) {
    setPageError(null)
    setNotice(message)
    vehicles.reload()
  }

  return (
    <section aria-labelledby="vehicles-title">
      <div className="page-header">
        <h1 id="vehicles-title">차량</h1>
        <div className="page-header__actions">
          <label className="checkbox">
            <input type="checkbox" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
            비활성 차량 포함
          </label>
          <button
            type="button"
            className="button button--primary"
            onClick={() => {
              clearMessages()
              setDialog({ type: 'create' })
            }}
          >
            차량 추가
          </button>
        </div>
      </div>

      {notice && <Alert kind="success">{notice}</Alert>}
      {pageError && <Alert kind="error">{pageError}</Alert>}
      <LoadingState loading={vehicles.loading && !vehicles.data} error={vehicles.error} onRetry={vehicles.reload} />

      {vehicles.data && vehicles.data.length === 0 && <p className="empty">등록된 차량이 없습니다.</p>}
      {vehicles.data && vehicles.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">자동차등록번호</th>
                <th scope="col">차종</th>
                <th scope="col">기본 운전자</th>
                <th scope="col">상태</th>
                <th scope="col">차량 앱 연결</th>
                <th scope="col">관리</th>
              </tr>
            </thead>
            <tbody>
              {vehicles.data.map((vehicle) => (
                <tr key={vehicle.id} data-testid={`vehicle-row-${vehicle.id}`} className={vehicle.active ? undefined : 'row--inactive'}>
                  <th scope="row">{vehicle.regNo}</th>
                  <td>{vehicle.modelName}</td>
                  <td>{vehicle.defaultDriver ? describeDriver(vehicle.defaultDriver) : '-'}</td>
                  <td>{vehicle.active ? '사용 중' : <span className="badge">비활성</span>}</td>
                  <td>
                    {vehicle.device.paired ? (
                      <>
                        <span className="badge badge--ok">연결됨</span>
                        <div className="muted small">페어링 {formatDateTime(vehicle.device.pairedAt)}</div>
                        <div className="muted small">마지막 동기화 {formatDateTime(vehicle.device.lastSyncedAt)}</div>
                      </>
                    ) : (
                      <span className="muted">연결 안 됨</span>
                    )}
                  </td>
                  <td>
                    <div className="actions">
                      <button
                        type="button"
                        className="button button--small"
                        onClick={() => {
                          clearMessages()
                          setDialog({ type: 'edit', vehicle })
                        }}
                        aria-label={`${vehicle.regNo} 수정`}
                      >
                        수정
                      </button>
                      {vehicle.active && (
                        <button
                          type="button"
                          className="button button--small"
                          onClick={() => {
                            clearMessages()
                            setDialog({ type: 'pair', vehicle })
                          }}
                          aria-label={`${vehicle.regNo} ${vehicle.device.paired ? '다시 페어링' : '페어링'}`}
                        >
                          {vehicle.device.paired ? '다시 페어링' : '페어링'}
                        </button>
                      )}
                      {vehicle.device.paired && (
                        <button
                          type="button"
                          className="button button--small button--danger-outline"
                          onClick={() => {
                            clearMessages()
                            setDialog({ type: 'unpair', vehicle })
                          }}
                          aria-label={`${vehicle.regNo} 연결 해제`}
                        >
                          연결 해제
                        </button>
                      )}
                      {vehicle.active ? (
                        <button
                          type="button"
                          className="button button--small button--danger-outline"
                          onClick={() => {
                            clearMessages()
                            setDialog({ type: 'deactivate', vehicle })
                          }}
                          aria-label={`${vehicle.regNo} 비활성`}
                        >
                          비활성
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="button button--small"
                          onClick={async () => {
                            clearMessages()
                            try {
                              await updateVehicle(vehicle.id, { active: true })
                              done(`${vehicle.regNo} 차량을 다시 사용합니다.`)
                            } catch (err) {
                              setPageError(errorMessage(err))
                            }
                          }}
                          aria-label={`${vehicle.regNo} 다시 사용`}
                        >
                          다시 사용
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(dialog?.type === 'create' || dialog?.type === 'edit') && (
        <VehicleFormDialog
          vehicle={dialog.type === 'edit' ? dialog.vehicle : undefined}
          drivers={drivers.data ?? []}
          onClose={() => setDialog(null)}
          onSaved={(vehicle, created) => {
            setDialog(null)
            done(created ? `${vehicle.regNo} 차량을 추가했습니다.` : `${vehicle.regNo} 차량 정보를 저장했습니다.`)
          }}
        />
      )}

      {dialog?.type === 'pair' && (
        <PairDialog
          vehicle={dialog.vehicle}
          onClose={() => setDialog(null)}
          onPaired={(result) => {
            setDialog(null)
            done(
              result.replacedDeviceId !== null
                ? `${dialog.vehicle.regNo} 차량을 페어링했습니다. 기존 기기는 연결이 해제되었습니다.`
                : `${dialog.vehicle.regNo} 차량을 페어링했습니다.`,
            )
          }}
        />
      )}

      {dialog?.type === 'unpair' && (
        <ConfirmDialog
          title="차량 앱 연결 해제"
          confirmLabel="연결 해제"
          danger
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await unpairDevice(dialog.vehicle.id)
            done(`${dialog.vehicle.regNo} 차량의 연결을 해제했습니다.`)
          }}
        >
          <p>
            <strong>{dialog.vehicle.regNo}</strong> 차량의 기기 연결을 해제할까요?
          </p>
          <p className="muted">
            해제하면 차량 앱이 더 이상 운행 기록을 올릴 수 없습니다. 아직 전송되지 않은 기록은 차량에 남아 있으며, 다시 페어링해야
            올라옵니다.
          </p>
        </ConfirmDialog>
      )}

      {dialog?.type === 'deactivate' && (
        <ConfirmDialog
          title="차량 비활성"
          confirmLabel="비활성으로 바꾸기"
          danger
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await updateVehicle(dialog.vehicle.id, { active: false })
            done(`${dialog.vehicle.regNo} 차량을 비활성으로 바꿨습니다.`)
          }}
        >
          <p>
            <strong>{dialog.vehicle.regNo}</strong> 차량을 비활성으로 바꿀까요?
          </p>
          <p className="muted">과거 운행 기록은 그대로 남습니다. 연결된 차량 앱 기기도 함께 해제됩니다.</p>
        </ConfirmDialog>
      )}
    </section>
  )
}

interface VehicleFormDialogProps {
  vehicle?: Vehicle
  drivers: Driver[]
  onClose: () => void
  onSaved: (vehicle: Vehicle, created: boolean) => void
}

function VehicleFormDialog({ vehicle, drivers, onClose, onSaved }: VehicleFormDialogProps) {
  const [regNo, setRegNo] = useState(vehicle?.regNo ?? '')
  const [modelName, setModelName] = useState(vehicle?.modelName ?? '')
  const [defaultDriverId, setDefaultDriverId] = useState(vehicle?.defaultDriver ? String(vehicle.defaultDriver.id) : '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const driverOptions = [...drivers]
  if (vehicle?.defaultDriver && !driverOptions.some((d) => d.id === vehicle.defaultDriver?.id)) {
    driverOptions.unshift({ ...vehicle.defaultDriver, active: false })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = {
      regNo: regNo.trim(),
      modelName: modelName.trim(),
      defaultDriverId: defaultDriverId ? Number(defaultDriverId) : null,
    }
    if (!body.regNo || !body.modelName) {
      setError('자동차등록번호와 차종을 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const saved = vehicle ? await updateVehicle(vehicle.id, body) : await createVehicle(body)
      onSaved(saved, !vehicle)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={vehicle ? '차량 수정' : '차량 추가'} onClose={onClose} busy={busy}>
      <form className="form" onSubmit={handleSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="vehicle-regno">자동차등록번호</label>
          <input id="vehicle-regno" required maxLength={20} value={regNo} onChange={(e) => setRegNo(e.target.value)} placeholder="12가3456" />
        </div>
        <div className="field">
          <label htmlFor="vehicle-model">차종</label>
          <input id="vehicle-model" required maxLength={50} value={modelName} onChange={(e) => setModelName(e.target.value)} placeholder="아이오닉 5" />
        </div>
        <div className="field">
          <label htmlFor="vehicle-driver">기본 운전자</label>
          <select id="vehicle-driver" value={defaultDriverId} onChange={(e) => setDefaultDriverId(e.target.value)}>
            <option value="">없음</option>
            {driverOptions.map((d) => (
              <option key={d.id} value={String(d.id)}>
                {describeDriver(d)}
                {d.active ? '' : ' (비활성)'}
              </option>
            ))}
          </select>
        </div>
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

const PAIRING_CODE_PATTERN = /^\d{6}$/

interface PairDialogProps {
  vehicle: Vehicle
  onClose: () => void
  onPaired: (result: PairResponse) => void
}

function PairDialog({ vehicle, onClose, onPaired }: PairDialogProps) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!PAIRING_CODE_PATTERN.test(code)) {
      setError('차량 화면에 표시된 숫자 6자리를 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const result = await pairDevice(vehicle.id, code)
      onPaired(result)
    } catch (err) {
      // PAIRING_CODE_INVALID / PAIRING_CODE_EXPIRED / RATE_LIMITED 등 서버 메시지 표시
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={`${vehicle.regNo} 페어링`} onClose={onClose} busy={busy}>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <p className="muted">
          차량 앱의 페어링 화면에 표시된 6자리 코드를 입력하세요. 코드는 10분 동안 한 번만 쓸 수 있습니다.
        </p>
        {vehicle.device.paired && (
          <Alert kind="warning">
            이 차량에는 이미 연결된 기기가 있습니다. 새 코드로 페어링하면 기존 기기는 연결이 해제되어 더 이상 운행 기록을 올릴 수
            없습니다.
          </Alert>
        )}
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="pairing-code">페어링 코드 (숫자 6자리)</label>
          <input
            id="pairing-code"
            className="code-input"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="\d{6}"
            maxLength={6}
            required
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\s/g, ''))}
            aria-invalid={error ? true : undefined}
          />
        </div>
        <div className="modal__actions">
          <button type="button" className="button" onClick={onClose} disabled={busy}>
            취소
          </button>
          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? '페어링 중…' : vehicle.device.paired ? '기존 기기 교체하고 페어링' : '페어링'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
