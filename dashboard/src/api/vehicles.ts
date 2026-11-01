import { apiRequest } from './client'
import type { PairResponse, UnpairResponse, Vehicle, VehicleInput } from './types'

export function fetchVehicles(includeInactive: boolean, signal?: AbortSignal): Promise<Vehicle[]> {
  return apiRequest<Vehicle[]>('/vehicles', { query: { includeInactive }, signal })
}

export function createVehicle(body: VehicleInput): Promise<Vehicle> {
  return apiRequest<Vehicle>('/vehicles', { method: 'POST', body })
}

/** 보낸 필드만 바뀝니다. active: false면 연결된 기기도 폐기 */
export function updateVehicle(id: number, body: Partial<VehicleInput> & { active?: boolean }): Promise<Vehicle> {
  return apiRequest<Vehicle>(`/vehicles/${id}`, { method: 'PATCH', body })
}

export function pairDevice(vehicleId: number, code: string): Promise<PairResponse> {
  return apiRequest<PairResponse>('/devices/pair', { method: 'POST', body: { code, vehicleId } })
}

export function unpairDevice(vehicleId: number): Promise<UnpairResponse> {
  return apiRequest<UnpairResponse>(`/vehicles/${vehicleId}/device`, { method: 'DELETE' })
}
