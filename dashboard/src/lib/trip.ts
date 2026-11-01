import type { BusinessCategory, DriverRef, Purpose, TripEndpoint } from '../api/types'
import { formatTime } from './format'
import { BUSINESS_CATEGORY_LABEL, ENDPOINT_TYPE_LABEL, PURPOSE_LABEL, labelOf } from './labels'

export const HIDDEN_PLACE = '장소 비공개'

/**
 * 출발·도착 표시 (rules §7)
 * - 라벨이 있으면 라벨 (미확인 운행의 회사·거래처 라벨은 공개)
 * - 마스킹으로 가려졌으면 '장소 비공개'
 */
export function describePlace(endpoint: TripEndpoint | null, masked: boolean): string {
  if (endpoint?.label) return endpoint.label
  if (masked) return HIDDEN_PLACE
  if (endpoint) return ENDPOINT_TYPE_LABEL[endpoint.type] ?? endpoint.type
  return '-'
}

/** `08:12~08:47`. 시각이 가려졌으면 null (시:분 숨김) */
export function describeTimeRange(startedAt: string | null, endedAt: string | null): string | null {
  const start = formatTime(startedAt)
  const end = formatTime(endedAt)
  if (!start && !end) return null
  return `${start ?? '?'}~${end ?? '?'}`
}

export function describePurpose(purpose: Purpose, businessCategory: BusinessCategory | null): string {
  const label = labelOf(PURPOSE_LABEL, purpose)
  return businessCategory ? `${label} · ${labelOf(BUSINESS_CATEGORY_LABEL, businessCategory)}` : label
}

export function describeDriver(driver: DriverRef | null): string {
  return driver ? `${driver.name} (${driver.dept})` : '미지정'
}
