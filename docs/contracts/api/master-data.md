# 기준정보 API (차량·직원·장소)

> [계약 문서 목차](../README.md) · 테이블: [database.md](../database.md)

- 호출: 관리자 / 인증: 관리자 JWT (전부)
- 회사당 데이터가 적으므로 목록은 페이지 없이 전체 반환
- 삭제: 차량·직원은 `active: false`로 비활성(기록 보존), 장소는 소프트 삭제
- 직원·장소를 바꾸면 `updated_at`이 갱신되어 다음 차량 설정 내려받기에 반영됩니다.

## 차량

### GET /api/v1/vehicles — 차량 목록

#### 요청

- Query: `includeInactive` (선택, 기본 false)

#### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": [
    {
      "id": 3,
      "regNo": "12가3456",
      "modelName": "아이오닉 5",
      "defaultDriver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
      "active": true,
      "device": { "paired": true, "deviceId": 7, "pairedAt": "2026-11-03T08:14:05+09:00", "lastSyncedAt": "2026-11-03T18:05:09+09:00" }
    },
    {
      "id": 4,
      "regNo": "34나5678",
      "modelName": "쏘나타",
      "defaultDriver": null,
      "active": true,
      "device": { "paired": false, "deviceId": null, "pairedAt": null, "lastSyncedAt": null }
    }
  ],
  "error": null
}
```

### POST /api/v1/vehicles — 차량 추가

#### 요청

- `regNo`: 필수, 1~20자, 회사 안에서 유일 / `modelName`: 필수, 1~50자 / `defaultDriverId`: 선택, 같은 회사 활성 직원

```json
{ "regNo": "12가3456", "modelName": "아이오닉 5", "defaultDriverId": 11 }
```

#### 성공 응답 — 201 Created

```json
{
  "success": true,
  "data": {
    "id": 3,
    "regNo": "12가3456",
    "modelName": "아이오닉 5",
    "defaultDriver": { "id": 11, "dept": "영업1팀", "name": "김영업" },
    "active": true,
    "device": { "paired": false, "deviceId": null, "pairedAt": null, "lastSyncedAt": null }
  },
  "error": null
}
```

#### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "regNo: 비어 있을 수 없습니다." } }
```

#### 실패 응답 — 404 Not Found (DRIVER_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "DRIVER_NOT_FOUND", "message": "직원을 찾을 수 없습니다." } }
```

#### 실패 응답 — 409 Conflict (VEHICLE_REG_NO_DUPLICATE)

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_REG_NO_DUPLICATE", "message": "이미 등록된 자동차등록번호입니다." } }
```

### PATCH /api/v1/vehicles/{vehicleId} — 차량 수정·비활성

#### 요청

- 보낸 필드만 바뀝니다: `regNo`, `modelName`, `defaultDriverId`(null이면 해제), `active`
- `active: false`로 바꾸면 연결된 기기도 폐기합니다.

```json
{ "defaultDriverId": null, "active": false }
```

#### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "id": 3,
    "regNo": "12가3456",
    "modelName": "아이오닉 5",
    "defaultDriver": null,
    "active": false,
    "device": { "paired": false, "deviceId": null, "pairedAt": null, "lastSyncedAt": "2026-11-03T18:05:09+09:00" }
  },
  "error": null
}
```

#### 실패 응답 — 404 Not Found (VEHICLE_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_NOT_FOUND", "message": "차량을 찾을 수 없습니다." } }
```

#### 실패 응답 — 409 Conflict (VEHICLE_REG_NO_DUPLICATE)

```json
{ "success": false, "data": null, "error": { "code": "VEHICLE_REG_NO_DUPLICATE", "message": "이미 등록된 자동차등록번호입니다." } }
```

## 직원

### GET /api/v1/drivers — 직원 목록

#### 요청

- Query: `includeInactive` (선택, 기본 false)

#### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": [
    { "id": 11, "dept": "영업1팀", "name": "김영업", "active": true },
    { "id": 12, "dept": "영업2팀", "name": "이외근", "active": true }
  ],
  "error": null
}
```

### POST /api/v1/drivers — 직원 추가

#### 요청

- `dept`: 필수, 1~50자 / `name`: 필수, 1~30자

```json
{ "dept": "영업1팀", "name": "김영업" }
```

#### 성공 응답 — 201 Created

```json
{ "success": true, "data": { "id": 11, "dept": "영업1팀", "name": "김영업", "active": true }, "error": null }
```

#### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "name: 비어 있을 수 없습니다." } }
```

### PATCH /api/v1/drivers/{driverId} — 직원 수정·비활성

#### 요청

- 보낸 필드만 바뀝니다: `dept`, `name`, `active`
- 비활성 직원은 차량 앱 선택 목록에서 빠지고, 기본 운전자로 지정된 차량은 기본 운전자가 해제됩니다. 과거 트립의 직원 정보는 유지

```json
{ "active": false }
```

#### 성공 응답 — 200 OK

```json
{ "success": true, "data": { "id": 11, "dept": "영업1팀", "name": "김영업", "active": false }, "error": null }
```

#### 실패 응답 — 404 Not Found (DRIVER_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "DRIVER_NOT_FOUND", "message": "직원을 찾을 수 없습니다." } }
```

## 장소 (회사·거래처)

### GET /api/v1/places — 장소 목록

#### 요청

- Query: `type` (선택, `COMPANY`/`CLIENT`). 삭제된 장소는 제외

#### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "본사", "type": "COMPANY", "lat": 37.497942, "lng": 127.027621, "radiusM": 200, "updatedAt": "2026-11-01T10:00:00+09:00" },
    { "id": 4, "name": "한빛상사", "type": "CLIENT", "lat": 37.566535, "lng": 126.977969, "radiusM": 150, "updatedAt": "2026-11-02T14:30:00+09:00" }
  ],
  "error": null
}
```

### POST /api/v1/places — 장소 추가

#### 요청

- `name`: 필수, 1~100자 / `type`: 필수, `COMPANY`·`CLIENT` / `lat`: 필수, -90~90 / `lng`: 필수, -180~180 / `radiusM`: 선택, 50~2000 (기본 200)
- 지도 검색은 없음. 좌표는 직접 입력 (1차)

```json
{ "name": "한빛상사", "type": "CLIENT", "lat": 37.566535, "lng": 126.977969, "radiusM": 150 }
```

#### 성공 응답 — 201 Created

```json
{
  "success": true,
  "data": { "id": 4, "name": "한빛상사", "type": "CLIENT", "lat": 37.566535, "lng": 126.977969, "radiusM": 150, "updatedAt": "2026-11-02T14:30:00+09:00" },
  "error": null
}
```

#### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "radiusM: 50 이상이어야 합니다." } }
```

### PATCH /api/v1/places/{placeId} — 장소 수정

#### 요청

- 보낸 필드만 바뀝니다: `name`, `type`, `lat`, `lng`, `radiusM`
- 과거 트립의 라벨은 업로드 당시 값을 유지합니다 (제안)

```json
{ "radiusM": 300 }
```

#### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": { "id": 4, "name": "한빛상사", "type": "CLIENT", "lat": 37.566535, "lng": 126.977969, "radiusM": 300, "updatedAt": "2026-11-05T09:00:00+09:00" },
  "error": null
}
```

#### 실패 응답 — 404 Not Found (PLACE_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "PLACE_NOT_FOUND", "message": "장소를 찾을 수 없습니다." } }
```

### DELETE /api/v1/places/{placeId} — 장소 삭제

#### 요청

- Path: `placeId` / 본문 없음. 소프트 삭제 (`deleted = true`, 차량에 삭제 전달)

#### 성공 응답 — 200 OK

```json
{ "success": true, "data": { "id": 4, "deleted": true }, "error": null }
```

#### 실패 응답 — 404 Not Found (PLACE_NOT_FOUND)

```json
{ "success": false, "data": null, "error": { "code": "PLACE_NOT_FOUND", "message": "장소를 찾을 수 없습니다." } }
```
