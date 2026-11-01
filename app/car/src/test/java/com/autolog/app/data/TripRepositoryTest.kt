package com.autolog.app.data

import com.autolog.app.domain.BusinessCategory
import com.autolog.app.domain.EndpointType
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.PurposeSource
import com.autolog.app.sync.FakeEditDao
import com.autolog.app.sync.FakePlaceDao
import com.autolog.app.sync.FakeTripDao
import com.autolog.app.sync.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TripRepositoryTest {
    private val trips = FakeTripDao()
    private val edits = FakeEditDao()
    private val repository = TripRepository(trips, edits, FakePlaceDao(), Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC))

    private fun unconfirmed(uuid: String, destType: EndpointType) =
        Fixtures.trip(uuid).copy(destType = destType, purpose = Purpose.UNCONFIRMED, purposeSource = PurposeSource.AUTO, autoPurpose = Purpose.UNCONFIRMED)

    @Test
    fun `거래처에 도착한 트립을 화면에서 업무로 고르면 거래처 방문으로 둔다`() = runBlocking {
        trips.insert(unconfirmed("a", EndpointType.CLIENT))
        repository.changePurpose("a", Purpose.BUSINESS, null)
        val saved = trips.find("a")!!
        assertEquals(Purpose.BUSINESS, saved.purpose)
        assertEquals(PurposeSource.MANUAL, saved.purposeSource)
        assertEquals(BusinessCategory.VISIT, saved.businessCategory)
    }

    @Test
    fun `거래처가 아닌 곳이면 세부 없이 업무로 둔다`() = runBlocking {
        trips.insert(unconfirmed("a", EndpointType.COMPANY))
        repository.changePurpose("a", Purpose.BUSINESS, null)
        assertNull(trips.find("a")!!.businessCategory)
    }

    @Test
    fun `업무가 아니면 세부를 두지 않는다`() = runBlocking {
        trips.insert(unconfirmed("a", EndpointType.CLIENT))
        repository.changePurpose("a", Purpose.PERSONAL, BusinessCategory.VISIT)
        assertNull(trips.find("a")!!.businessCategory)
    }
}
