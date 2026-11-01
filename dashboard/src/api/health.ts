export type ServerStatus = 'checking' | 'up' | 'down'

interface HealthResponse {
  status: string
}

export async function fetchServerStatus(signal?: AbortSignal): Promise<ServerStatus> {
  try {
    const response = await fetch('/actuator/health', { signal })
    if (!response.ok) {
      return 'down'
    }
    const body = (await response.json()) as HealthResponse
    return body.status === 'UP' ? 'up' : 'down'
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    return 'down'
  }
}
