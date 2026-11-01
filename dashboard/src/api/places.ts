import { apiRequest } from './client'
import type { Place, PlaceInput, PlaceType } from './types'

export function fetchPlaces(type: PlaceType | undefined, signal?: AbortSignal): Promise<Place[]> {
  return apiRequest<Place[]>('/places', { query: { type }, signal })
}

export function createPlace(body: PlaceInput): Promise<Place> {
  return apiRequest<Place>('/places', { method: 'POST', body })
}

export function updatePlace(id: number, body: Partial<PlaceInput>): Promise<Place> {
  return apiRequest<Place>(`/places/${id}`, { method: 'PATCH', body })
}

export function deletePlace(id: number): Promise<{ id: number; deleted: boolean }> {
  return apiRequest<{ id: number; deleted: boolean }>(`/places/${id}`, { method: 'DELETE' })
}
