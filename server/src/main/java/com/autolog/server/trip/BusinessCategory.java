package com.autolog.server.trip;

/** 업무 세부. 용도가 BUSINESS일 때만 값이 있다. */
public enum BusinessCategory {
	VISIT("거래처 방문"),
	MEETING("회의"),
	PROMOTION("판촉"),
	TRAINING("교육"),
	OTHER("기타");

	private final String label;

	BusinessCategory(String label) {
		this.label = label;
	}

	/** 기록부 비고에 쓰는 한국어 이름 */
	public String label() {
		return label;
	}
}
