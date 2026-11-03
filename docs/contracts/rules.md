# 동작 규칙

> [계약 문서 목차](README.md)

## 1. 인증

### 관리자 (D1)

- `POST /auth/login` → JWT 발급. 클레임 `sub`(adminUserId), `orgId`, `role`(ADMIN), `exp`(발급 + 12시간)
- 서명 키: 환경변수 `JWT_SECRET` (32바이트 이상). 비밀번호는 BCrypt
- 로그인 요청 제한: IP당 분당 10회 → 429
- 로그인 실패 제한(계정 기준): 같은 `loginId`(대소문자 무시)로 첫 실패부터 15분 안에 5번 실패하면, 그 15분이 끝날 때까지 비밀번호가 맞아도 429. 로그인에 성공하면 실패 기록을 지움. 없는 아이디도 똑같이 셈 (계정 존재 여부 비노출)
  - IP 기준 제한은 `X-Forwarded-For`를 조작하면 우회될 수 있으므로(공통 Nginx·Cloudflare가 기존 값 뒤에 덧붙이는 방식), 무차별 대입 방어는 계정 기준 제한이 담당
  - 누군가 일부러 5번 틀려 관리자를 15분간 못 들어오게 할 수 있음 (감수)
- 회사·관리자 계정은 배포 시 시드로 생성. 회원가입·초대·비밀번호 찾기 없음. 심사용 회사·계정은 별도 1개

### 차량 기기 토큰

- 차량 앱이 페어링 시작 때 32바이트 난수(`SecureRandom`) → base64url 문자열로 만들어 보관 (D14)
  - Android Keystore에 AES-256-GCM 키 생성(키는 기기 밖으로 못 나감) → 토큰을 이 키로 암호화해 DataStore에 저장
  - `androidx.security:security-crypto`(EncryptedSharedPreferences)는 지원 중단이라 쓰지 않음
  - 앱 데이터 삭제·재설치로 키가 사라지면 토큰을 복구할 수 없으므로 다시 페어링
- 서버에는 SHA-256 hex(64자)만 보냄·저장. 원문은 차량 밖으로 나가지 않음 → DB가 유출돼도 토큰 재사용 불가
- 만료 없음. 관리자가 연결 해제하거나 같은 차량을 다시 페어링하면 폐기(`revoked_at`)
- 폐기된 토큰 요청 → 401 `DEVICE_TOKEN_INVALID` → 차량 앱은 기기 토큰을 삭제하고 업로드 중단·페어링 화면 안내, 로컬 기록은 유지 (구현 확인됨 2026-11-02)
- 차량 1대 = 활성 기기 1개

### 토큰 구분

- 서버는 `Authorization: Bearer` 값이 JWT 형식(점 2개)이면 관리자, 아니면 기기 토큰으로 처리 (제안)
- 관리자 전용 API에 기기 토큰 → 403 `FORBIDDEN`, 차량 전용 API에 관리자 JWT → 403 `FORBIDDEN`

## 2. 페어링 (D6)

차량 앱의 페어링 코드 발급·조회 요청에는 이전 기기 토큰을 붙이지 않습니다 (구현 확인됨 2026-11-02).

1. 차량 앱(P단 정차 중): `POST /devices/pairing-codes` (installationId, tokenHash) → pairingId, 6자리 코드, 만료 시각
2. 차량 앱: 코드·QR 표시, 3초 간격으로 `GET /devices/pairing-codes/{pairingId}`
3. 관리자: 차량 관리 화면에서 해당 차량 '페어링' → 코드 입력 → `POST /devices/pair`
4. 서버: 같은 차량의 기존 활성 기기 폐기 → 새 `device` 생성(tokenHash 복사) → 코드 상태 PAIRED
5. 차량 앱: PAIRED 수신 → `GET /devices/me/config`로 차량·직원·장소 받기 → "등록 완료" TTS
6. 만료된 코드는 EXPIRED. 차량 앱은 새 코드를 발급받음

