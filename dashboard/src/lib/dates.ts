// 날짜 문자열(YYYY-MM-DD, YYYY-MM) 계산

export const MAX_RANGE_DAYS = 366

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/
const MONTH_PATTERN = /^(\d{4})-(\d{2})$/

function toUtc(date: string): number | null {
  const match = DATE_PATTERN.exec(date)
  if (!match) return null
  const time = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  // 2026-02-30 같은 없는 날짜 거르기
  const check = new Date(time)
  if (check.getUTCMonth() !== Number(match[2]) - 1 || check.getUTCDate() !== Number(match[3])) return null
  return time
}

export function isValidDate(date: string): boolean {
  return toUtc(date) !== null
}

export function isValidMonth(month: string): boolean {
  const match = MONTH_PATTERN.exec(month)
  return !!match && Number(match[2]) >= 1 && Number(match[2]) <= 12
}

/** from~to 날짜 포함 일수. 형식이 틀리면 null */
export function inclusiveDays(from: string, to: string): number | null {
  const a = toUtc(from)
  const b = toUtc(to)
  if (a === null || b === null) return null
  return Math.round((b - a) / 86_400_000) + 1
}

/** 월의 첫날·마지막 날 */
export function monthRange(month: string): { from: string; to: string } {
  const [year, mon] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, mon, 0)).getUTCDate()
  return { from: `${month}-01`, to: `${month}-${String(lastDay).padStart(2, '0')}` }
}

/**
 * 조회 기간 검증. 오류 문구 또는 null
 * 서버 계약은 '최대 366일'이므로 날짜 포함 366일까지 허용합니다.
 */
export function validateRange(from: string, to: string): string | null {
  if (!isValidDate(from) || !isValidDate(to)) return '시작일과 종료일을 YYYY-MM-DD 형식으로 입력해 주세요.'
  const days = inclusiveDays(from, to)
  if (days === null || days < 1) return '종료일은 시작일과 같거나 이후여야 합니다.'
  if (days > MAX_RANGE_DAYS) return `기간은 최대 ${MAX_RANGE_DAYS}일까지 조회할 수 있습니다. (현재 ${days}일)`
  return null
}
