import { useEffect, useState } from 'react'
import { fetchServerStatus, type ServerStatus } from './api/health'
import './App.css'

const STATUS_LABEL: Record<ServerStatus, string> = {
  checking: '서버 확인 중',
  up: '서버 연결됨',
  down: '서버 연결 안 됨',
}

function App() {
  const [status, setStatus] = useState<ServerStatus>('checking')

  useEffect(() => {
    const controller = new AbortController()
    fetchServerStatus(controller.signal)
      .then(setStatus)
      .catch(() => {
        // 언마운트로 요청이 취소된 경우: 상태를 바꾸지 않습니다.
      })
    return () => controller.abort()
  }, [])

  return (
    <main className="app">
      <header className="app__header">
        <h1>AutoLog 관리자</h1>
        <span className={`status status--${status}`}>{STATUS_LABEL[status]}</span>
      </header>
      <p className="app__lead">차량이 기록한 운행일지를 모아 업무사용비율 확인, 월 마감, 기록부 내보내기를 합니다.</p>
    </main>
  )
}

export default App
