import { useState, type FormEvent } from 'react'
import { createDriver, fetchDrivers, updateDriver } from '../api/drivers'
import type { Driver } from '../api/types'
import { Alert } from '../components/Alert'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { LoadingState } from '../components/LoadingState'
import { Modal } from '../components/Modal'
import { useApiData } from '../hooks/useApiData'
import { errorMessage } from '../lib/errors'

type DialogState = { type: 'create' } | { type: 'edit'; driver: Driver } | { type: 'deactivate'; driver: Driver } | null

export function DriversPage() {
  const [includeInactive, setIncludeInactive] = useState(false)
  const drivers = useApiData(`drivers:${includeInactive}`, (signal) => fetchDrivers(includeInactive, signal))
  const [dialog, setDialog] = useState<DialogState>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [pageError, setPageError] = useState<string | null>(null)

  function open(next: DialogState) {
    setNotice(null)
    setPageError(null)
    setDialog(next)
  }

  function done(message: string) {
    setDialog(null)
    setPageError(null)
    setNotice(message)
    drivers.reload()
  }

  async function reactivate(driver: Driver) {
    setNotice(null)
    setPageError(null)
    try {
      await updateDriver(driver.id, { active: true })
      done(`${driver.name} 직원을 다시 사용합니다.`)
    } catch (err) {
      setPageError(errorMessage(err))
    }
  }

  return (
    <section aria-labelledby="drivers-title">
      <div className="page-header">
        <h1 id="drivers-title">직원</h1>
        <div className="page-header__actions">
          <label className="checkbox">
            <input type="checkbox" checked={includeInactive} onChange={(e) => setIncludeInactive(e.target.checked)} />
            비활성 직원 포함
          </label>
          <button type="button" className="button button--primary" onClick={() => open({ type: 'create' })}>
            직원 추가
          </button>
        </div>
      </div>
      <p className="muted">직원은 차량 이용자입니다. 운행기록부의 부서·성명으로 쓰이며, 차량 앱의 이용자 선택 목록에 내려갑니다.</p>

      {notice && <Alert kind="success">{notice}</Alert>}
      {pageError && <Alert kind="error">{pageError}</Alert>}
      <LoadingState loading={drivers.loading && !drivers.data} error={drivers.error} onRetry={drivers.reload} />

      {drivers.data && drivers.data.length === 0 && <p className="empty">등록된 직원이 없습니다.</p>}
      {drivers.data && drivers.data.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">부서</th>
                <th scope="col">성명</th>
                <th scope="col">상태</th>
                <th scope="col">관리</th>
              </tr>
            </thead>
            <tbody>
              {drivers.data.map((driver) => (
                <tr key={driver.id} className={driver.active ? undefined : 'row--inactive'}>
                  <td>{driver.dept}</td>
                  <th scope="row">{driver.name}</th>
                  <td>{driver.active ? '사용 중' : <span className="badge">비활성</span>}</td>
                  <td>
                    <div className="actions">
                      <button
                        type="button"
                        className="button button--small"
                        onClick={() => open({ type: 'edit', driver })}
                        aria-label={`${driver.name} 수정`}
                      >
                        수정
                      </button>
                      {driver.active ? (
                        <button
                          type="button"
                          className="button button--small button--danger-outline"
                          onClick={() => open({ type: 'deactivate', driver })}
                          aria-label={`${driver.name} 비활성`}
                        >
                          비활성
                        </button>
                      ) : (
                        <button
                          type="button"
                          className="button button--small"
                          onClick={() => reactivate(driver)}
                          aria-label={`${driver.name} 다시 사용`}
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
        <DriverFormDialog
          driver={dialog.type === 'edit' ? dialog.driver : undefined}
          onClose={() => setDialog(null)}
          onSaved={(driver, created) => done(created ? `${driver.name} 직원을 추가했습니다.` : `${driver.name} 직원 정보를 저장했습니다.`)}
        />
      )}

      {dialog?.type === 'deactivate' && (
        <ConfirmDialog
          title="직원 비활성"
          confirmLabel="비활성으로 바꾸기"
          danger
          onClose={() => setDialog(null)}
          onConfirm={async () => {
            await updateDriver(dialog.driver.id, { active: false })
            done(`${dialog.driver.name} 직원을 비활성으로 바꿨습니다.`)
          }}
        >
          <p>
            <strong>
              {dialog.driver.name} ({dialog.driver.dept})
            </strong>{' '}
            직원을 비활성으로 바꿀까요?
          </p>
          <p className="muted">
            차량 앱의 이용자 선택 목록에서 빠지고, 기본 운전자로 지정된 차량은 기본 운전자가 해제됩니다. 과거 운행 기록의 직원 정보는
            유지됩니다.
          </p>
        </ConfirmDialog>
      )}
    </section>
  )
}

interface DriverFormDialogProps {
  driver?: Driver
  onClose: () => void
  onSaved: (driver: Driver, created: boolean) => void
}

function DriverFormDialog({ driver, onClose, onSaved }: DriverFormDialogProps) {
  const [dept, setDept] = useState(driver?.dept ?? '')
  const [name, setName] = useState(driver?.name ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body = { dept: dept.trim(), name: name.trim() }
    if (!body.dept || !body.name) {
      setError('부서와 성명을 입력해 주세요.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const saved = driver ? await updateDriver(driver.id, body) : await createDriver(body)
      onSaved(saved, !driver)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={driver ? '직원 수정' : '직원 추가'} onClose={onClose} busy={busy}>
      <form className="form" onSubmit={handleSubmit} noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="driver-dept">부서</label>
          <input id="driver-dept" required maxLength={50} value={dept} onChange={(e) => setDept(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="driver-name">성명</label>
          <input id="driver-name" required maxLength={30} value={name} onChange={(e) => setName(e.target.value)} />
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
