// 열거값 한국어 라벨 (database.md 열거값)
import type { ActorType, BusinessCategory, EndpointType, PlaceType, Purpose, PurposeSource } from '../api/types'

export const PURPOSE_LABEL: Record<Purpose, string> = {
  COMMUTE: '출퇴근',
  BUSINESS: '업무',
  PERSONAL: '개인',
  UNCONFIRMED: '미확인',
}

export const PURPOSE_SOURCE_LABEL: Record<PurposeSource, string> = {
  AUTO: '자동 추정',
  VOICE: '음성 확인',
  MANUAL: '차량 화면 선택',
  ADMIN: '관리자 수정',
}

export const BUSINESS_CATEGORY_LABEL: Record<BusinessCategory, string> = {
  VISIT: '거래처 방문',
  MEETING: '회의',
  PROMOTION: '판촉',
  TRAINING: '교육',
  OTHER: '기타',
}

export const PLACE_TYPE_LABEL: Record<PlaceType, string> = {
  COMPANY: '회사',
  CLIENT: '거래처',
}

export const ENDPOINT_TYPE_LABEL: Record<EndpointType, string> = {
  HOME: '집',
  COMPANY: '회사',
  CLIENT: '거래처',
  UNREGISTERED: '미등록 장소',
}

export const ACTOR_TYPE_LABEL: Record<ActorType, string> = {
  DEVICE: '차량',
  ADMIN: '관리자',
}

export const EDIT_FIELD_LABEL: Record<string, string> = {
  purpose: '용도',
  purposeSource: '판정 출처',
  businessCategory: '업무 세부',
  note: '메모',
  driverId: '직원',
}

export const WARNING_LABEL: Record<string, { label: string; description: string }> = {
  RATIO_HIGH: { label: '업무비율 높음', description: '업무사용비율이 95% 이상입니다.' },
  UNCONFIRMED_EXISTS: { label: '미확인 운행 있음', description: '용도가 확인되지 않은 운행이 있습니다.' },
  SYNC_DELAYED: { label: '동기화 지연', description: '마지막 동기화 후 48시간이 지났습니다.' },
  ODO_GAP: { label: '계기판 거리 불연속', description: '직전 운행의 주행 후 거리와 다음 운행의 주행 전 거리가 1km 넘게 다릅니다.' },
}

export const PURPOSES: Purpose[] = ['COMMUTE', 'BUSINESS', 'PERSONAL', 'UNCONFIRMED']
export const BUSINESS_CATEGORIES: BusinessCategory[] = ['VISIT', 'MEETING', 'PROMOTION', 'TRAINING', 'OTHER']

/** 라벨 표에 없는 값은 원래 코드를 그대로 보여 줍니다. */
export function labelOf<K extends string>(table: Record<K, string>, value: K | string | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return (table as Record<string, string>)[value] ?? value
}
