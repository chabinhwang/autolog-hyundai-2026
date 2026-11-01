# AutoLog 계약 문서 (1차)

서버·차량 앱·대시보드가 같은 기준으로 구현하기 위한 계약 문서입니다. **이 폴더가 원본**이고, Notion 「상세 명세 (1차)」는 작성 초기 기록입니다(2026-11-01 이후 갱신하지 않음).

- **(제안)**: 근거 없이 제가 정한 기본값. 바꿀 때는 이 문서부터 고칩니다.
- **미확인**: 문서·실측 근거 없음.

## 목차

| 문서 | 내용 |
| --- | --- |
| [architecture.md](architecture.md) | 전체 구조, 데이터 흐름, 구성 요소별 내부 구조, 설계 원칙 |
| [database.md](database.md) | 열거값, 테이블 정의 (PostgreSQL, Flyway) |
| [rules.md](rules.md) | 인증·페어링, 동기화·수정, 마스킹, 대시보드 집계, 기록부 내보내기, 개인정보 보유·동의 |
| [config.md](config.md) | 서버 환경변수, 회사·관리자 시드 |
| [api/auth.md](api/auth.md) | 관리자 로그인·현재 관리자 |
| [api/devices.md](api/devices.md) | 페어링 코드, 차량 페어링·연결 해제, 차량 설정 내려받기, 직원 동의 기록 |
| [api/trips.md](api/trips.md) | 트립 일괄 업로드, 수정, 운행 목록·상세 |
| [api/reports.md](api/reports.md) | 대시보드 월간 집계, 기록부 XLSX |
| [api/master-data.md](api/master-data.md) | 차량·직원·장소 관리 |

## 결정 필요 항목 (제안값으로 작성함)

