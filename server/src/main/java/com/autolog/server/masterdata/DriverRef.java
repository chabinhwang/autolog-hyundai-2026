package com.autolog.server.masterdata;

/** 응답에 넣는 직원 요약 { id, dept, name } */
public record DriverRef(Long id, String dept, String name) {

	public static DriverRef of(Driver driver) {
		return driver == null ? null : new DriverRef(driver.getId(), driver.getDept(), driver.getName());
	}
}
