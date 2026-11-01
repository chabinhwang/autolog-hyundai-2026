import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import drivers from './fixtures/drivers-list-success.json'
import tripsList from './fixtures/trips-list-success.json'
import vehicles from './fixtures/vehicles-list-success.json'
import { renderApp } from './renderApp'

const routes = {
  'GET /api/v1/trips': { body: tripsList },
  'GET /api/v1/vehicles': { body: vehicles },
  'GET /api/v1/drivers': { body: drivers },
}

describe('운행 목록', () => {
  it('조건을 쿼리로 보내고, 공개 운행은 장소·시:분을 표시한다', async () => {
    const { mock } = renderApp('/trips?from=2026-11-01&to=2026-11-30&vehicleId=3', routes)

    const row = within(await screen.findByTestId('trip-row-3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11'))
    expect(row.getByText('2026-11-03 (화)')).toBeTruthy()
    expect(row.getByText('08:12~08:47')).toBeTruthy()
    expect(row.getByText('집')).toBeTruthy()
    expect(row.getByText('본사')).toBeTruthy()
    expect(row.getByText('출퇴근')).toBeTruthy()
    expect(row.getByText('음성 확인')).toBeTruthy()
    expect(row.getByText('16.7 km')).toBeTruthy()
    expect(row.getByText('12,034.5')).toBeTruthy()

    const [request] = mock.requests('GET', '/api/v1/trips')
    expect(Object.fromEntries(request.query)).toEqual({
      from: '2026-11-01',
      to: '2026-11-30',
      vehicleId: '3',
      page: '0',
      size: '20',
    })
    expect(screen.getByText('1 / 1 페이지 (총 2건)')).toBeTruthy()
  })

  it("masked: true인 운행은 '장소 비공개'로 표시하고 시:분을 숨긴다", async () => {
    renderApp('/trips?from=2026-11-01&to=2026-11-30', routes)

    const rowElement = await screen.findByTestId('trip-row-7e1d0c9b-2222-4c1e-9a52-6d1f0e8b2c13')
    const row = within(rowElement)
    expect(row.getAllByText('장소 비공개')).toHaveLength(2)
    expect(row.getByText('비공개')).toBeTruthy()
    expect(row.getByText('개인')).toBeTruthy()
    expect(row.getByText('44.5 km')).toBeTruthy()
    expect(rowElement.textContent).not.toMatch(/\d{2}:\d{2}/)
  })

  it('조회 기간이 366일을 넘으면 요청하지 않고 안내한다', async () => {
    const { mock } = renderApp('/trips?from=2026-11-01&to=2026-11-30', routes)
    await screen.findByTestId('trip-row-3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11')

    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-01-01' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2027-01-02' } })
    fireEvent.click(screen.getByRole('button', { name: '조회' }))

    expect((await screen.findByRole('alert')).textContent).toContain('최대 366일')
    expect(mock.requests('GET', '/api/v1/trips')).toHaveLength(1)
  })

  it('필터를 바꿔 조회하면 URL과 요청에 반영한다', async () => {
    const { mock } = renderApp('/trips?from=2026-11-01&to=2026-11-30', routes)
    await screen.findByTestId('trip-row-3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11')
    await screen.findByRole('option', { name: '김영업 (영업1팀)' })

    fireEvent.change(screen.getByLabelText('직원'), { target: { value: '11' } })
    fireEvent.change(screen.getByLabelText('용도'), { target: { value: 'BUSINESS' } })
    fireEvent.click(screen.getByRole('button', { name: '조회' }))

    await screen.findByTestId('trip-row-3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11')
    const requests = mock.requests('GET', '/api/v1/trips')
    expect(requests).toHaveLength(2)
    expect(requests[1].query.get('driverId')).toBe('11')
    expect(requests[1].query.get('purpose')).toBe('BUSINESS')
    expect(window.location.search).toBe('?from=2026-11-01&to=2026-11-30&driverId=11&purpose=BUSINESS')
  })
})
