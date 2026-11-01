package com.autolog.server.trip;

/** 판정 출처 */
public enum PurposeSource {
	/** 추정값, 확인 안 됨 */
	AUTO,
	/** 음성 확인 */
	VOICE,
	/** 차량 화면 선택 */
	MANUAL,
	/** 관리자 수정 */
	ADMIN
}
