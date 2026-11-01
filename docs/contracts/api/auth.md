# 인증 API

> [계약 문서 목차](../README.md) · 규칙: [rules.md §1](../rules.md#1-인증)

## POST /api/v1/auth/login — 관리자 로그인

- 호출: 관리자 / 인증: 없음 / IP당 분당 10회 + 계정당 15분에 실패 5회 ([rules §1](../rules.md#1-인증))
- JWT 발급, 만료 12시간, Refresh 없음 (D1)

### 요청

- `loginId`: 필수, 이메일 형식 / `password`: 필수, 1~100자

```json
{
  "loginId": "admin@autolog-demo.kr",
  "password": "********"
}
```

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 43200,
    "admin": { "id": 1, "loginId": "admin@autolog-demo.kr", "displayName": "총무팀" },
    "organization": { "id": 1, "name": "오토로그 주식회사" }
  },
  "error": null
}
```

### 실패 응답 — 400 Bad Request (VALIDATION_FAILED)

```json
{ "success": false, "data": null, "error": { "code": "VALIDATION_FAILED", "message": "loginId: 이메일 형식이어야 합니다." } }
```

### 실패 응답 — 401 Unauthorized (INVALID_CREDENTIALS)

아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않습니다.

```json
{ "success": false, "data": null, "error": { "code": "INVALID_CREDENTIALS", "message": "아이디 또는 비밀번호가 올바르지 않습니다." } }
```

### 실패 응답 — 429 Too Many Requests (RATE_LIMITED, IP 기준)

```json
{ "success": false, "data": null, "error": { "code": "RATE_LIMITED", "message": "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요." } }
```

### 실패 응답 — 429 Too Many Requests (RATE_LIMITED, 계정 기준)

같은 아이디로 15분 안에 5번 실패한 경우. 비밀번호가 맞아도 이 기간이 끝날 때까지 거절합니다.

```json
{ "success": false, "data": null, "error": { "code": "RATE_LIMITED", "message": "로그인 실패가 많아 잠시 로그인할 수 없습니다. 15분 후 다시 시도해 주세요." } }
```

## GET /api/v1/auth/me — 현재 관리자 조회

- 호출: 관리자 / 인증: 관리자 JWT
- 대시보드 새로고침 시 토큰 유효성 확인과 회사 정보 조회

### 요청

- Header: `Authorization: Bearer {관리자 JWT}` / 본문 없음

### 성공 응답 — 200 OK

```json
{
  "success": true,
  "data": {
    "admin": { "id": 1, "loginId": "admin@autolog-demo.kr", "displayName": "총무팀" },
    "organization": { "id": 1, "name": "오토로그 주식회사", "bizRegNo": "123-45-67890" },
    "expiresAt": "2026-11-03T20:00:00+09:00"
  },
  "error": null
}
```

### 실패 응답 — 401 Unauthorized (UNAUTHORIZED)

토큰 없음·만료·서명 불일치. 대시보드는 로그인 화면으로 이동합니다.

```json
{ "success": false, "data": null, "error": { "code": "UNAUTHORIZED", "message": "로그인이 필요합니다." } }
```
