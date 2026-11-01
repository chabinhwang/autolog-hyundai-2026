package com.autolog.server.global.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 페이지 응답 공통 구조: { content, page, size, totalElements, totalPages } */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PageResponse<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
		return new PageResponse<>(mapper.apply(page.getContent()), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}
}
