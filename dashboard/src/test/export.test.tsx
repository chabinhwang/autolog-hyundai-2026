import { fireEvent, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import vehicleNotFound from './fixtures/reports-logbook-vehicle-not-found.json'
import vehicles from './fixtures/vehicles-list-success.json'
import { renderApp } from './renderApp'

const CONTRACT_HEADER =
  "attachment; filename=\"logbook.xlsx\"; filename*=UTF-8''%EC%9A%B4%ED%96%89%EA%B8%B0%EB%A1%9D%EB%B6%80_12%EA%B0%803456_2026-01-01_2026-12-31.xlsx"

function stubDownload() {
  // jsdom에는 createObjectURL이 없을 수 있어 먼저 자리를 만든 뒤 가로챕니다.
  if (typeof URL.createObjectURL !== 'function') {
    Object.assign(URL, { createObjectURL: () => '', revokeObjectURL: () => undefined })
  }
  const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:logbook')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  const clicked: string[] = []
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    clicked.push(this.download)
  })
  return { createObjectURL, clicked }
}

async function submit(vehicleId: string, from: string, to: string) {
  await screen.findByRole('option', { name: '12가3456 · 아이오닉 5' })
  fireEvent.change(screen.getByLabelText('차량'), { target: { value: vehicleId } })
  fireEvent.change(screen.getByLabelText('시작일'), { target: { value: from } })
  fireEvent.change(screen.getByLabelText('종료일'), { target: { value: to } })
  fireEvent.click(screen.getByRole('button', { name: 'XLSX 내려받기' }))
}

describe('기록부 내보내기 화면', () => {
  it('fetch로 받아 filename*의 파일명으로 저장한다', async () => {
    const { clicked, createObjectURL } = stubDownload()
    const { mock } = renderApp('/export', {
      'GET /api/v1/vehicles': { body: vehicles },
      'GET /api/v1/reports/logbook': {
        raw: new Uint8Array([0x50, 0x4b]),
        headers: {
          'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'Content-Disposition': CONTRACT_HEADER,
        },
      },
    })

    await submit('3', '2026-01-01', '2026-12-31')

    expect(await screen.findByText('운행기록부_12가3456_2026-01-01_2026-12-31.xlsx 파일을 내려받았습니다.')).toBeTruthy()
    expect(clicked).toEqual(['운행기록부_12가3456_2026-01-01_2026-12-31.xlsx'])
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    const [request] = mock.requests('GET', '/api/v1/reports/logbook')
    expect(request.query.get('vehicleId')).toBe('3')
  })

  it('헤더가 없으면 운행기록부_{regNo}_{from}_{to}.xlsx로 저장한다', async () => {
    const { clicked } = stubDownload()
    renderApp('/export', {
      'GET /api/v1/vehicles': { body: vehicles },
      'GET /api/v1/reports/logbook': { raw: new Uint8Array([1]), headers: { 'Content-Type': 'application/octet-stream' } },
    })
    await submit('4', '2026-11-01', '2026-11-30')
    await screen.findByText(/파일을 내려받았습니다/)
    expect(clicked).toEqual(['운행기록부_34나5678_2026-11-01_2026-11-30.xlsx'])
  })

  it('실패 응답(JSON)은 오류 메시지로 표시한다', async () => {
    const { clicked } = stubDownload()
    renderApp('/export', {
      'GET /api/v1/vehicles': { body: vehicles },
      'GET /api/v1/reports/logbook': { status: 404, body: vehicleNotFound },
    })
    await submit('3', '2026-01-01', '2026-12-31')
    expect((await screen.findByRole('alert')).textContent).toContain('차량을 찾을 수 없습니다.')
    expect(clicked).toEqual([])
  })

  it('기간이 366일을 넘으면 요청하지 않는다', async () => {
    stubDownload()
    const { mock } = renderApp('/export', { 'GET /api/v1/vehicles': { body: vehicles } })
    await submit('3', '2026-01-01', '2027-01-02')
    expect((await screen.findByRole('alert')).textContent).toContain('최대 366일')
    expect(mock.requests('GET', '/api/v1/reports/logbook')).toHaveLength(0)
  })
})
