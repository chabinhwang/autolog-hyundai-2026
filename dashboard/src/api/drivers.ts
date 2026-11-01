import { apiRequest } from './client'
import type { Driver, DriverInput } from './types'

export function fetchDrivers(includeInactive: boolean, signal?: AbortSignal): Promise<Driver[]> {
  return apiRequest<Driver[]>('/drivers', { query: { includeInactive }, signal })
}

export function createDriver(body: DriverInput): Promise<Driver> {
  return apiRequest<Driver>('/drivers', { method: 'POST', body })
}

export function updateDriver(id: number, body: Partial<DriverInput> & { active?: boolean }): Promise<Driver> {
  return apiRequest<Driver>(`/drivers/${id}`, { method: 'PATCH', body })
}
