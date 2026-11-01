// 서버 오류 코드별 기본 문구. 서버 메시지가 비어 있을 때만 사용합니다.
export const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: '입력값을 확인해 주세요.',
  INVALID_REQUEST_BODY: '요청 형식이 올바르지 않습니다.',
  AUTO_FIELD_IMMUTABLE: '차량이 기록한 값은 수정할 수 없습니다.',
  EDIT_REASON_REQUIRED: '관리자 수정에는 사유가 필요합니다.',
  PAIRING_CODE_INVALID: '페어링 코드가 올바르지 않습니다.',
  PAIRING_CODE_EXPIRED: '페어링 코드가 만료되었습니다. 차량에서 새 코드를 받아 주세요.',
  UNAUTHORIZED: '로그인이 필요합니다.',
  INVALID_CREDENTIALS: '아이디 또는 비밀번호가 올바르지 않습니다.',
  FORBIDDEN: '이 작업을 할 권한이 없습니다.',
  PERSONAL_TRIP_ADMIN_EDIT_FORBIDDEN: '개인 운행은 운전자만 수정할 수 있습니다.',
  NOT_FOUND: '요청한 항목을 찾을 수 없습니다.',
  TRIP_NOT_FOUND: '운행 기록을 찾을 수 없습니다.',
  VEHICLE_NOT_FOUND: '차량을 찾을 수 없습니다.',
  DRIVER_NOT_FOUND: '직원을 찾을 수 없습니다.',
  PLACE_NOT_FOUND: '장소를 찾을 수 없습니다.',
  PAIRING_NOT_FOUND: '페어링 요청을 찾을 수 없습니다.',
  DEVICE_NOT_FOUND: '연결된 기기가 없습니다.',
  VEHICLE_REG_NO_DUPLICATE: '이미 등록된 자동차등록번호입니다.',
  RATE_LIMITED: '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.',
  INTERNAL_ERROR: '서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
  NETWORK_ERROR: '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.',
  UNKNOWN_ERROR: '요청을 처리하지 못했습니다.',
}

/** 화면에 보여 줄 오류 문구 */
export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return ERROR_MESSAGES.UNKNOWN_ERROR
}
