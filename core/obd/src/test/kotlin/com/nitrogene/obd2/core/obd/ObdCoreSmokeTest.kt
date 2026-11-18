package com.nitrogene.obd2.core.obd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObdCoreSmokeTest {
    @Test
    fun `schema version is initialized`() {
        assertEquals(1, ObdCoreInfo.SCHEMA_VERSION)
    }
}
