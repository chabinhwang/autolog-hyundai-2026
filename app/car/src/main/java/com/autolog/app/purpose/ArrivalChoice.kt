package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Endpoint
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource

/**
 * 도착 질문 팝업의 버튼 (car-app.md §3). 음성 대답과 동시에 받고 먼저 들어온 쪽으로 확정한다.
 * [LATER]는 지금 답하지 않고 미확인으로 남긴다.
 */
enum class ArrivalChoice {
    COMMUTE, BUSINESS, PERSONAL, LATER;

    /**
     * 버튼으로 고른 용도. 출처는 MANUAL(화면 선택), [LATER]는 UNCONFIRMED/AUTO.
     * 업무 세부는 추정이 업무면 그 세부, 아니면 도착지가 거래처일 때 방문(미확인 3버튼과 같은 규칙).
     */
    fun decide(estimate: PurposeEstimate, dest: Endpoint): PurposeDecision = when (this) {
        COMMUTE -> PurposeDecision(Purpose.COMMUTE, PurposeSource.MANUAL, null)
        PERSONAL -> PurposeDecision(Purpose.PERSONAL, PurposeSource.MANUAL, null)
        BUSINESS -> PurposeDecision(Purpose.BUSINESS, PurposeSource.MANUAL, businessCategory(estimate, dest))
        LATER -> PurposeDecision(Purpose.UNCONFIRMED, PurposeSource.AUTO, null)
    }

    private fun businessCategory(estimate: PurposeEstimate, dest: Endpoint): BusinessCategory? = when {
        estimate.purpose == Purpose.BUSINESS -> estimate.businessCategory
        dest.type == EndpointType.CLIENT -> BusinessCategory.VISIT
        else -> null
    }
}
