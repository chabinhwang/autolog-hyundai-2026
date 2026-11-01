package com.autolog.app.export

import com.autolog.app.data.local.DriverEntity
import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.sync.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CsvExporterTest {
    private val drivers = mapOf(11L to DriverEntity(11, "영업1팀", "김영업", true))

    @Test
    fun `서식 열 순서로 출퇴근·업무 거리를 나눠 적는다`() {
        val commute = Fixtures.trip("a", Instant.parse("2026-11-02T23:12:00Z"))
        val business = Fixtures.trip("b", Instant.parse("2026-11-03T04:00:00Z")).copy(
            odoStartKm = 12051.2, odoEndKm = 12063.6, originType = EndpointType.COMPANY, originLabel = "본사",
            destType = EndpointType.CLIENT, destPlaceId = 4, destLabel = "한빛상사",
            purpose = Purpose.BUSINESS, businessCategory = BusinessCategory.VISIT,
        )
        val lines = CsvExporter.export(listOf(business, commute), drivers).trimEnd().split("\r\n")

        assertEquals(3, lines.size)
        assertEquals(CsvExporter.HEADER.joinToString(","), lines[0])
        assertEquals("2026-11-03,영업1팀,김영업,12034.5,12051.2,16.7,16.7,,출퇴근,집,본사,08:12,08:47,", lines[1])
        assertEquals("2026-11-03,영업1팀,김영업,12051.2,12063.6,12.4,,12.4,업무(거래처 방문),본사,한빛상사,13:00,13:35,", lines[2])
    }

    @Test
    fun `개인·미확인은 업무 거리 열이 비고 이용자 미지정이면 이름이 비어 있다`() {
        val personal = Fixtures.trip("a").copy(purpose = Purpose.PERSONAL, driverId = null)
        val line = CsvExporter.export(listOf(personal), drivers).trimEnd().split("\r\n")[1]
        assertEquals("2026-11-03,,,12034.5,12051.2,16.7,,,개인,집,본사,08:12,08:47,", line)
    }

    @Test
    fun `쉼표·따옴표는 감싸고 수식처럼 보이는 메모는 무력화한다`() {
        val trip = Fixtures.trip("a").copy(note = "=SUM(A1), \"메모\"", destType = EndpointType.UNREGISTERED, destLabel = "강남역, 2번 출구")
        val line = CsvExporter.export(listOf(trip), drivers).trimEnd().split("\r\n")[1]
        assertEquals(true, line.endsWith("\"'=SUM(A1), \"\"메모\"\"\""))
        assertEquals(true, line.contains("\"강남역, 2번 출구\""))
    }
}
