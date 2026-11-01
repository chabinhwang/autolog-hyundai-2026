# 운행 API

> [계약 문서 목차](../README.md) · 규칙: [rules.md §3 업로드](../rules.md#3-트립-업로드-put-tripsbatch), [§4 수정](../rules.md#4-수정-post-tripsclientuuidedits), [§7 마스킹](../rules.md#7-마스킹-관리자에게-보이는-값)

## PUT /api/v1/trips/batch — 트립 일괄 업로드

- 호출: 차량 / 인증: 기기 토큰
- 최대 50건, 건별 결과 반환. 일부가 거절돼도 HTTP 200

### 요청

| 필드 | 필수 | 규칙 |
| --- | --- | --- |
| `trips[].clientUuid` | O | UUID v4, 중복 방지 키 |
| `trips[].vehicleId` | O | 기록 당시 페어링된 차량 ID. 기기 토큰의 차량과 다르면 `VEHICLE_MISMATCH` (D10) |
| `trips[].startedAt`, `endedAt` | O | ISO-8601, endedAt > startedAt. 서버 기준 10분 넘게 미래·366일 넘게 과거면 `TIMESTAMP_OUT_OF_RANGE` (D11) |
| `trips[].odoStartKm`, `odoEndKm` | O | 0 이상, odoEndKm ≥ odoStartKm |
| `trips[].origin`, `dest` | O | `{ type, placeId, label }`. COMPANY·CLIENT는 placeId 필수, HOME·UNREGISTERED는 null. label은 UNREGISTERED만 사용(최대 100자) |
| `trips[].autoPurpose` | O | 앱 추정값 |
| `trips[].purpose`, `purposeSource` | O | UNCONFIRMED이면 purposeSource는 AUTO |
| `trips[].businessCategory` | | purpose가 BUSINESS일 때만 |
| `trips[].note` | | 최대 200자 |
| `trips[].driverId` | | 같은 회사 활성 직원, null = 미지정 |
| `trips[].recordedAt` | O | 차량 로컬 저장 시각 |
| `appVersion` | | 최대 20자 |

```json
{
  "appVersion": "0.1.0",
  "trips": [
    {
      "clientUuid": "3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11",
      "vehicleId": 3,
      "startedAt": "2026-11-03T08:12:00+09:00",
      "endedAt": "2026-11-03T08:47:00+09:00",
      "odoStartKm": 12034.5,
      "odoEndKm": 12051.2,
      "origin": { "type": "HOME", "placeId": null, "label": null },
      "dest": { "type": "COMPANY", "placeId": 1, "label": null },
      "autoPurpose": "COMMUTE",
      "purpose": "COMMUTE",
      "purposeSource": "VOICE",
      "businessCategory": null,
      "note": null,
      "driverId": 11,
      "recordedAt": "2026-11-03T08:50:12+09:00"
    }
  ]
}
```

### 성공 응답 — 200 OK

`result`: `CREATED` / `DUPLICATE` / `REJECTED`. `REJECTED`만 `errorCode` 값이 있습니다 ([건별 errorCode 목록](../README.md#트립-업로드-건별-errorcode)).

```json
{
  "success": true,
  "data": {
    "results": [
      { "clientUuid": "3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11", "result": "CREATED", "errorCode": null },
      { "clientUuid": "a1b2c3d4-0000-4c1e-9a52-6d1f0e8b2c12", "result": "REJECTED", "errorCode": "DRIVER_NOT_FOUND" },
      { "clientUuid": "b2c3d4e5-0000-4c1e-9a52-6d1f0e8b2c14", "result": "REJECTED", "errorCode": "VEHICLE_MISMATCH" }
    ]
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

요청 전체가 잘못된 경우(`trips` 없음, 51건 이상). 개별 트립 오류는 200의 `REJECTED`로 반환합니다.

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "trips: 1~50건이어야 합니다." } }
```

### 실패 응답 — 401 Unauthorized (DEVICE_TOKEN_INVALID)

```json
{ "success": false, "data": null, "error": { "code": "DEVICE_TOKEN_INVALID", "message": "차량 등록이 해제되었습니다. 다시 페어링해 주세요." } }
```

## POST /api/v1/trips/{clientUuid}/edits — 운행 수정

- 호출: 차량·관리자 / 인증: 기기 토큰 또는 관리자 JWT
- 수정 가능: `purpose`, `businessCategory`, `note`, `driverId`. 덮어쓰지 않고 이력에 추가합니다.

### 요청 (차량)

- `clientEditUuid`: 필수, 중복 방지 키 / `editedAt`: 필수, 서버 기준 10분 넘게 미래면 400 (D11) / `purposeSource`: purpose를 바꿀 때 `MANUAL` 또는 `VOICE`
- `changes`: 바꿀 필드만

```json
{
  "clientEditUuid": "c0ffee00-1234-4abc-9def-000000000001",
  "editedAt": "2026-11-03T18:05:00+09:00",
  "purposeSource": "MANUAL",
  "changes": { "purpose": "BUSINESS", "businessCategory": "VISIT" }
}
```

### 요청 (관리자)

- `reason`: 필수, 1~200자. `editedAt`은 서버 시각

```json
{
  "reason": "운전자 확인 후 업무 세부 정정",
  "changes": { "businessCategory": "MEETING", "note": "분기 실적 회의" }
}
```

### 성공 응답 — 200 OK

바뀐 필드가 없으면 `edit`은 null. 같은 `clientEditUuid` 재전송은 처음 결과를 그대로 반환합니다.

```json
{
  "success": true,
  "data": {
    "edit": {
      "seq": 2,
      "actorType": "DEVICE",
      "changes": [
        { "field": "purpose", "before": "UNCONFIRMED", "after": "BUSINESS" },
        { "field": "businessCategory", "before": null, "after": "VISIT" }
      ]
    },
    "current": { "purpose": "BUSINESS", "purposeSource": "MANUAL", "businessCategory": "VISIT", "note": null, "driverId": 11 }
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (AUTO_FIELD_IMMUTABLE)

```json
{ "success": false, "data": null, "error": { "code": "AUTO_FIELD_IMMUTABLE", "message": "odoEndKm: 차량이 기록한 값은 수정할 수 없습니다." } }
```

### 실패 응답 — 400 Bad Request (EDIT_REASON_REQUIRED)

```json
{ "success": false, "data": null, "error": { "code": "EDIT_REASON_REQUIRED", "message": "관리자 수정에는 사유가 필요합니다." } }
```

### 실패 응답 — 403 Forbidden (PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN)

관리자가 개인 운행을 수정하거나, 용도를 PERSONAL로 바꾸려는 경우 (D4).

```json
{ "success": false, "data": null, "error": { "code": "PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN", "message": "개인 운행은 운전자만 수정할 수 있습니다." } }
```

### 실패 응답 — 404 Not Found (TRIP_NOT_FOUND)

없는 트립, 다른 회사·다른 차량의 트립, 아직 업로드되지 않은 트립.

```json
{ "success": false, "data": null, "error": { "code": "TRIP_NOT_FOUND", "message": "운행 기록을 찾을 수 없습니다." } }
```

## GET /api/v1/trips — 운행 목록

- 호출: 관리자 / 인증: 관리자 JWT
- 마스킹 적용 (rules §7). 정렬: 시작 시각 내림차순

### 요청

- Query: `from`, `to` (필수, `YYYY-MM-DD`, 최대 366일) / `vehicleId`, `driverId`, `purpose` (선택) / `page`, `size`
- 예: `GET /api/v1/trips?from=2026-11-01&to=2026-11-30&vehicleId=3&page=0&size=20`

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "clientUuid": "3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11",
        "tripDate": "2026-11-03",
        "vehicle": { "id": 3, "regNo": "12가3456" },
        "driver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
        "startedAt": "2026-11-03T08:12:00+09:00",
        "endedAt": "2026-11-03T08:47:00+09:00",
        "odoStartKm": 12034.5,
        "odoEndKm": 12051.2,
        "distanceKm": 16.7,
        "origin": { "type": "HOME", "label": "집" },
        "dest": { "type": "COMPANY", "label": "본사" },
        "purpose": "COMMUTE",
        "purposeSource": "VOICE",
        "businessCategory": null,
        "note": null,
        "edited": false,
        "masked": false
      },
      {
        "clientUuid": "7e1d0c9b-2222-4c1e-9a52-6d1f0e8b2c13",
        "tripDate": "2026-11-02",
        "vehicle": { "id": 3, "regNo": "12가3456" },
        "driver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
        "startedAt": null,
        "endedAt": null,
        "odoStartKm": 11990.0,
        "odoEndKm": 12034.5,
        "distanceKm": 44.5,
        "origin": null,
        "dest": null,
        "purpose": "PERSONAL",
        "purposeSource": "VOICE",
        "businessCategory": null,
        "note": null,
        "edited": false,
        "masked": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "to: from 이후 366일 이내여야 합니다." } }
```

## GET /api/v1/trips/{clientUuid} — 운행 상세

- 호출: 관리자 / 인증: 관리자 JWT
- 목록 항목 + 차량 자동값(`auto`) + 수정 이력. 마스킹 적용

### 요청

- Path: `clientUuid` / 본문 없음

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "clientUuid": "3f6c1e2a-0b7d-4c1e-9a52-6d1f0e8b2c11",
    "tripDate": "2026-11-03",
    "vehicle": { "id": 3, "regNo": "12가3456" },
    "driver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
    "startedAt": "2026-11-03T08:12:00+09:00",
    "endedAt": "2026-11-03T08:47:00+09:00",
    "odoStartKm": 12034.5,
    "odoEndKm": 12051.2,
    "distanceKm": 16.7,
    "origin": { "type": "HOME", "label": "집" },
    "dest": { "type": "CLIENT", "label": "한빛상사" },
    "purpose": "BUSINESS",
    "purposeSource": "ADMIN",
    "businessCategory": "MEETING",
    "note": "분기 실적 회의",
    "masked": false,
    "auto": { "purpose": "UNCONFIRMED" },
    "edits": [
      {
        "seq": 1,
        "actorType": "DEVICE",
        "actorName": "12가3456 차량",
        "changes": [
          { "field": "purpose", "before": "UNCONFIRMED", "after": "BUSINESS" },
          { "field": "businessCategory", "before": null, "after": "VISIT" }
        ],
        "reason": null,
        "editedAt": "2026-11-03T18:05:00+09:00",
        "receivedAt": "2026-11-03T18:05:09+09:00"
      },
      {
        "seq": 2,
        "actorType": "ADMIN",
        "actorName": "총무팀",
        "changes": [
          { "field": "businessCategory", "before": "VISIT", "after": "MEETING" },
          { "field": "note", "before": null, "after": "분기 실적 회의" }
        ],
        "reason": "운전자 확인 후 업무 세부 정정",
        "editedAt": "2026-11-04T10:00:00+09:00",
        "receivedAt": "2026-11-04T10:00:00+09:00"
      }
    ],
    "recordedAt": "2026-11-03T08:50:12+09:00",
    "syncedAt": "2026-11-03T08:50:20+09:00",
    "appVersion": "0.1.0"
  },
  "error": null
}
```

### 실패 응답 — 404 Not Found (TRIP_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "TRIP_NOT_FOUND", "message": "운행 기록을 찾을 수 없습니다." } }
```
