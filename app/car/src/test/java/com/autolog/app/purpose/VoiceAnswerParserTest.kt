package com.autolog.app.purpose

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.purpose.VoiceAnswerParser.Answer
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAnswerParserTest {
    private val commute = PurposeEstimate(Purpose.COMMUTE, null, "출근으로 기록할까요?")
    private val visit = PurposeEstimate(Purpose.BUSINESS, BusinessCategory.VISIT, "한빛상사 방문, 업무로 기록할까요?")
    private val unknown = PurposeEstimate(Purpose.UNCONFIRMED, null, "업무였나요, 개인 용도였나요?")

    @Test
    fun `긍정 응답`() {
        listOf("응", "네", "네 맞아요", "예", "맞아", "그래", "응응", "네.", "좋아요").forEach {
            assertEquals(it, Answer.Yes, VoiceAnswerParser.parse(it))
        }
    }

    @Test
    fun `개인 응답`() {
        listOf("아니", "아니요", "개인", "개인 용도야", "아뇨").forEach {
            assertEquals(it, Answer.Personal, VoiceAnswerParser.parse(it))
        }
    }

    @Test
    fun `용도를 직접 말하면 그 용도`() {
        assertEquals(Answer.Explicit(Purpose.COMMUTE, null), VoiceAnswerParser.parse("퇴근이야"))
        assertEquals(Answer.Explicit(Purpose.BUSINESS, null), VoiceAnswerParser.parse("업무"))
        assertEquals(Answer.Explicit(Purpose.BUSINESS, BusinessCategory.VISIT), VoiceAnswerParser.parse("거래처 방문이에요"))
        assertEquals(Answer.Explicit(Purpose.BUSINESS, BusinessCategory.MEETING), VoiceAnswerParser.parse("회의 다녀왔어"))
        assertEquals(Answer.Explicit(Purpose.BUSINESS, BusinessCategory.PROMOTION), VoiceAnswerParser.parse("판촉"))
        assertEquals(Answer.Explicit(Purpose.BUSINESS, BusinessCategory.TRAINING), VoiceAnswerParser.parse("교육"))
    }

    @Test
    fun `부정 뒤에 용도가 오면 용도를 따른다`() {
        assertEquals(Answer.Explicit(Purpose.BUSINESS, null), VoiceAnswerParser.parse("아니 업무야"))
        assertEquals(Answer.Explicit(Purpose.COMMUTE, null), VoiceAnswerParser.parse("아니, 퇴근"))
    }

    @Test
    fun `STT 조각을 이어붙인 응답도 해석한다`() {
        assertEquals(Answer.Explicit(Purpose.BUSINESS, BusinessCategory.VISIT), VoiceAnswerParser.parse("네 거래처 방문이에요"))
    }

    @Test
    fun `응답 전체가 너무면 업무 오인식으로 본다`() {
        listOf("너무", "너무요", "너무예요", "너무였어요.").forEach {
            assertEquals(it, Answer.Explicit(Purpose.BUSINESS, null), VoiceAnswerParser.parse(it))
        }
        listOf("너무 피곤해", "너무 멀었어").forEach {
            assertEquals(it, Answer.Unknown, VoiceAnswerParser.parse(it))
        }
    }

    @Test
    fun `알아들을 수 없으면 Unknown`() {
        listOf("", "  ", "어제 날씨", "음").forEach {
            assertEquals(it, Answer.Unknown, VoiceAnswerParser.parse(it))
        }
    }

    @Test
    fun `긍정이면 추정값을 음성 확인으로 확정`() {
        assertEquals(PurposeDecision(Purpose.COMMUTE, PurposeSource.VOICE, null), VoiceAnswerParser.resolve(commute, "응"))
        assertEquals(
            PurposeDecision(Purpose.BUSINESS, PurposeSource.VOICE, BusinessCategory.VISIT),
            VoiceAnswerParser.resolve(visit, "네"),
        )
    }

    @Test
    fun `무응답이나 해석 불가는 미확인 AUTO`() {
        val unconfirmed = PurposeDecision(Purpose.UNCONFIRMED, PurposeSource.AUTO, null)
        assertEquals(unconfirmed, VoiceAnswerParser.resolve(commute, null))
        assertEquals(unconfirmed, VoiceAnswerParser.resolve(commute, "음"))
    }

    @Test
    fun `미확인 질문에 응이라고만 하면 미확인`() {
        assertEquals(PurposeDecision(Purpose.UNCONFIRMED, PurposeSource.AUTO, null), VoiceAnswerParser.resolve(unknown, "응"))
    }

    @Test
    fun `업무라고만 하면 추정한 업무 세부를 유지하고, 추정이 업무가 아니면 세부 없음`() {
        assertEquals(BusinessCategory.VISIT, VoiceAnswerParser.resolve(visit, "업무").businessCategory)
        assertEquals(null, VoiceAnswerParser.resolve(unknown, "업무").businessCategory)
        assertEquals(Purpose.BUSINESS, VoiceAnswerParser.resolve(unknown, "업무").purpose)
    }

    @Test
    fun `개인이라고 하면 개인`() {
        assertEquals(PurposeDecision(Purpose.PERSONAL, PurposeSource.VOICE, null), VoiceAnswerParser.resolve(commute, "아니 개인"))
    }
}
