import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchLogbook } from '../api/reports'
import { getToken, setToken } from '../auth/tokenStorage'
import { defaultLogbookFilename, parseContentDispositionFilename } from '../lib/contentDisposition'
import vehicleNotFound from './fixtures/reports-logbook-vehicle-not-found.json'
import meUnauthorized from './fixtures/auth-me-unauthorized.json'
import { mockFetch } from './mockFetch'

// reports.md의 응답 헤더 예시 그대로
const CONTRACT_HEADER =
  "attachment; filename=\"logbook.xlsx\"; filename*=UTF-8''%EC%9A%B4%ED%96%89%EA%B8%B0%EB%A1%9D%EB%B6%80_12%EA%B0%803456_2026-01-01_2026-12-31.xlsx"
const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

describe('Content-Disposition 파일명 해석', () => {
  it('계약 예시의 filename*(RFC 5987)을 UTF-8로 해석한다', () => {
    expect(parseContentDispositionFilename(CONTRACT_HEADER)).toBe('운행기록부_12가3456_2026-01-01_2026-12-31.xlsx')
  })

  it('대소문자·따옴표·순서가 달라도 읽는다', () => {
    expect(parseContentDispositionFilename(`attachment; FILENAME*="utf-8'ko'%ED%95%9C.xlsx"; filename="a.xlsx"`)).toBe('한.xlsx')
  })

  it('filename*이 없으면 null (filename만 있어도 사용하지 않음)', () => {
    expect(parseContentDispositionFilename('attachment; filename="logbook.xlsx"')).toBeNull()
    expect(parseContentDispositionFilename(null)).toBeNull()
  })

  it('잘못된 인코딩·지원하지 않는 문자셋은 null', () => {
    expect(parseContentDispositionFilename("attachment; filename*=UTF-8''%E0%A4%A")).toBeNull()
    expect(parseContentDispositionFilename("attachment; filename*=EUC-KR''%C7%D1.xlsx")).toBeNull()
  })

  it('경로 구분자는 지운다', () => {
    expect(parseContentDispositionFilename("attachment; filename*=UTF-8''..%2F..%2Fevil.xlsx")).toBe('.._.._evil.xlsx')
  })

  it('기본 파일명은 운행기록부_{regNo}_{from}_{to}.xlsx', () => {
    expect(defaultLogbookFilename('12가3456', '2026-01-01', '2026-12-31')).toBe('운행기록부_12가3456_2026-01-01_2026-12-31.xlsx')
  })
})

describe('기록부 XLSX 받기', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const query = { vehicleId: 3, regNo: '12가3456', from: '2026-01-01', to: '2026-12-31' }

  it('Authorization 헤더로 요청하고 filename*의 파일명을 쓴다', async () => {
    setToken('jwt-token')
    const mock = mockFetch({
      'GET /api/v1/reports/logbook': {
        raw: new Uint8Array([0x50, 0x4b, 0x03, 0x04]),
        headers: { 'Content-Type': XLSX_TYPE, 'Content-Disposition': CONTRACT_HEADER },
      },
    })

    const file = await fetchLogbook(query)

    expect(file.filename).toBe('운행기록부_12가3456_2026-01-01_2026-12-31.xlsx')
    expect(file.blob.size).toBe(4)
    const [request] = mock.calls
    expect(request.headers.get('Authorization')).toBe('Bearer jwt-token')
    expect(Object.fromEntries(request.query)).toEqual({ vehicleId: '3', from: '2026-01-01', to: '2026-12-31', format: 'xlsx' })
  })

  it('Content-Disposition을 읽을 수 없으면(CORS 미노출 등) 기본 파일명을 쓴다', async () => {
    mockFetch({ 'GET /api/v1/reports/logbook': { raw: new Uint8Array([1]), headers: { 'Content-Type': XLSX_TYPE } } })
    const file = await fetchLogbook(query)
    expect(file.filename).toBe('운행기록부_12가3456_2026-01-01_2026-12-31.xlsx')
  })

  it('실패 응답은 JSON 오류로 처리한다', async () => {
    mockFetch({ 'GET /api/v1/reports/logbook': { status: 404, body: vehicleNotFound } })
    await expect(fetchLogbook(query)).rejects.toMatchObject({ code: 'VEHICLE_NOT_FOUND', message: '차량을 찾을 수 없습니다.' })
  })

  it('401이면 토큰을 지우고 로그인 화면으로 보낸다', async () => {
    setToken('jwt-token')
    window.history.replaceState(null, '', '/export')
    mockFetch({ 'GET /api/v1/reports/logbook': { status: 401, body: meUnauthorized } })
    await expect(fetchLogbook(query)).rejects.toMatchObject({ code: 'UNAUTHORIZED' })
    expect(getToken()).toBeNull()
    expect(window.location.pathname).toBe('/login')
  })
})
