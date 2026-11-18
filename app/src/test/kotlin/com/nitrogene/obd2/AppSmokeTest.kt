package com.nitrogene.obd2

import com.nitrogene.obd2.core.obd.ObdCoreInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppSmokeTest {
    @Test
    fun `app can access core obd schema version`() {
        assertEquals(1, ObdCoreInfo.SCHEMA_VERSION)
    }
}
