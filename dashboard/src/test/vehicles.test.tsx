import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import pairCodeExpired from './fixtures/devices-pair-code-expired.json'
import pairCodeInvalid from './fixtures/devices-pair-code-invalid.json'
import pairRequest from './fixtures/devices-pair-request.json'
import pairSuccess from './fixtures/devices-pair-success.json'
import rateLimited from './fixtures/devices-rate-limited.json'
import drivers from './fixtures/drivers-list-success.json'
import unpairSuccess from './fixtures/vehicles-device-delete-success.json'
import vehicles from './fixtures/vehicles-list-success.json'
import { renderApp } from './renderApp'

const routes = {
  'GET /api/v1/vehicles': { body: vehicles },
  'GET /api/v1/drivers': { body: drivers },
}

async function openPairDialog(name: string) {
  fireEvent.click(await screen.findByRole('button', { name }))
  return within(await screen.findByRole('dialog'))
}

describe('차량 페어링', () => {
  it('목록에 연결 상태를 표시하고 includeInactive를 보낸다', async () => {
    const { mock } = renderApp('/vehicles', routes)
    const paired = within(await screen.findByTestId('vehicle-row-3'))
    expect(paired.getByText('연결됨')).toBeTruthy()
    expect(paired.getByRole('button', { name: '12가3456 다시 페어링' })).toBeTruthy()
    const unpaired = within(screen.getByTestId('vehicle-row-4'))
    expect(unpaired.getByText('연결 안 됨')).toBeTruthy()
    expect(unpaired.queryByRole('button', { name: '34나5678 연결 해제' })).toBeNull()
    expect(mock.requests('GET', '/api/v1/vehicles')[0].query.get('includeInactive')).toBe('false')

    fireEvent.click(screen.getByLabelText('비활성 차량 포함'))
    await screen.findByTestId('vehicle-row-3')
    expect(mock.requests('GET', '/api/v1/vehicles').at(-1)?.query.get('includeInactive')).toBe('true')
  })

  it('6자리 숫자가 아니면 요청하지 않는다', async () => {
    const { mock } = renderApp('/vehicles', routes)
    const dialog = await openPairDialog('34나5678 페어링')
    expect(dialog.queryByText(/이미 연결된 기기가 있습니다/)).toBeNull()
    fireEvent.change(dialog.getByLabelText('페어링 코드 (숫자 6자리)'), { target: { value: '12ab' } })
    fireEvent.click(dialog.getByRole('button', { name: '페어링' }))
    expect(dialog.getByRole('alert').textContent).toContain('숫자 6자리')
    expect(mock.requests('POST', '/api/v1/devices/pair')).toHaveLength(0)
  })

  it('재페어링 시 기존 기기 교체를 안내하고, 코드와 vehicleId를 보낸다', async () => {
    const { mock } = renderApp('/vehicles', { ...routes, 'POST /api/v1/devices/pair': { body: pairSuccess } })
    const dialog = await openPairDialog('12가3456 다시 페어링')
    expect(dialog.getByText(/이미 연결된 기기가 있습니다/)).toBeTruthy()

    fireEvent.change(dialog.getByLabelText('페어링 코드 (숫자 6자리)'), { target: { value: '482913' } })
    fireEvent.click(dialog.getByRole('button', { name: '기존 기기 교체하고 페어링' }))

    expect(await screen.findByText('12가3456 차량을 페어링했습니다. 기존 기기는 연결이 해제되었습니다.')).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
    const [request] = mock.requests('POST', '/api/v1/devices/pair')
    expect(request.body).toEqual(pairRequest)
    // 목록 새로 고침
    expect(mock.requests('GET', '/api/v1/vehicles')).toHaveLength(2)
  })

  it.each([
    ['PAIRING_CODE_INVALID', 400, pairCodeInvalid, '페어링 코드가 올바르지 않습니다.'],
    ['PAIRING_CODE_EXPIRED', 400, pairCodeExpired, '페어링 코드가 만료되었습니다. 차량에서 새 코드를 받아 주세요.'],
    ['RATE_LIMITED', 429, rateLimited, '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'],
  ])('%s 오류 메시지를 대화상자에 표시한다', async (_code, status, body, message) => {
    renderApp('/vehicles', { ...routes, 'POST /api/v1/devices/pair': { status, body } })
    const dialog = await openPairDialog('34나5678 페어링')
    fireEvent.change(dialog.getByLabelText('페어링 코드 (숫자 6자리)'), { target: { value: '000000' } })
    fireEvent.click(dialog.getByRole('button', { name: '페어링' }))
    expect((await dialog.findByRole('alert')).textContent).toContain(message)
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('연결 해제는 확인 대화상자를 거쳐 DELETE /vehicles/{id}/device를 호출한다', async () => {
    const { mock } = renderApp('/vehicles', { ...routes, 'DELETE /api/v1/vehicles/3/device': { body: unpairSuccess } })
    fireEvent.click(await screen.findByRole('button', { name: '12가3456 연결 해제' }))
    const dialog = within(await screen.findByRole('dialog', { name: '차량 앱 연결 해제' }))
    expect(mock.requests('DELETE', '/api/v1/vehicles/3/device')).toHaveLength(0)

    fireEvent.click(dialog.getByRole('button', { name: '연결 해제' }))

    expect(await screen.findByText('12가3456 차량의 연결을 해제했습니다.')).toBeTruthy()
    expect(mock.requests('DELETE', '/api/v1/vehicles/3/device')).toHaveLength(1)
  })

  it('연결 해제 취소 시 요청하지 않는다', async () => {
    const { mock } = renderApp('/vehicles', routes)
    fireEvent.click(await screen.findByRole('button', { name: '12가3456 연결 해제' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '취소' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(mock.calls.some((c) => c.method === 'DELETE')).toBe(false)
  })
})
