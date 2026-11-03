import { errorMessage } from '../lib/errors'
import { Alert } from './Alert'

/** 목록 화면 공통: 불러오는 중·오류 표시 */
export function LoadingState({ loading, error, onRetry }: { loading: boolean; error: unknown; onRetry?: () => void }) {
  if (error) {
    return (
      <Alert kind="error">
        <span>{errorMessage(error)}</span>
        {onRetry && (
          <button type="button" className="button button--small alert__action" onClick={onRetry}>
            다시 시도
          </button>
        )}
      </Alert>
    )
  }
  if (loading) {
    return (
      <p className="loading" role="status">
        <span className="spinner" aria-hidden="true" />
        불러오는 중…
      </p>
    )
  }
  return null
}
