import { apiRequest } from './client'
import type { AdminTripEditRequest, Page, TripDetail, TripEditResponse, TripListItem, TripListQuery } from './types'

export function fetchTrips(query: TripListQuery, signal?: AbortSignal): Promise<Page<TripListItem>> {
  return apiRequest<Page<TripListItem>>('/trips', { query: { ...query }, signal })
}

export function fetchTrip(clientUuid: string, signal?: AbortSignal): Promise<TripDetail> {
  return apiRequest<TripDetail>(`/trips/${encodeURIComponent(clientUuid)}`, { signal })
}

export function editTrip(clientUuid: string, body: AdminTripEditRequest): Promise<TripEditResponse> {
  return apiRequest<TripEditResponse>(`/trips/${encodeURIComponent(clientUuid)}/edits`, { method: 'POST', body })
}
