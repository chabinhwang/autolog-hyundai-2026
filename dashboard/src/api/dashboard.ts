import { apiRequest } from './client'
import type { DashboardSummary } from './types'

export function fetchSummary(month: string, signal?: AbortSignal): Promise<DashboardSummary> {
  return apiRequest<DashboardSummary>('/dashboard/summary', { query: { month }, signal })
}
