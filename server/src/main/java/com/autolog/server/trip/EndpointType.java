package com.autolog.server.trip;

/** 출발·도착 유형 */
public enum EndpointType {
	HOME,
	COMPANY,
	CLIENT,
	UNREGISTERED;

	/** 서버 장소(place)를 참조하는 유형인지 */
	public boolean requiresPlace() {
		return this == COMPANY || this == CLIENT;
	}
}
