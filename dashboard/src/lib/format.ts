// 표시 형식. 시각은 모두 Asia/Seoul 기준
const TIME_ZONE = 'Asia/Seoul'

const dateTimeFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const dateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const kmFormatter = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

function parts(formatter: Intl.DateTimeFormat, date: Date): Record<string, string> {
  const result: Record<string, string> = {}
  for (const part of formatter.formatToParts(date)) result[part.type] = part.value
  return result
}

function parseDate(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

/** `2026-11-03 08:12` (Asia/Seoul). 값이 없으면 '-' */
export function formatDateTime(iso: string | null | undefined): string {
  const date = parseDate(iso)
  if (!date) return '-'
  const p = parts(dateTimeFormatter, date)
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`
}

/** `08:12` (Asia/Seoul). 값이 없으면 null */
export function formatTime(iso: string | null | undefined): string | null {
  const date = parseDate(iso)
  if (!date) return null
  const p = parts(dateTimeFormatter, date)
  return `${p.hour}:${p.minute}`
}

/** `2026-11-03 (화)` — tripDate는 이미 Asia/Seoul 날짜 */
export function formatTripDate(tripDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(tripDate)
  if (!match) return tripDate
  const weekday = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))).getUTCDay()
  return `${tripDate} (${WEEKDAYS[weekday]})`
}

/** `12,034.5` */
export function formatKm(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return kmFormatter.format(value)
}

/** `82.6%`. null이면 '-' (총주행거리 0) */
export function formatRatio(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return `${value.toFixed(1)}%`
}

/** 오늘 날짜 `YYYY-MM-DD` (Asia/Seoul) */
export function todayInSeoul(now: Date = new Date()): string {
  const p = parts(dateFormatter, now)
  return `${p.year}-${p.month}-${p.day}`
}

/** 이번 달 `YYYY-MM` (Asia/Seoul) */
export function currentMonthInSeoul(now: Date = new Date()): string {
  return todayInSeoul(now).slice(0, 7)
}
