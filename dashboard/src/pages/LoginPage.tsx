import { useState, type FormEvent } from 'react'
import { login } from '../api/auth'
import { useAuth } from '../auth/context'
import { Alert } from '../components/Alert'
import { LogoMark } from '../components/Icon'
import { errorMessage } from '../lib/errors'
import { isSafeInternalPath, navigate, useLocation } from '../router/router'

export function LoginPage() {
  const { completeLogin } = useAuth()
  const { searchParams } = useLocation()
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const expired = searchParams.get('reason') === 'expired'
  const next = searchParams.get('next')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!loginId.trim() || !password) {
      setError('아이디와 비밀번호를 입력해 주세요.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const response = await login({ loginId: loginId.trim(), password })
      completeLogin(response)
      navigate(isSafeInternalPath(next) ? next : '/', { replace: true })
    } catch (err) {
      // INVALID_CREDENTIALS·RATE_LIMITED·VALIDATION_FAILED는 서버 메시지를 그대로 표시
      setError(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <main className="login">
      <div className="login__brand">
        <div className="login__brand-top">
          <LogoMark size={40} />
          <span className="login__wordmark">AutoLog</span>
        </div>
        <div className="login__hero">
          <p className="login__eyebrow">업무용승용차 운행기록부</p>
          <p className="login__headline">
            차가 쓰고,
            <br />
            한마디로 끝내는 운행일지
          </p>
          <ul className="login__points">
            <li>계기판 주행거리·시각은 차량이 직접 기록해 고칠 수 없습니다</li>
            <li>업무 여부는 도착할 때 음성으로 한 번만 확인합니다</li>
            <li>개인 운행은 장소와 시각을 가려 직원의 사생활을 지킵니다</li>
          </ul>
        </div>
        <p className="login__foot">Pleos Connect 차량 앱 · 관리자 대시보드</p>
      </div>
      <div className="login__panel">
      <form className="login__card" onSubmit={handleSubmit} noValidate aria-labelledby="login-title">
        <h1 id="login-title" className="login__title">
          관리자 로그인
        </h1>
        <p className="muted">회사 관리자 계정으로 로그인해 주세요.</p>
        {expired && !error && <Alert kind="warning">로그인이 만료되었습니다. 다시 로그인해 주세요.</Alert>}
        {error && <Alert kind="error">{error}</Alert>}
        <div className="field">
          <label htmlFor="login-id">아이디 (이메일)</label>
          <input
            id="login-id"
            type="email"
            autoComplete="username"
            value={loginId}
            onChange={(event) => setLoginId(event.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">비밀번호</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            maxLength={100}
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </div>
        <button type="submit" className="button button--primary button--block" disabled={submitting}>
          {submitting ? '로그인 중…' : '로그인'}
        </button>
      </form>
      </div>
    </main>
  )
}
