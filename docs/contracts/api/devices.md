# 페어링·기기 API

> [계약 문서 목차](../README.md) · 규칙: [rules.md §2 페어링](../rules.md#2-페어링-d6), [§5 설정 내려받기](../rules.md#5-설정-내려받기-get-devicesmeconfig)

## POST /api/v1/devices/pairing-codes — 페어링 코드 발급

- 호출: 차량 / 인증: 없음 / IP당 분당 10회
- 6자리 코드, 유효 10분, 1회용 (D6)

### 요청

- `installationId`: 필수, 1~64자 (앱 설치 시 만든 UUID)
- `tokenHash`: 필수, 기기 토큰 원문의 SHA-256 hex 64자 (소문자)

```json
{
  "installationId": "8b1f2c64-5a1e-4b7c-9d3a-0f6e2c1d7a90",
  "tokenHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
}
```

### 성공 응답 — 201 Created

```json
{
  "success": true,
  "data": {
    "pairingId": "5d3c1a2e-7f40-4b8a-a1c9-2e6f0b9d4c11",
    "code": "482913",
    "expiresAt": "2026-11-03T08:22:00+09:00"
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "tokenHash: 64자리 16진수여야 합니다." } }
```

### 실패 응답 — 429 Too Many Requests (RATE_LIMITED)

```json
{ "success": false, "data": null, "error": { "code": "RATE_LIMITED", "message": "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요." } }
```

## GET /api/v1/devices/pairing-codes/{pairingId} — 페어링 상태 조회

- 호출: 차량 / 인증: 없음
- 코드 표시 중 3초 간격으로 호출. 토큰은 응답에 없음 (차량이 이미 원문을 가짐)

### 요청

- Path: `pairingId` (UUID) / 본문 없음

### 성공 응답 — 200 OK

`status`: `PENDING` / `PAIRED` / `EXPIRED`. `vehicle`은 PAIRED일 때만 값이 있습니다.

```json
{
  "success": true,
  "data": {
    "status": "PAIRED",
    "expiresAt": "2026-11-03T08:22:00+09:00",
    "vehicle": { "regNo": "12가3456", "modelName": "아이오닉 5" }
  },
  "error": null
}
```

### 실패 응답 — 404 Not Found (PAIRING_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "PAIRING_NOT_FOUND", "message": "페어링 요청을 찾을 수 없습니다." } }
```

## POST /api/v1/devices/pair — 차량 페어링

- 호출: 관리자 / 인증: 관리자 JWT / 관리자당 분당 10회
- 같은 차량의 기존 활성 기기는 폐기됩니다. 같은 회사에서 같은 `installationId`를 쓰던 다른 차량의 활성 기기도 폐기됩니다 ([rules §2](../rules.md#2-페어링-d6)).

### 요청

- `code`: 필수, 숫자 6자리 / `vehicleId`: 필수, 자기 회사의 활성 차량

```json
{
  "code": "482913",
  "vehicleId": 3
}
```

### 성공 응답 — 200 OK

`replacedDeviceId`는 기존 기기가 없으면 null.

```json
{
  "success": true,
  "data": {
    "deviceId": 7,
    "vehicleId": 3,
    "pairedAt": "2026-11-03T08:14:05+09:00",
    "replacedDeviceId": 5
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (PAIRING_CODE_INVALID)

```json
{ "success": false, "data": null, "error": { "code": "PAIRING_CODE_INVALID", "message": "페어링 코드가 올바르지 않습니다." } }
```

### 실패 응답 — 400 Bad Request (PAIRING_CODE_EXPIRED)

```json
{ "success": false, "data": null, "error": { "code": "PAIRING_CODE_EXPIRED", "message": "페어링 코드가 만료되었습니다. 차량에서 새 코드를 받아 주세요." } }
```

### 실패 응답 — 404 Not Found (VEHICLE_NOT_FOUND)

다른 회사 차량이거나 비활성 차량.

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_NOT_FOUND", "message": "차량을 찾을 수 없습니다." } }
```

## DELETE /api/v1/vehicles/{vehicleId}/device — 차량 연결 해제

- 호출: 관리자 / 인증: 관리자 JWT
- 활성 기기 토큰을 폐기합니다. 이후 차량 앱 요청은 401 `DEVICE_TOKEN_INVALID`.

### 요청

- Path: `vehicleId` / 본문 없음

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": { "vehicleId": 3, "deviceId": 7, "revokedAt": "2026-11-10T09:00:00+09:00" },
  "error": null
}
```

### 실패 응답 — 404 Not Found (VEHICLE_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_NOT_FOUND", "message": "차량을 찾을 수 없습니다." } }
```

### 실패 응답 — 404 Not Found (DEVICE_NOT_FOUND)

연결된 기기가 없는 차량.

```json
{ "success": false, "data": null, "error": { "code": "DEVICE_NOT_FOUND", "message": "연결된 기기가 없습니다." } }
```

## GET /api/v1/devices/me/config — 차량 설정 내려받기

- 호출: 차량 / 인증: 기기 토큰
- 차량 정보, 회사 직원 목록, 회사·거래처 장소, 관리자가 수정한 이 차량 트립의 현재 용도. 집 위치는 서버에 없음 (D2)

### 요청

- Header: `Authorization: Bearer {기기 토큰 원문}`
- Query: `since` (선택, ISO-8601). 없으면 기준정보 전체·최근 90일 관리자 수정 트립, 있으면 그 이후 바뀐 직원·장소·관리자 수정 트립(`tripUpdates`). 이전 응답의 `serverTime`을 그대로 사용
- 예: `GET /api/v1/devices/me/config?since=2026-11-03T08:14:06%2B09:00`

### 성공 응답 — 200 OK

직원 비활성은 `active: false`, 장소 삭제는 `deleted: true`로 전달합니다. `consentVersion`은 현재 동의 문서 버전이며, 차량 앱은 직원이 이 버전에 동의하지 않았으면 이용자로 선택될 때 동의를 받습니다.

`tripUpdates`는 이 차량(기기 토큰의 차량)의 트립 중 `since` 이후 **관리자 수정 이력**이 생긴 트립의 현재 용도입니다. `since`가 없으면 최근 90일(차량 앱의 동기화 완료 트립 보관 기간) 안의 관리자 수정이 대상입니다. 차량 앱은 같은 `clientUuid`의 로컬 트립을 이 값으로 덮어써, 관리자가 확정한 미확인 운행이 차량 화면에 남지 않게 합니다(D15). 차량 수정은 대상이 아닙니다(차량이 이미 아는 값).

```json
{
  "success": true,
  "data": {
    "serverTime": "2026-11-03T08:14:07+09:00",
    "consentVersion": "2026-11-01",
    "vehicle": { "id": 3, "regNo": "12가3456", "modelName": "아이오닉 5", "defaultDriverId": 11 },
    "drivers": [
      { "id": 11, "dept": "영업1팀", "name": "김영업", "active": true }
    ],
    "places": [
      { "id": 1, "name": "본사", "type": "COMPANY", "lat": 37.497942, "lng": 127.027621, "radiusM": 200, "deleted": false },
      { "id": 4, "name": "한빛상사", "type": "CLIENT", "lat": 37.566535, "lng": 126.977969, "radiusM": 150, "deleted": false }
    ],
    "tripUpdates": [
      { "clientUuid": "652d3495-eca1-4ffc-b5ef-d9c52d57ea0a", "purpose": "BUSINESS", "purposeSource": "ADMIN", "businessCategory": "OTHER" }
    ]
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "since: ISO-8601 형식이어야 합니다." } }
```

### 실패 응답 — 401 Unauthorized (DEVICE_TOKEN_INVALID)

연결 해제·재페어링된 기기. 차량 앱은 업로드를 멈추고 페어링 화면을 안내합니다.

```json
{ "success": false, "data": null, "error": { "code": "DEVICE_TOKEN_INVALID", "message": "차량 등록이 해제되었습니다. 다시 페어링해 주세요." } }
```

## POST /api/v1/devices/me/consents — 직원 동의 기록

- 호출: 차량 / 인증: 기기 토큰
- 직원이 차량 앱에서 이용자로 처음 선택될 때 받은 동의를 서버에 남깁니다 ([rules §11](../rules.md#11-개인정보-보유삭제동의-d13)). 같은 직원·같은 버전을 다시 보내면 기존 기록을 그대로 반환합니다.

### 요청

- `driverId`: 필수, 같은 회사의 활성 직원 / `consentVersion`: 필수, 1~20자 / `consentedAt`: 필수, ISO-8601 (서버 기준 10분 넘게 미래면 400)

```json
{
  "driverId": 11,
  "consentVersion": "2026-11-01",
  "consentedAt": "2026-11-03T08:10:00+09:00"
}
```

### 성공 응답 — 201 Created

처음 기록이면 201, 이미 있던 기록이면 200으로 같은 본문을 반환합니다.

```json
{
  "success": true,
  "data": { "driverId": 11, "consentVersion": "2026-11-01", "consentedAt": "2026-11-03T08:10:00+09:00" },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "consentedAt: 현재 시각 이후일 수 없습니다." } }
```

### 실패 응답 — 404 Not Found (DRIVER_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "DRIVER_NOT_FOUND", "message": "직원을 찾을 수 없습니다." } }
```