- 코드 발급은 인증이 없으므로 IP당 분당 10회 제한
- 대기 중(PENDING) 코드끼리는 중복 없음
- 같은 `installationId`로 새 코드를 발급하면 그 설치의 이전 대기 코드는 EXPIRED
- 이미 기기에 쓰인 `tokenHash`의 코드로 페어링하면 400 `PAIRING_CODE_INVALID` (차량 앱은 페어링마다 새 토큰)
- 비활성 차량은 페어링할 수 없음 → 404 `VEHICLE_NOT_FOUND`
- 페어링하면 같은 회사에서 같은 `installationId`를 쓰던 다른 차량의 활성 기기도 폐기 (앱을 다른 차량으로 옮겼을 때 이전 차량이 연결된 것으로 남지 않게)

### 재페어링 (D10)

- 차량 앱은 페어링마다 새 기기 토큰을 만듭니다 (이전 토큰 재사용 안 함)
- 로컬 트립·수정에는 기록 당시 `vehicleId`를 함께 저장합니다
- 재페어링 시작 전: 미전송(PENDING) 건이 있으면 "전송되지 않은 기록 N건" 경고 → 먼저 업로드 시도. 이전 토큰이 이미 폐기됐으면 업로드할 수 없으므로 경고 후 진행
- 같은 차량에 다시 페어링: 미전송 트립은 새 토큰으로 정상 업로드 (서버의 `device_id`만 새 기기)
- 차량 '다시 등록' → 새 6자리 코드 → 관리자 연결 응답 `replacedDeviceId` → 홈 복귀·이용자 선택 해제·새 토큰 업로드는 **확인됨 (2026-11-02)**
- 다른 차량에 페어링: 이전 차량의 미전송 트립은 `VEHICLE_MISMATCH`로 거절 → 차량 앱은 `FAILED`로 표시하고 보관 (관리자 문의 대상). 서버에는 다른 차량 기록으로 들어가지 않음

## 3. 트립 업로드 (`PUT /trips/batch`)

