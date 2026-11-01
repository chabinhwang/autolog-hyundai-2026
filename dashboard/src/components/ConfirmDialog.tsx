import { useState, type ReactNode } from 'react'
import { errorMessage } from '../lib/errors'
import { Alert } from './Alert'
import { Modal } from './Modal'

interface ConfirmDialogProps {
  title: string
  children: ReactNode
  confirmLabel: string
  danger?: boolean
  /** 실패하면 대화상자 안에 오류를 보여 줍니다. 성공하면 onClose 호출 */
  onConfirm: () => Promise<void>
  onClose: () => void
}

export function ConfirmDialog({ title, children, confirmLabel, danger = false, onConfirm, onClose }: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      onClose()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose} busy={busy}>
      <div className="modal__body">{children}</div>
      {error && <Alert kind="error">{error}</Alert>}
      <div className="modal__actions">
        <button type="button" className="button" onClick={onClose} disabled={busy}>
          취소
        </button>
        <button
          type="button"
          className={danger ? 'button button--danger' : 'button button--primary'}
          onClick={handleConfirm}
          disabled={busy}
        >
          {busy ? '처리 중…' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
