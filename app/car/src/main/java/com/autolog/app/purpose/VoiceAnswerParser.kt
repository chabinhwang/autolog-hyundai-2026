package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource

/** 확정된 용도. */
data class PurposeDecision(
    val purpose: Purpose,
    val source: PurposeSource,
    val businessCategory: BusinessCategory?,
)

/**
 * 음성 응답 해석 (car-app-rules.md 음성 응답 해석).
 *
 * 우선순위: 구체적인 용도 단어(출근·업무·방문…) > 개인·아니 > 예·응.
 * "아니 업무야"처럼 부정 뒤에 용도가 오면 용도를 따른다.
 */
object VoiceAnswerParser {
    sealed interface Answer {
        data object Yes : Answer
        data object Personal : Answer
        data class Explicit(val purpose: Purpose, val category: BusinessCategory?) : Answer
        data object Unknown : Answer
    }

    private val CATEGORY_WORDS = listOf(
        "방문" to BusinessCategory.VISIT,
        "거래처" to BusinessCategory.VISIT,
        "미팅" to BusinessCategory.MEETING,
        "회의" to BusinessCategory.MEETING,
        "판촉" to BusinessCategory.PROMOTION,
        "영업" to BusinessCategory.PROMOTION,
        "교육" to BusinessCategory.TRAINING,
        "연수" to BusinessCategory.TRAINING,
    )
    private val BUSINESS_WORDS = listOf("업무", "출장", "일")
    private val COMMUTE_WORDS = listOf("출근", "퇴근", "출퇴근")
    private val PERSONAL_WORDS = listOf("개인", "사적", "아니", "아뇨", "노")
    private val YES_WORDS = listOf("응", "어", "네", "예", "그래", "맞아", "맞습니다", "맞아요", "좋아", "기록해", "오케이", "ok")

    fun parse(text: String): Answer {
        val t = normalize(text)
        if (t.isEmpty()) return Answer.Unknown
        if (COMMUTE_WORDS.any { it in t }) return Answer.Explicit(Purpose.COMMUTE, null)
        CATEGORY_WORDS.firstOrNull { (word, _) -> word in t }?.let { (_, category) ->
            return Answer.Explicit(Purpose.BUSINESS, category)
        }
        if (BUSINESS_WORDS.any { word -> if (word.length == 1) t == word || t.startsWith("${word}이") else word in t }) {
            return Answer.Explicit(Purpose.BUSINESS, null)
        }
        if (PERSONAL_WORDS.any { word -> if (word.length == 1) t == word else word in t }) return Answer.Personal
        val firstToken = text.lowercase().split(Regex("[\\s.,!?~·]+")).firstOrNull { it.isNotEmpty() }.orEmpty()
        if (YES_WORDS.any { yes -> firstToken == yes || (yes.length > 1 && firstToken.startsWith(yes)) || isRepeated(firstToken, yes) }) {
            return Answer.Yes
        }
        return Answer.Unknown
    }

    /** "응응", "네네" 같은 반복. */
    private fun isRepeated(token: String, word: String): Boolean =
        word.length == 1 && token.length in 2..3 && token.all { it == word[0] }

    /**
     * 추정값과 응답으로 최종 용도를 정한다. 무응답·해석 불가·추정이 미확인인데 "응"이면 UNCONFIRMED(AUTO).
     */
    fun resolve(estimate: PurposeEstimate, answerText: String?): PurposeDecision {
        val unconfirmed = PurposeDecision(Purpose.UNCONFIRMED, PurposeSource.AUTO, null)
        val answer = answerText?.let(::parse) ?: return unconfirmed
        return when (answer) {
            Answer.Yes -> if (estimate.purpose == Purpose.UNCONFIRMED) {
                unconfirmed
            } else {
                PurposeDecision(estimate.purpose, PurposeSource.VOICE, estimate.businessCategory)
            }
            Answer.Personal -> PurposeDecision(Purpose.PERSONAL, PurposeSource.VOICE, null)
            is Answer.Explicit -> {
                val category = when {
                    answer.purpose != Purpose.BUSINESS -> null
                    answer.category != null -> answer.category
                    estimate.purpose == Purpose.BUSINESS -> estimate.businessCategory
                    else -> null
                }
                PurposeDecision(answer.purpose, PurposeSource.VOICE, category)
            }
            Answer.Unknown -> unconfirmed
        }
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[\\s.,!?~·]"), "")
}
