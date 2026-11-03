package com.autolog.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AreaNameTest {
    @Test
    fun `내비 주소에서 마지막 행정구역만 남긴다`() {
        assertEquals("시흥동", AreaName.from("수정구 시흥동"))
        assertEquals("신림동", AreaName.from(" 서울특별시 관악구  신림동 "))
    }

    @Test
    fun `비었거나 이상한 주소는 쓰지 않는다`() {
        assertNull(AreaName.from(null))
        assertNull(AreaName.from(""))
        assertNull(AreaName.from("   "))
        assertNull(AreaName.from("가"))
    }
}
