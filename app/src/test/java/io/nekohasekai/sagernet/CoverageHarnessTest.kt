package io.nekohasekai.sagernet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeps the JVM unit-test task executable so JaCoCo can emit a session file.
 * This test does not call product code. First-party coverage stays at the
 * real baseline until behavior tests are added.
 */
class CoverageHarnessTest {
    @Test
    fun harnessDoesNotTouchProductCode() {
        assertEquals(2, 1 + 1)
    }
}
