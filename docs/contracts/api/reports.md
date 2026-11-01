# 대시보드·보고서 API

> [계약 문서 목차](../README.md) · 규칙: [rules.md §8 집계](../rules.md#8-대시보드-집계-get-dashboardsummary), [§9 내보내기](../rules.md#9-기록부-내보내기-get-reportslogbook-d7)

## GET /api/v1/dashboard/summary — 월간 집계 (홈)

- 호출: 관리자 / 인증: 관리자 JWT
- 활성 차량별 월간 합계와 경고

### 요청

- Query: `month` (필수, `YYYY-MM`)
- 예: `GET /api/v1/dashboard/summary?month=2026-11`

### 성공 응답 — 200 OK

- `businessRatio`: 업무사용비율(%), 총주행거리 0이면 null / `maxBusinessRatio`: 미확인이 전부 업무일 때 비율
- `warnings`: `RATIO_HIGH`, `UNCONFIRMED_EXISTS`, `SYNC_DELAYED`, `ODO_GAP`

```json
{
  "success": true,
  "data": {
    "month": "2026-11",
    "vehicles": [
      {
        "vehicleId": 3,
        "regNo": "12가3456",
        "modelName": "아이오닉 5",
        "defaultDriver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
        "paired": true,
        "lastSyncedAt": "2026-11-03T18:05:09+09:00",
        "tripCount": 14,
        "totalKm": 412.3,
        "commuteKm": 180.0,
        "businessKm": 160.5,
        "personalKm": 51.8,
        "unconfirmedKm": 20.0,
        "unconfirmedCount": 1,
        "businessRatio": 82.6,
        "maxBusinessRatio": 87.4,
        "warnings": ["UNCONFIRMED_EXISTS"]
      }
    ],
    "totals": {
      "totalKm": 412.3,
      "commuteKm": 180.0,
      "businessKm": 160.5,
      "personalKm": 51.8,
      "unconfirmedKm": 20.0,
      "unconfirmedCount": 1,
      "businessRatio": 82.6
    }
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "month: YYYY-MM 형식이어야 합니다." } }
```

## GET /api/v1/reports/logbook — 기록부 내보내기 (XLSX)

- 호출: 관리자 / 인증: 관리자 JWT
- 차량 1대, 기간 최대 366일. 마스킹 적용. 1차는 XLSX만 (D7)

### 요청

- Query: `vehicleId` (필수), `from`, `to` (필수, `YYYY-MM-DD`, 날짜 포함), `format` (선택, 기본 `xlsx`)
- 예: `GET /api/v1/reports/logbook?vehicleId=3&from=2026-01-01&to=2026-12-31&format=xlsx`
- 대시보드는 `fetch`로 받아 Blob 다운로드 (Authorization 헤더가 필요해 `<a href>` 직접 링크 불가)

### 성공 응답 — 200 OK (파일)

공통 응답 구조의 예외입니다. 본문은 XLSX 바이너리입니다.

```text
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="logbook.xlsx"; filename*=UTF-8''%EC%9A%B4%ED%96%89%EA%B8%B0%EB%A1%9D%EB%B6%80_12%EA%B0%803456_2026-01-01_2026-12-31.xlsx
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "format: 지원하는 형식은 xlsx입니다." } }
```

### 실패 응답 — 404 Not Found (VEHICLE_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_NOT_FOUND", "message": "차량을 찾을 수 없습니다." } }
```