| # | 항목 | 제안값 | 이유 |
| --- | --- | --- | --- |
| D1 | 관리자 인증 | JWT(HS256) Access Token만, 만료 12시간, Refresh 없음. 대시보드는 `sessionStorage`에 보관(탭 닫으면 로그아웃) | 대시보드(Cloudflare Pages)와 API(`autolog-api.aihub.io.kr`)의 출처가 달라 쿠키보다 Authorization 헤더가 단순 |
| D2 | 집 위치 | 서버에 저장하지 않음. 차량 앱 로컬에만 두고, 트립에는 유형 `HOME`·라벨 '집'만 전송 | 좌표 대신 장소명만 저장하는 원칙. 서버 `place`는 회사·거래처만 |
| D3 | 차량 자동값 | 시각·주행 전/후 계기판 거리·자동 추정 용도는 서버 저장 후 수정 불가 | '계기판 값을 차가 직접 기록' = 위변조 방지 어필 근거 |
| D4 | 개인 운행 | 관리자는 장소·메모·시:분을 볼 수 없고 용도도 바꿀 수 없음(바꿔서 장소를 보는 우회 차단). 용도 변경은 차량에서만 | 직원 위치 감시 우려 대응 |
| D5 | 해시 체인 | 1차는 수정 이력만 저장, 해시 체인 검증은 2차 | 어필 기능 표의 단계(2차) |
| D6 | 페어링 코드 | 숫자 6자리, 10분 유효, 1회용. 관리자 입력은 분당 10회 제한 | 인증된 관리자만 입력 + 짧은 유효시간 |
| D7 | 기록부 XLSX | 1차는 별지 서식의 항목·열 순서를 맞춘 표. 서식 레이아웃 그대로 재현은 원본 서식 파일 확보 후 | 별지 서식 원본(엑셀) 미확보 |
| D8 | CORS 허용 출처 | 환경변수 `CORS_ALLOWED_ORIGINS` = 대시보드 Pages 도메인 + `http://localhost:5173` | Pages 도메인 미정 |
| D9 | 경고 기준값 | 업무비율 95% 이상, 마지막 동기화 48시간 초과, 계기판 거리 불연속 1km 초과 | 임의 기준값 (근거 없음) |
| D10 | 재페어링과 미전송 트립 (G1) | 트립마다 기록 당시 `vehicleId`를 보내고, 토큰의 차량과 다르면 건별 `REJECTED`(`VEHICLE_MISMATCH`). 차량 앱은 재페어링 전에 미전송 건수를 경고 | 차량 앱을 다른 차량에 다시 페어링하면 이전 차량의 미전송 트립이 새 차량 기록으로 올라가는 것을 막음 |
| D11 | 시각 범위 (G2) | 서버 수신 시각보다 10분 넘게 미래이거나 366일 넘게 과거인 트립은 건별 `REJECTED`(`TIMESTAMP_OUT_OF_RANGE`) | 차량 시계 오류로 잘못된 날짜가 기록부에 들어가는 것을 막음. 10분·366일은 임의 기준값 |
| D12 | 환경변수·시드 (G3) | 서버 설정은 전부 환경변수. 회사·관리자 시드는 서버 시작 시 같은 사업자등록번호가 없을 때만 생성 ([config.md](config.md)) | 운영 Compose에서 이미지 하나로 회사별 시드 가능 |
| D13 | 보유 기간·동의 (G4) | 서버 운행 기록 5년 보유(국세기본법 장부 보존 기간 기준, 미확인). 직원별 동의를 차량 앱에서 받고 서버에 기록 ([rules §11](rules.md#11-개인정보-보유삭제동의-d13)) | 개인정보처리방침·동의 화면에 보유 기간이 필수 |
| D14 | 기기 토큰 저장 (G5) | Android Keystore의 AES-GCM 키로 암호화해 DataStore에 저장 | `androidx.security:security-crypto`(EncryptedSharedPreferences)는 1.1.0에서 전 API 지원 중단, Keystore 직접 사용 권장 ([릴리스 노트](https://developer.android.com/jetpack/androidx/releases/security)) |

## 공통 규칙

| 항목 | 규칙 |
| --- | --- |
| Base URL | 운영 `https://autolog-api.aihub.io.kr/api/v1` / 로컬 `http://localhost:8080/api/v1` / 에뮬레이터 `http://10.0.2.2:8080/api/v1` (Pleos AVD에서 동일한지 미확인) |
| 형식 | JSON, UTF-8, 필드명 camelCase |
| 응답 구조 | 항상 `{ success, data, error }`. 예외: 기록부 XLSX 다운로드 성공 응답은 파일 본문 (실패는 JSON) |
| 인증 헤더 | `Authorization: Bearer {관리자 JWT 또는 기기 토큰}`. 쿠키를 쓰지 않으므로 CSRF 대상 아님 |
| 시각 | ISO-8601 + 오프셋 (`2026-11-03T08:12:00+09:00`). 서버 저장은 `timestamptz` |
| 사용일자 | `tripDate` = `startedAt`의 Asia/Seoul 날짜. 서버가 계산 |
| 거리 | km, 소수 첫째 자리 number. 주행거리는 서버가 계산(주행 후 − 주행 전) |
| 식별자 | 서버 엔티티는 number id. 트립은 차량 앱이 만든 `clientUuid`(UUID v4)만 외부에 노출 |
| 페이지 | 쿼리 `page`(0부터), `size`(기본 20, 최대 100). 응답 `data` = `{ content, page, size, totalElements, totalPages }` |
| 회사 범위 | 관리자 요청은 JWT의 `orgId`로, 차량 요청은 기기 토큰의 차량으로 범위를 정함. 요청 본문의 회사·차량 ID는 신뢰하지 않음. 다른 회사 리소스는 403 대신 404 (존재 여부 비노출) |
| CORS | 허용 출처는 D8. 메서드 GET·POST·PUT·PATCH·DELETE, 헤더 Authorization·Content-Type. 자격 증명(쿠키) 없음 |

```json
{ "success": true, "data": { }, "error": null }
```

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "radiusM: 50 이상이어야 합니다." } }
```

## 오류 코드

| HTTP | code | 의미 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 필드 값 검증 실패 (메시지에 첫 번째 필드 오류) |
| 400 | `INVALID_REQUEST_BODY` | JSON 파싱 실패·타입 불일치 |
| 400 | `AUTO_FIELD_IMMUTABLE` | 수정 불가 필드(차량 자동값) 수정 시도 |
| 400 | `EDIT_REASON_REQUIRED` | 관리자 수정에 사유 없음 |
| 400 | `PAIRING_CODE_INVALID` | 대기 중인 페어링 코드와 일치하지 않음 |
| 400 | `PAIRING_CODE_EXPIRED` | 유효시간(10분) 지난 코드 |
| 401 | `UNAUTHORIZED` | 토큰 없음·형식 오류·만료 |
| 401 | `INVALID_CREDENTIALS` | 로그인 아이디·비밀번호 불일치 |
| 401 | `DEVICE_TOKEN_INVALID` | 등록되지 않았거나 연결 해제된 기기 토큰 |
| 403 | `FORBIDDEN` | 인증은 됐지만 호출 주체가 맞지 않음 (예: 기기 토큰으로 관리자 API) |
| 403 | `PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN` | 관리자가 개인 운행 수정 시도 (D4) |
| 404 | `NOT_FOUND` | 경로 없음 |
| 404 | `TRIP_NOT_FOUND` · `VEHICLE_NOT_FOUND` · `DRIVER_NOT_FOUND` · `PLACE_NOT_FOUND` · `PAIRING_NOT_FOUND` · `DEVICE_NOT_FOUND` | 해당 리소스 없음 (다른 회사 소유 포함) |
| 409 | `VEHICLE_REG_NO_DUPLICATE` | 같은 회사에 같은 자동차등록번호 존재 |
| 429 | `RATE_LIMITED` | 요청 횟수 제한 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 (상세는 서버 로그에만) |

### 트립 업로드 건별 errorCode

`PUT /trips/batch`는 HTTP 200 안에서 건별로 `REJECTED` + `errorCode`를 돌려줍니다.

| errorCode | 의미 |
| --- | --- |
| `VALIDATION_FAILED` | 필드 규칙 위반 (거리·시각 순서, 열거값, 필수값, placeId 조합 등) |
| `VEHICLE_MISMATCH` | 트립의 `vehicleId`가 기기 토큰의 차량과 다름 (D10) |
| `TIMESTAMP_OUT_OF_RANGE` | 시각이 서버 기준 10분 넘게 미래이거나 366일 넘게 과거 (D11) |
| `DRIVER_NOT_FOUND` | 같은 회사의 활성 직원이 아님 |
| `PLACE_NOT_FOUND` | 같은 회사의 장소가 아님 |

- 401·403·429·500은 모든 인증 API에 공통이라 API 문서의 각 엔드포인트에는 그 API에 특화된 실패만 적습니다.
- 서버 로그·오류 메시지에 장소 라벨·메모·토큰을 출력하지 않습니다.

## 2차 범위 (이번 계약에서 제외)

월 마감(`POST /closes`, 마감된 달 수정 409 `MONTH_CLOSED`), 미확인 확인 요청, SSE 실시간 반영(`GET /events`), PDF 내보내기, 절세 리포트, 해시 체인 검증, 운전자 웹
