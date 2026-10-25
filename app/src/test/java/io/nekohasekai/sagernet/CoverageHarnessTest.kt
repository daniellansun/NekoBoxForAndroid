package io.nekohasekai.sagernet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Kept so the unit-test task still has a trivial case beside the parser tests.
 * It does not call product code.
 */
class CoverageHarnessTest {
    @Test
    fun harnessDoesNotTouchProductCode() {
        assertEquals(2, 1 + 1)
    }
}
