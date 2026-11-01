import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import summary from './fixtures/dashboard-summary-success.json'
import { renderApp } from './renderApp'

describe('홈 (월간 집계)', () => {
  it('차량별 집계·비율·경고 라벨과 합계를 표시한다', async () => {
    const { mock } = renderApp('/?month=2026-11', { 'GET /api/v1/dashboard/summary': { body: summary } })

    const row = await screen.findByTestId('summary-row-3')
    const cells = within(row)
    expect(cells.getByText('12가3456')).toBeTruthy()
    expect(cells.getByText('아이오닉 5')).toBeTruthy()
    expect(cells.getByText('김영업 (영업1팀)')).toBeTruthy()
    expect(cells.getByText('14건')).toBeTruthy()
    expect(cells.getByText('412.3')).toBeTruthy()
    expect(cells.getByText('180.0')).toBeTruthy()
    expect(cells.getByText('160.5')).toBeTruthy()
    expect(cells.getByText('51.8')).toBeTruthy()
    expect(cells.getByText('20.0')).toBeTruthy()
    expect(cells.getByText('82.6%')).toBeTruthy()
    expect(cells.getByText('87.4%')).toBeTruthy()
    expect(cells.getByText('2026-11-03 18:05')).toBeTruthy()
    expect(cells.getByText('미확인 운행 있음')).toBeTruthy()
    expect(cells.getByRole('link', { name: '1건 보기' }).getAttribute('href')).toBe(
      '/trips?from=2026-11-01&to=2026-11-30&vehicleId=3&purpose=UNCONFIRMED',
    )

    const totals = within(screen.getByTestId('summary-totals'))
    expect(totals.getByText('합계')).toBeTruthy()
    expect(totals.getByText('412.3')).toBeTruthy()
    expect(totals.getByText('82.6%')).toBeTruthy()

    const [request] = mock.requests('GET', '/api/v1/dashboard/summary')
    expect(request.query.get('month')).toBe('2026-11')
  })

  it('경고 코드 4종을 한국어 라벨로 표시하고 총주행 0이면 비율은 -', async () => {
    const vehicle = summary.data.vehicles[0]
    const body = {
      ...summary,
      data: {
        ...summary.data,
        vehicles: [
          { ...vehicle, warnings: ['RATIO_HIGH', 'UNCONFIRMED_EXISTS', 'SYNC_DELAYED', 'ODO_GAP'] },
          {
            ...vehicle,
            vehicleId: 4,
            regNo: '34나5678',
            paired: false,
            lastSyncedAt: null,
            tripCount: 0,
            totalKm: 0,
            commuteKm: 0,
            businessKm: 0,
            personalKm: 0,
            unconfirmedKm: 0,
            unconfirmedCount: 0,
            businessRatio: null,
            maxBusinessRatio: null,
            warnings: [],
          },
        ],
      },
    }
    renderApp('/?month=2026-11', { 'GET /api/v1/dashboard/summary': { body } })

    const row = within(await screen.findByTestId('summary-row-3'))
    for (const label of ['업무비율 높음', '미확인 운행 있음', '동기화 지연', '계기판 거리 불연속']) {
      expect(row.getByText(label)).toBeTruthy()
    }
    const empty = within(screen.getByTestId('summary-row-4'))
    expect(empty.getAllByText('-').length).toBeGreaterThanOrEqual(2)
    expect(empty.getByText('미연결')).toBeTruthy()
    expect(empty.getByText('없음')).toBeTruthy()
  })
})
