package com.autolog.app.trip

import com.autolog.app.domain.DetectedTrip

/**
 * 0.5km 미만 트립 처리 (car-app-rules.md 트립 경계 '무시' 규칙).
 *
 * - 직전 트립이 아직 서버로 안 올라갔으면 직전 트립의 끝을 이번 트립 끝으로 늘려 병합
 * - 직전 트립이 이미 올라갔으면 서버 자동값을 바꿀 수 없으므로(D3) 기록하지 않음
 */
object ShortTripPolicy {
    const val MIN_DISTANCE_KM = 0.5

    sealed interface Decision {
        /** 새 트립으로 저장. */
        data object Keep : Decision
        /** 직전 트립(previousId)의 끝을 바꾼다. */
        data class MergeIntoPrevious(val previousId: String) : Decision
        /** 기록하지 않는다. */
        data object Drop : Decision
    }

    /**
     * @param previousId 직전 트립 id. 없으면 null
     * @param previousMergeable 직전 트립이 아직 업로드 전이고 확인 질문도 끝난 상태인지
     */
    fun decide(trip: DetectedTrip, previousId: String?, previousMergeable: Boolean): Decision = when {
        trip.distanceKm >= MIN_DISTANCE_KM -> Decision.Keep
        previousId != null && previousMergeable -> Decision.MergeIntoPrevious(previousId)
        else -> Decision.Drop
    }
}
