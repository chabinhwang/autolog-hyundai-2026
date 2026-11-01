import { useCallback, useEffect, useRef, useState } from 'react'
import { isAbortError } from '../api/client'

export interface ApiData<T> {
  /** 마지막으로 받은 값 (다시 불러오는 중에도 이전 값 유지) */
  data: T | undefined
  error: unknown
  loading: boolean
  reload: () => void
}

interface Settled<T> {
  requestKey: string
  data: T | undefined
  error: unknown
}

/**
 * key가 바뀔 때마다 loader를 다시 호출합니다. 이전 요청은 취소(AbortController)합니다.
 * loader는 최신 값을 쓰도록 ref로 보관하므로 key에 조회 조건을 모두 담아야 합니다.
 */
export function useApiData<T>(key: string, loader: (signal: AbortSignal) => Promise<T>): ApiData<T> {
  const loaderRef = useRef(loader)
  useEffect(() => {
    loaderRef.current = loader
  })

  const [version, setVersion] = useState(0)
  const requestKey = `${version}:${key}`
  const [settled, setSettled] = useState<Settled<T>>({ requestKey: '', data: undefined, error: null })

  useEffect(() => {
    const controller = new AbortController()
    loaderRef.current(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setSettled({ requestKey, data, error: null })
      },
      (error: unknown) => {
        if (controller.signal.aborted || isAbortError(error)) return
        setSettled((prev) => ({ requestKey, data: prev.data, error }))
      },
    )
    return () => controller.abort()
  }, [requestKey])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const current = settled.requestKey === requestKey
  return { data: settled.data, error: current ? settled.error : null, loading: !current, reload }
}
