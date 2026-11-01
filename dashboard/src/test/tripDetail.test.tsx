import { fireEvent, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import drivers from './fixtures/drivers-list-success.json'
import detail from './fixtures/trips-detail-success.json'
import editAdminRequest from './fixtures/trips-edit-admin-request.json'
import reasonRequired from './fixtures/trips-edit-reason-required.json'
import personalForbidden from './fixtures/trips-edit-personal-forbidden.json'
import editSuccess from './fixtures/trips-edit-success.json'
import tripsList from './fixtures/trips-list-success.json'
import { renderApp } from './renderApp'

const UUID = detail.data.clientUuid
const PERSONAL_UUID = tripsList.data.content[1].clientUuid

// 계약에 개인 운행 상세 예시가 없어, 목록 예시의 개인 운행(마스킹됨)에 상세 전용 필드를 더해 만듭니다.
const { edited: _edited, ...personalItem } = tripsList.data.content[1]
const personalDetail = {
  success: true,
  data: {
    ...personalItem,
    auto: { purpose: 'PERSONAL' },
    edits: [],
    recordedAt: '2026-11-02T20:10:00+09:00',
    syncedAt: '2026-11-02T20:10:05+09:00',
    appVersion: '0.1.0',
  },
  error: null,
}

const baseRoutes = {
  'GET /api/v1/drivers': { body: drivers },
  [`GET /api/v1/trips/${UUID}`]: { body: detail },
  [`GET /api/v1/trips/${PERSONAL_UUID}`]: { body: personalDetail },
}

describe('운행 상세', () => {
  it('현재값·차량 자동값·수정 이력(전/후, 사유, 수정 주체)을 표시한다', async () => {
    renderApp(`/trips/${UUID}`, baseRoutes)

    const auto = within(await screen.findByRole('region', { name: '차량 자동 기록 (수정 불가)' }))
    expect(auto.getByText('2026-11-03 08:12')).toBeTruthy()
    expect(auto.getByText('2026-11-03 08:47')).toBeTruthy()
    expect(auto.getByText('12,034.5 km')).toBeTruthy()
    expect(auto.getByText('12,051.2 km')).toBeTruthy()
    expect(auto.getByText('미확인')).toBeTruthy()

    const current = within(screen.getByRole('region', { name: '현재 기록' }))
    expect(current.getByText('업무 · 회의')).toBeTruthy()
    expect(current.getByText('관리자 수정')).toBeTruthy()
    expect(current.getByText('한빛상사')).toBeTruthy()

    const first = within(screen.getByTestId('edit-row-1'))
    expect(first.getByText('차량')).toBeTruthy()
    expect(first.getByText('12가3456 차량')).toBeTruthy()
    expect(screen.getByTestId('edit-row-1').textContent).toContain('용도: 미확인 → 업무')
    expect(screen.getByTestId('edit-row-1').textContent).toContain('업무 세부: (없음) → 거래처 방문')

    const second = within(screen.getByTestId('edit-row-2'))
    expect(second.getByText('관리자')).toBeTruthy()
    expect(second.getByText('총무팀')).toBeTruthy()
    expect(second.getByText('운전자 확인 후 업무 세부 정정')).toBeTruthy()
    expect(screen.getByTestId('edit-row-2').textContent).toContain('업무 세부: 거래처 방문 → 회의')
    expect(screen.getByTestId('edit-row-2').textContent).toContain('메모: (없음) → 분기 실적 회의')
  })

  it('관리자 용도 선택지에 개인(PERSONAL)이 없다', async () => {
    renderApp(`/trips/${UUID}`, baseRoutes)
    const select = await screen.findByLabelText('용도')
    const options = within(select).getAllByRole('option').map((o) => o.textContent)
    expect(options).toEqual(['출퇴근', '업무'])
  })

  it('현재 용도가 개인이면 수정 폼 대신 수정 불가 안내를 보여 준다', async () => {
    renderApp(`/trips/${PERSONAL_UUID}`, baseRoutes)
    expect(await screen.findByText(/개인 운행은 관리자가 수정할 수 없습니다/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: '수정 저장' })).toBeNull()
    expect(screen.queryByLabelText('수정 사유 (필수)')).toBeNull()
    const current = within(screen.getByRole('region', { name: '현재 기록' }))
    expect(current.getAllByText('장소 비공개')).toHaveLength(2)
    const auto = within(screen.getByRole('region', { name: '차량 자동 기록 (수정 불가)' }))
    expect(auto.getAllByText('(비공개)')).toHaveLength(2)
  })

  it('사유가 없으면 요청하지 않고 사유 입력을 요구한다', async () => {
    const { mock } = renderApp(`/trips/${UUID}`, baseRoutes)
    fireEvent.change(await screen.findByLabelText('메모'), { target: { value: '새 메모' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))
    expect((await screen.findByRole('alert')).textContent).toContain('수정 사유를 입력해 주세요.')
    expect(mock.requests('POST', `/api/v1/trips/${UUID}/edits`)).toHaveLength(0)
  })

  it('바뀐 필드만 changes에 담아 사유와 함께 보낸다 (계약 예시 요청과 동일)', async () => {
    // 수정 전 상태: 업무·거래처 방문, 메모 없음 (계약 상세 예시의 seq 1 직후)
    const before = { ...detail, data: { ...detail.data, purposeSource: 'MANUAL', businessCategory: 'VISIT', note: null, edits: [detail.data.edits[0]] } }
    let saved = false
    const { mock } = renderApp(`/trips/${UUID}`, {
      ...baseRoutes,
      [`GET /api/v1/trips/${UUID}`]: () => ({ body: saved ? detail : before }),
      [`POST /api/v1/trips/${UUID}/edits`]: () => {
        saved = true
        return { body: editSuccess }
      },
    })

    fireEvent.change(await screen.findByLabelText('업무 세부'), { target: { value: 'MEETING' } })
    fireEvent.change(screen.getByLabelText('메모'), { target: { value: '분기 실적 회의' } })
    fireEvent.change(screen.getByLabelText('수정 사유 (필수)'), { target: { value: '운전자 확인 후 업무 세부 정정' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    expect(await screen.findByText('수정했습니다. 수정 이력에 기록되었습니다.')).toBeTruthy()
    const [request] = mock.requests('POST', `/api/v1/trips/${UUID}/edits`)
    expect(request.body).toEqual(editAdminRequest)
    expect(await screen.findByTestId('edit-row-2')).toBeTruthy()
  })

  it.each([
    ['EDIT_REASON_REQUIRED', 400, reasonRequired, '관리자 수정에는 사유가 필요합니다.'],
    ['PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN', 403, personalForbidden, '개인 운행은 운전자만 수정할 수 있습니다.'],
  ])('서버 오류 %s 메시지를 표시한다', async (_code, status, body, message) => {
    renderApp(`/trips/${UUID}`, { ...baseRoutes, [`POST /api/v1/trips/${UUID}/edits`]: { status, body } })
    fireEvent.change(await screen.findByLabelText('용도'), { target: { value: 'COMMUTE' } })
    fireEvent.change(screen.getByLabelText('수정 사유 (필수)'), { target: { value: '정정' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))
    expect((await screen.findByRole('alert')).textContent).toContain(message)
  })

  it('용도를 업무가 아닌 값으로 바꾸면 업무 세부는 보내지 않는다', async () => {
    const { mock } = renderApp(`/trips/${UUID}`, {
      ...baseRoutes,
      [`POST /api/v1/trips/${UUID}/edits`]: { body: editSuccess },
    })
    fireEvent.change(await screen.findByLabelText('용도'), { target: { value: 'COMMUTE' } })
    expect((screen.getByLabelText('업무 세부') as HTMLSelectElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('수정 사유 (필수)'), { target: { value: '출근 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))
    await screen.findByText('수정했습니다. 수정 이력에 기록되었습니다.')
    const [request] = mock.requests('POST', `/api/v1/trips/${UUID}/edits`)
    expect(request.body).toEqual({ reason: '출근 확인', changes: { purpose: 'COMMUTE' } })
  })
})