- 요청당 최대 50건. 건별 결과(`CREATED` / `DUPLICATE` / `REJECTED`)를 반환하고, 일부가 거절돼도 HTTP 200
- 같은 `clientUuid`가 이미 있으면 내용 비교 없이 `DUPLICATE` (저장된 값 유지)
- 서버가 계산: `tripDate`, `distanceKm`. 차량·회사는 기기 토큰에서 결정
- 건별 검증 → 실패 시 `REJECTED` + `errorCode` (목록: [README](README.md#트립-업로드-건별-errorcode))
  - `vehicleId`가 기기 토큰의 차량과 다르면 `VEHICLE_MISMATCH` (D10)
  - `startedAt`·`endedAt`·`recordedAt`이 서버 수신 시각 + 10분보다 미래이거나, `startedAt`이 서버 수신 시각 − 366일보다 과거면 `TIMESTAMP_OUT_OF_RANGE` (D11)
  - `odoEndKm ≥ odoStartKm`, `endedAt > startedAt`, 열거값 범위
  - `purpose = UNCONFIRMED`이면 `purposeSource = AUTO`
  - `businessCategory`는 `purpose = BUSINESS`일 때만
  - `driverId`는 같은 회사의 활성 직원(`DRIVER_NOT_FOUND`), `placeId`는 같은 회사의 장소(`PLACE_NOT_FOUND`)
  - COMPANY·CLIENT는 `placeId` 필수, HOME·UNREGISTERED는 `placeId` null
  - COMPANY·CLIENT의 라벨은 서버가 장소 이름으로 채움 (요청 라벨 무시). HOME은 '집', UNREGISTERED는 요청 라벨 또는 null. 차량 앱은 NaviHelper 주소의 동 이름(예: 시흥동), 못 받으면 null 전송 (확인됨 2026-11-03), 경로 목적지명은 2차
  - 같은 회사 장소면 삭제된 장소도 받음 (차량이 오프라인인 동안 삭제된 경우). 장소의 `type`과 출발·도착 유형이 같은지는 검사하지 않음
  - `purpose = BUSINESS`이고 `businessCategory`가 null이면 그대로 저장 (기본값 `OTHER`는 수정 때만 적용)
- 0.5km 미만 트립은 직전 트립이 미전송(`PENDING`)일 때만 직전 트립에 병합. 직전 트립이 이미 서버로 올라갔으면 D3(자동값 불변)에 따라 새 짧은 트립은 기록하지 않고 버림 (구현 기준, 결정됨 2026-11-02). 서버는 주행거리 0 이상이면 받음
- 업로드 요청 성공 시 `device.last_synced_at` 갱신

## 4. 수정 (`POST /trips/{clientUuid}/edits`)

- 수정 가능 필드: `purpose`, `businessCategory`, `note`, `driverId`. 그 외 필드가 오면 400 `AUTO_FIELD_IMMUTABLE` (D3)
- 요청 1건 = 이력 1행 (`changes`에 바뀐 필드 전부). 값이 같은 필드는 이력에서 제외하고, 바뀐 게 없으면 이력 없이 현재값 반환
- `purpose`를 BUSINESS가 아닌 값으로 바꾸면 `businessCategory`는 자동으로 null (이력에 함께 기록)
- `purpose`를 BUSINESS로 바꾸면서 `businessCategory`가 없으면 `OTHER`
- 차량 화면 미확인 3버튼의 '업무' 선택은 도착지가 등록 거래처면 `VISIT`, 그 외는 `OTHER`. `BUSINESS/MANUAL/VISIT` 서버 저장 및 기록부 비고 '거래처 방문 / 장소명' **확인됨 (2026-11-02)**. 위 서버 기본값 규칙과 구분
- 수정 후 용도가 BUSINESS가 아닌데 `businessCategory` 값을 보내면 400 `VALIDATION_FAILED`
- `purpose`를 `UNCONFIRMED`로 바꾸는 수정은 400 `VALIDATION_FAILED` (미확인은 차량 추정값 전용)
- 판정 출처: 차량 수정은 요청의 `purposeSource`(`MANUAL`/`VOICE`, 용도를 바꾸는데 없으면 400), 관리자 수정은 `ADMIN`. 용도를 바꾸지 않는 수정은 판정 출처 유지
- 차량: 자기 차량의 트립만(다른 차량 트립은 404), `clientEditUuid` 필수. 같은 키 재전송 → 200, `edit`는 처음 만든 이력, `current`는 지금 값 (바뀐 게 없던 요청은 이력이 없으므로 다시 평가)
- 차량 수정의 `editedAt`이 서버 수신 시각 + 10분보다 미래면 400 `VALIDATION_FAILED` (D11)
- 관리자: `reason` 필수(400 `EDIT_REASON_REQUIRED`), 현재 용도가 PERSONAL이면 403 `PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN`(D4). 관리자는 용도를 `COMMUTE`·`BUSINESS`로만 바꿀 수 있음: PERSONAL로 바꾸면 403 `PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN` (개인 여부는 운전자가 판단), UNCONFIRMED는 위 규칙대로 400
- 관리자 수정 응답(`current`, `edit.changes`)도 §7 마스킹을 **수정 후** 용도 기준으로 적용. 미확인 운행을 업무·출퇴근으로 확정하면 그때부터 장소·메모·시각이 보임 (D15, 결정됨 2026-11-01)
- 대시보드는 가려진 메모(미확인 운행)를 관리자가 수정하지 못하게 막음 (원래 값을 모른 채 덮어쓰는 것 방지)
- 현재값 = 서버 수신 순서상 마지막 이력. 차량·관리자 수정이 겹쳐도 둘 다 이력에 남음
- 트립보다 수정이 먼저 도착 → 404 `TRIP_NOT_FOUND` → 차량 앱은 트립 업로드 후 재시도

## 5. 설정 내려받기 (`GET /devices/me/config`)

- `since` 없음 → 전체. 있으면 그 시각 이후 `updated_at`이 바뀐 직원·장소만
- 삭제·비활성도 전달: 장소 `deleted: true`, 직원 `active: false`
- 응답의 `serverTime`을 다음 요청의 `since`로 사용 (차량 시계와 무관)
- `serverTime`은 초 단위 내림, 비교는 `updated_at >= since` (같은 초의 변경은 다음 응답에 다시 올 수 있으므로 차량 앱은 id 기준으로 덮어씀)
- `since`가 없을 때도 비활성 직원·삭제된 장소를 포함 (차량 앱 로컬 목록 정리용)
- 차량 정보와 현재 동의 문서 버전(`consentVersion`)은 항상 포함
- `tripUpdates`: 이 차량 트립 중 `since` 이후(없으면 최근 90일) 관리자 수정 이력(`actor_type = ADMIN`, `created_at >= since`)이 생긴 트립의 현재 `purpose`·`purposeSource`·`businessCategory`. 차량 앱은 로컬 값을 덮어쓰고 수정 이력을 만들지 않음 (관리자 확정 후 차량 화면에 미확인으로 남아 운전자가 덮어쓰는 것을 막음, 2026-11-02 연동 점검에서 발견)

## 6. 차량 앱 동기화 (참고)

2~6단계 구현·실측 **확인됨 (2026-11-02)**. 전체 차량 앱 동작은 [car-app.md](car-app.md).

- Room `sync_state`: `PENDING` → `SYNCED` / `FAILED`(일반 거절은 재시도 안 함). `DRIVER_NOT_FOUND`는 이용자를 비우고 재전송하며, 과거 이 코드로 `FAILED`가 된 트립도 같은 방식으로 재전송. 남은 실패는 상단 빨간 '전송 실패 N건' 표시 (확인됨 2026-11-02)
- 종료 확정 즉시 `UNCONFIRMED/AUTO`·답변 대기로 저장 → 음성 결과 반영 → 업로드. 질문 중 종료되면 다음 실행 때 미확인 업로드
- 미전송 트립 수정은 로컬 값만 변경. 업로드된 트립은 `trip_edit`으로 `POST /trips/{clientUuid}/edits`. 업로드 요청·응답 사이 용도 변경은 `SYNCED` 처리 후 수정 이력 재전송
- WorkManager 고유 즉시 작업 `trip-sync`, 네트워크 연결 조건, 지수 백오프 30초 시작. 트리거: 저장·수정 직후, 앱 시작·주행 시작 시. 별도 `trip-sync-periodic`은 15분 주기
- 즉시 작업 정책은 `ExistingWorkPolicy.REPLACE`. 서버 중단 중 대기 → 복구 후 다음 주행 시작·저장 요청이 백오프 중 이전 작업을 대체해 바로 전송. 이전 `APPEND_OR_REPLACE`의 약 3분 지연 해소 **확인됨 (2026-11-02)**
- 순서: 동의 기록 → 트립 업로드 → 수정 업로드 → 설정 내려받기
- 설정 수신 시 비활성화된 선택 이용자는 해제. `tripUpdates`의 관리자 확정 용도를 차량 트립에 반영하며 별도 차량 수정 이력은 만들지 않음 (확인됨 2026-11-02)
- 트립·수정에는 기록 당시 `vehicleId`를 함께 저장 (D10)
- 401은 기기 토큰 삭제·재페어링, 429·5xx·네트워크 오류는 재시도. 트립 업로드 요청 전체 400은 해당 묶음 `FAILED`
- Pleos AVD에서 트립 저장·화면 수정 직후 WorkManager 실행 → 호스트 `http://10.0.2.2:8080` 업로드 성공 **확인됨 (2026-11-02)**
- 최근·미확인 목록의 새 운행이 화면 위로 숨던 LazyColumn key 문제 수정 후 맨 위에 바로 표시됨. '확인이 필요한 운행' 제목은 0건이면 일반색, 1건 이상이면 경고색 (6a6a69d, 구현 확인됨 2026-11-02)

## 7. 마스킹 (관리자에게 보이는 값)

- 저장값은 그대로 두고, 관리자 응답(JSON·XLSX)을 만드는 한 곳에서만 적용. 단위 테스트 필수
- 기준은 트립의 **현재** 용도

| 현재 용도 | 보이는 것 | 가리는 것 (null로 응답) |
| --- | --- | --- |
| PERSONAL | 사용일자, 직원, 주행 전/후 계기판 거리, 주행거리, '개인' | 출발·도착 라벨·장소, 메모, 시작·종료 시각(시:분), 이력의 메모 값 |
| UNCONFIRMED | 위와 같음 + 회사·거래처 출발·도착 | HOME·UNREGISTERED 출발·도착(유형까지 null), 메모, 시작·종료 시각, 이력의 메모 값 |
| COMMUTE | 전부 (HOME은 '집') | - |
| BUSINESS | 전부 | - |

- 가린 항목이 있으면 응답에 `masked: true` → 대시보드는 '장소 비공개'로 표시
- 시각을 가리는 운행(PERSONAL·UNCONFIRMED)은 상세의 `recordedAt`·`syncedAt`도 null (시:분이 드러나므로)
- 이력 `changes[].before/after`에서 가린 값은 null. `driverId`는 number id 또는 null
- 계기판 거리는 개인 운행도 공개 (기록 누락 탐지와 서식 ⑤⑥ 연속성에 필요)

## 8. 대시보드 집계 (`GET /dashboard/summary`)

- 월 단위(`month=YYYY-MM`, Asia/Seoul `trip_date` 기준), 활성 차량별
- 총주행거리 = 모든 트립 주행거리 합 (개인·미확인 포함)
- 업무용 사용거리 = 출퇴근 + 일반 업무
- 업무사용비율 = 업무용 사용거리 ÷ 총주행거리 × 100, 소수 첫째 자리 반올림. 총주행거리 0이면 null
- 최대 비율 = (업무용 + 미확인) ÷ 총주행거리 × 100 (미확인이 전부 업무일 때)
- 경고 (D9)
  - `RATIO_HIGH`: 업무비율 95% 이상
  - `UNCONFIRMED_EXISTS`: 미확인 1건 이상
  - `SYNC_DELAYED`: 마지막 동기화 48시간 초과 (페어링된 차량만)
  - `ODO_GAP`: 같은 차량에서 직전 트립 주행 후 거리와 다음 트립 주행 전 거리 차이 1km 초과 (앱 미실행 구간 의심)
- 합계(`totals`)는 활성 차량의 트립만 더함
- `lastSyncedAt`은 연결 해제된 기기를 포함한 그 차량의 최댓값. `SYNC_DELAYED`는 활성 기기의 `last_synced_at`(없으면 `paired_at`) 기준

## 9. 기록부 내보내기 (`GET /reports/logbook`, D7)

- 차량 1대 + 기간(`from`~`to`, 날짜 포함, 최대 366일). 1차는 XLSX만
- 비활성 차량도 내보낼 수 있음 (과거 기록 보존)
- 파일명: `운행기록부_{자동차등록번호}_{from}_{to}.xlsx` (Content-Disposition에 RFC 5987 `filename*` 사용)
- 상단: 과세기간(from~to), 상호명, 사업자등록번호, ① 차종, ② 자동차등록번호
- 행(시작 시각순): ③ 사용일자(요일) · ④ 부서 · ④ 성명 · ⑤ 주행 전 · ⑥ 주행 후 · ⑦ 주행거리 · ⑧ 출퇴근용 · ⑨ 일반 업무용 · ⑩ 비고
- ⑩ 비고: BUSINESS = 업무 세부 + 도착지 라벨 + 메모 (구분자 ` / `) / COMMUTE = '출퇴근' / PERSONAL = 빈칸 / UNCONFIRMED = '미확인'
- 하단: ⑪ 총주행거리, ⑫ 업무용 사용거리, ⑬ 업무사용비율 (총주행거리 0이면 '-')
- 미확인 포함 시 시트 상단에 경고 문구
- 마스킹 규칙(7번) 그대로 적용

## 10. 서버 설정

환경변수·시드는 [config.md](config.md) (D12).

## 11. 개인정보 보유·삭제·동의 (D13)

### 보유 기간

| 데이터 | 위치 | 보유 | 비고 |
| --- | --- | --- | --- |
| 운행 기록·수정 이력 | 서버 | 해당 과세기간 신고기한부터 5년 | 국세기본법상 장부 보존 기간 5년 기준 (조문·기산일 미확인, 법령 확인 필요) |
| 직원(부서·성명) | 서버 | 마지막 운행 기록의 보유 기간까지 | 비활성으로 바꿔도 과거 기록 표시에 필요 |
| 동의 기록 | 서버 | 동의 철회 후에도 해당 직원 운행 기록 보유 기간까지 | 동의 사실 증빙 |
| 집 위치·좌표 | 차량 앱 | 차량 앱 안에서만, 사용자가 삭제하거나 앱 삭제 시까지 | 서버 전송 없음 (D2) |
| 동기화 완료된 트립 | 차량 앱 | 동기화 후 90일 (제안) | 자동 삭제 미구현, DAO 쿼리만 있음 (확인됨 2026-11-02) |
| 미전송 트립 | 차량 앱 | 전송 성공까지 (`FAILED`는 사용자가 지울 때까지) | |

- 서버의 5년 보유 기간 경과 자동 삭제는 1차에 만들지 않습니다 (5년 이내라 발생하지 않음). 차량 앱 90일 자동 삭제도 별도 미구현입니다 (DAO 쿼리만 있음, 확인됨 2026-11-02)
- 완료 트립의 좌표는 장소 판정 후 버리고 Room에도 저장하지 않습니다. 장소 좌표와 진행 중 시작 값의 DataStore 보관은 [car-app §1](car-app.md#1-기록복구수정) 참고 (확인됨 2026-11-02)
- 회사가 서비스를 그만두면 관리자 요청으로 회사 데이터 전체 삭제. 1차는 운영자가 수동 처리 (삭제 API 없음)
- 개인정보처리방침·위치정보 이용약관은 대시보드 도메인에 공개 페이지로 게시 (App Market 제출 필수)

### 동의

- 동의 주체: 직원(차량이용자). 차량 앱에서 이용자로 **처음 선택될 때** 동의 화면을 띄웁니다 (정차 중)
- 동의 화면 내용
  - 수집 항목: 사용일자, 주행 전/후 계기판 거리, 출발·도착 장소명(회사·거래처·'집', 그 밖에는 동 이름), 운행 용도·업무 세부·메모, 이용자(부서·성명)
  - 서버로 보내지 않는 것: 좌표, 집 위치
  - 관리자가 볼 수 있는 범위: 개인 운행은 날짜·거리만 (마스킹 규칙 7번)
  - 보유 기간 (위 표), 동의 거부 시 해당 직원으로 운행을 기록할 수 없음
- 동의하면 차량 앱 로컬에 저장하고 서버에 `POST /devices/me/consents`로 기록. 거부하면 그 직원은 이용자로 선택할 수 없음 (이용자 미지정으로 기록하거나 다른 직원 선택)
- 동의 문서가 바뀌면 `consentVersion`을 올리고, 차량 앱은 다음 선택 때 다시 동의를 받습니다
- 설정 미수신 등으로 동의 문서 버전을 모르면 동의를 기록하지 않습니다. 동의 화면 문구는 이 절 항목대로 구현됐습니다 (확인됨 2026-11-02)
- 동의 철회: 1차는 관리자 문의로 처리 (철회 API 없음). 철회 이후 해당 직원 선택 불가
